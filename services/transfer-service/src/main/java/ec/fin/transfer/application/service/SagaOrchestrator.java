package ec.fin.transfer.application.service;

import ec.fin.transfer.application.port.in.AdvanceSagaUseCase;
import ec.fin.transfer.application.port.in.HandlePaymentResultUseCase;
import ec.fin.transfer.application.port.out.ConcurrentUpdateException;
import ec.fin.transfer.application.port.out.EventOutbox;
import ec.fin.transfer.application.port.out.LedgerPort;
import ec.fin.transfer.application.port.out.LedgerPort.LedgerResult;
import ec.fin.transfer.application.port.out.SagaDispatcher;
import ec.fin.transfer.application.port.out.TransferRepository;
import ec.fin.transfer.application.port.out.UnitOfWork;
import ec.fin.transfer.domain.model.Transfer;
import ec.fin.transfer.domain.saga.SagaCommand;
import ec.fin.transfer.domain.saga.SagaPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Saga orchestrator. Each iteration is: read the state (tx 1), call the participant with no transaction open,
 * then record the outcome (tx 2) only if nobody else advanced the saga meanwhile. Participants are idempotent,
 * so a duplicated call caused by two concurrent workers or a crash between the call and tx 2 is harmless.
 */
@Slf4j
@RequiredArgsConstructor
public class SagaOrchestrator implements AdvanceSagaUseCase, HandlePaymentResultUseCase {

  private static final int MAX_STEPS_PER_RUN = 10;
  private static final int MAX_CONFLICT_RETRIES = 3;

  private final TransferRepository transfers;
  private final EventOutbox outbox;
  private final LedgerPort ledger;
  private final UnitOfWork unitOfWork;
  private final SagaDispatcher dispatcher;
  private final SagaPolicy policy;
  private final Clock clock;
  private final int dueBatchSize;

  @Override
  public void advance(UUID transferId) {
    for (int step = 0; step < MAX_STEPS_PER_RUN; step++) {
      Optional<Transfer> loaded = unitOfWork.inTransaction(() -> transfers.findById(transferId));
      if (loaded.isEmpty() || !loaded.get().isDue(clock.instant())) {
        return;
      }
      Transfer snapshot = loaded.get();
      SagaCommand command = snapshot.nextCommand();
      log.debug("Transfer {} in {} executes {}", transferId, snapshot.getState(), command);
      Consumer<Transfer> transition = execute(command);
      if (!record(snapshot, transition)) {
        return;
      }
    }
  }

  @Override
  public int advanceDue() {
    List<UUID> due = unitOfWork.inTransaction(() -> transfers.findDueIds(clock.instant(), dueBatchSize));
    for (UUID transferId : due) {
      try {
        advance(transferId);
      } catch (RuntimeException e) {
        log.error("Saga of transfer {} could not advance; it will be retried", transferId, e);
      }
    }
    return due.size();
  }

  @Override
  public void handle(PaymentResult result) {
    for (int attempt = 1; ; attempt++) {
      try {
        boolean keepGoing = unitOfWork.inTransaction(() -> applyPaymentResult(result));
        if (keepGoing) {
          dispatcher.dispatch(result.transferId());
        }
        return;
      } catch (ConcurrentUpdateException e) {
        if (attempt >= MAX_CONFLICT_RETRIES) {
          throw e;
        }
      }
    }
  }

  /** Talks to the participant (no transaction open) and returns the transition to apply with the answer. */
  private Consumer<Transfer> execute(SagaCommand command) {
    return switch (command) {
      case SagaCommand.Debit debit -> {
        LedgerResult result = ledger.debit(debit);
        yield transfer -> {
          Instant now = clock.instant();
          switch (result) {
            case LedgerResult.Applied applied -> transfer.debitSucceeded(now, policy);
            case LedgerResult.Rejected rejected -> transfer.debitRejected(rejected.describe(), now);
            case LedgerResult.Unavailable unavailable -> transfer.stepFailedTransiently(unavailable.detail(), now, policy);
          }
        };
      }
      case SagaCommand.Credit credit -> {
        LedgerResult result = ledger.credit(credit);
        yield transfer -> {
          Instant now = clock.instant();
          switch (result) {
            case LedgerResult.Applied applied -> transfer.creditSucceeded(now);
            case LedgerResult.Rejected rejected -> transfer.creditRejected(rejected.describe(), now);
            case LedgerResult.Unavailable unavailable -> transfer.stepFailedTransiently(unavailable.detail(), now, policy);
          }
        };
      }
      case SagaCommand.Compensate compensate -> {
        LedgerResult result = reverseAll(compensate);
        yield transfer -> {
          Instant now = clock.instant();
          switch (result) {
            case LedgerResult.Applied applied -> transfer.compensationSucceeded(now);
            case LedgerResult.Rejected rejected -> transfer.stepFailedTransiently("reversal refused: " + rejected.describe(), now, policy);
            case LedgerResult.Unavailable unavailable -> transfer.stepFailedTransiently(unavailable.detail(), now, policy);
          }
        };
      }
      case SagaCommand.ResolvePaymentTimeout timeout -> transfer -> transfer.paymentResultTimedOut(clock.instant(), policy);
    };
  }

  // Reverse order of the forward steps; stops at the first failure so the retry starts over (reversals are idempotent).
  private LedgerResult reverseAll(SagaCommand.Compensate compensate) {
    for (UUID operationId : compensate.operationIds()) {
      LedgerResult result = ledger.reverse(operationId, compensate.reason());
      if (!(result instanceof LedgerResult.Applied)) {
        return result;
      }
    }
    return new LedgerResult.Applied();
  }

  private boolean record(Transfer snapshot, Consumer<Transfer> transition) {
    try {
      return unitOfWork.inTransaction(() -> {
        Transfer transfer = transfers.findById(snapshot.getId()).orElseThrow();
        if (transfer.getVersion() != snapshot.getVersion()) {
          return false;
        }
        transition.accept(transfer);
        persist(transfer);
        log.info("Transfer {} moved to {}", transfer.getId(), transfer.getState());
        return true;
      });
    } catch (ConcurrentUpdateException e) {
      log.debug("Transfer {} was advanced by another worker", snapshot.getId());
      return false;
    }
  }

  private boolean applyPaymentResult(PaymentResult result) {
    Optional<Transfer> found = transfers.findById(result.transferId());
    if (found.isEmpty()) {
      log.warn("Payment result {} refers to unknown transfer {}", result.orderId(), result.transferId());
      return false;
    }
    Transfer transfer = found.get();
    Instant now = clock.instant();
    boolean applied = result.settled()
        ? transfer.paymentSettled(result.orderId(), now)
        : transfer.paymentRejected(result.orderId(), result.describe(), now);
    if (!applied) {
      log.info("Ignoring duplicate or late payment result for transfer {} in state {}", transfer.getId(), transfer.getState());
      return false;
    }
    persist(transfer);
    log.info("Transfer {} moved to {} after the payment result", transfer.getId(), transfer.getState());
    return !transfer.isTerminal();
  }

  private void persist(Transfer transfer) {
    transfers.save(transfer);
    outbox.append(transfer.getCorrelationId(), transfer.pullEvents());
  }
}
