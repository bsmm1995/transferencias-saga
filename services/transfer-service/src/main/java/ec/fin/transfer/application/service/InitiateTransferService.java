package ec.fin.transfer.application.service;

import ec.fin.commons.error.ConflictException;
import ec.fin.transfer.application.port.in.InitiateTransferUseCase;
import ec.fin.transfer.application.port.out.DuplicateIdempotencyKeyException;
import ec.fin.transfer.application.port.out.EventOutbox;
import ec.fin.transfer.application.port.out.IdempotencyStore;
import ec.fin.transfer.application.port.out.IdempotencyStore.IdempotencyRecord;
import ec.fin.transfer.application.port.out.RiskAssessmentPort;
import ec.fin.transfer.application.port.out.RiskAssessmentPort.RiskAssessment;
import ec.fin.transfer.application.port.out.SagaDispatcher;
import ec.fin.transfer.application.port.out.TransferRepository;
import ec.fin.transfer.application.port.out.UnitOfWork;
import ec.fin.transfer.domain.model.Rail;
import ec.fin.transfer.domain.model.Transfer;
import ec.fin.transfer.domain.model.TransferOrder;
import ec.fin.transfer.domain.service.RailRouter;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Idempotency guard, risk check and saga start, as in the "Iniciar Transferencia" component of the L3 diagram. */
@Slf4j
@RequiredArgsConstructor
public class InitiateTransferService implements InitiateTransferUseCase {

  private final TransferRepository transfers;
  private final IdempotencyStore idempotency;
  private final EventOutbox outbox;
  private final RiskAssessmentPort risk;
  private final RailRouter railRouter;
  private final UnitOfWork unitOfWork;
  private final SagaDispatcher dispatcher;
  private final Clock clock;
  private final IdempotencyPolicy idempotencyPolicy;

  @Override
  public InitiationResult initiate(InitiateTransferCommand command) {
    String fingerprint = RequestFingerprint.of(command);
    // Fixed once per request, so the replay check and the registration agree on which records are expired.
    Instant validSince = idempotencyPolicy.validSince(clock.instant());
    Optional<InitiationResult> replay =
        unitOfWork.inTransaction(() -> replayOf(command.idempotencyKey(), fingerprint, validSince));
    if (replay.isPresent()) {
      log.info("Idempotent replay of transfer {}", replay.get().transfer().getId());
      return replay.get();
    }

    TransferOrder order = command.toOrder();
    Rail rail = railRouter.route(order);
    UUID transferId = UUID.randomUUID();
    RiskAssessment assessment = risk.assess(transferId, order);
    Instant now = clock.instant();
    Transfer transfer = assessment.approved()
        ? Transfer.accept(transferId, order, rail, assessment.score(), command.correlationId(), now)
        : Transfer.rejectByRisk(transferId, order, rail, assessment.score(),
            "Rejected by the fraud engine: " + String.join(", ", assessment.reasons()), command.correlationId(), now);

    try {
      unitOfWork.inTransaction(() -> {
        transfers.save(transfer);
        idempotency.register(new IdempotencyRecord(command.idempotencyKey(), fingerprint, transferId, now), validSince);
        outbox.append(transfer.getCorrelationId(), transfer.pullEvents());
      });
    } catch (DuplicateIdempotencyKeyException e) {
      // A concurrent request with the same key won the race: answer exactly what it answered.
      return unitOfWork.inTransaction(() -> replayOf(command.idempotencyKey(), fingerprint, validSince)).orElseThrow(() -> e);
    }

    log.info("Transfer {} registered on rail {} with status {}", transferId, rail, transfer.status());
    if (!transfer.isTerminal()) {
      dispatcher.dispatch(transferId);
    }
    return new InitiationResult(transfer, false);
  }

  private Optional<InitiationResult> replayOf(String idempotencyKey, String fingerprint, Instant validSince) {
    return idempotency.findValid(idempotencyKey, validSince).map(record -> {
      if (!record.requestFingerprint().equals(fingerprint)) {
        throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "The Idempotency-Key was already used with a different request");
      }
      Transfer original = transfers.findById(record.transferId())
          .orElseThrow(() -> new IllegalStateException("Idempotency record points to a missing transfer " + record.transferId()));
      return new InitiationResult(original, true);
    });
  }
}
