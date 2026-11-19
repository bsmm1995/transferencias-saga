package ec.fin.transfer.domain.model;

import static ec.fin.transfer.domain.saga.SagaStepLog.Outcome.FAILED;
import static ec.fin.transfer.domain.saga.SagaStepLog.Outcome.REJECTED;
import static ec.fin.transfer.domain.saga.SagaStepLog.Outcome.REQUESTED;
import static ec.fin.transfer.domain.saga.SagaStepLog.Outcome.RETRY_SCHEDULED;
import static ec.fin.transfer.domain.saga.SagaStepLog.Outcome.SUCCEEDED;
import static ec.fin.transfer.domain.saga.SagaStepLog.Outcome.TIMED_OUT;

import ec.fin.transfer.domain.event.TransferEvent;
import ec.fin.transfer.domain.saga.SagaCommand;
import ec.fin.transfer.domain.saga.SagaPolicy;
import ec.fin.transfer.domain.saga.SagaState;
import ec.fin.transfer.domain.saga.SagaStepLog;
import ec.fin.transfer.domain.saga.SagaStepLog.Outcome;
import ec.fin.transfer.domain.saga.SagaStepLog.Step;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * Aggregate root of a transfer and of the orchestrated saga that executes it.
 *
 * <pre>
 *                     ┌──────────── OWN_BANK ───────────┐
 * DEBIT_PENDING ──ok──┤                                 ├─> CREDIT_PENDING ──ok──> COMPLETED
 *      │              └── INSTANT / DEFERRED ─> AWAITING_PAYMENT_RESULT ──settled──> COMPLETED
 *      │ rejected                                   │ rejected / unknown
 *      v                                            v
 *   REJECTED     CREDIT_PENDING ──rejected──> COMPENSATING ──ok──> COMPENSATED
 *                                                   └──gave up──> FAILED (manual reconciliation)
 * </pre>
 *
 * Every transition is a local transaction: the new state, the journal lines and the events are persisted together.
 */
@Getter
public final class Transfer {

  private final UUID id;
  private final TransferOrder order;
  private final Rail rail;
  private final String correlationId;
  private final Integer riskScore;
  private final Instant createdAt;
  private final long version;
  private SagaState state;
  private int attempts;
  private Instant nextAttemptAt;
  private String statusReason;
  private Instant updatedAt;

  @Getter(AccessLevel.NONE)
  private final List<SagaStepLog> newSteps = new ArrayList<>();
  @Getter(AccessLevel.NONE)
  private final List<TransferEvent> newEvents = new ArrayList<>();

  @Builder(builderMethodName = "rehydrate", buildMethodName = "restore")
  private Transfer(UUID id, TransferOrder order, Rail rail, String correlationId, Integer riskScore, Instant createdAt,
      long version, SagaState state, int attempts, Instant nextAttemptAt, String statusReason, Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.order = Objects.requireNonNull(order, "order");
    this.rail = Objects.requireNonNull(rail, "rail");
    this.correlationId = correlationId;
    this.riskScore = riskScore;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.version = version;
    this.state = Objects.requireNonNull(state, "state");
    this.attempts = attempts;
    this.nextAttemptAt = nextAttemptAt;
    this.statusReason = statusReason;
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static Transfer accept(UUID id, TransferOrder order, Rail rail, int riskScore, String correlationId, Instant now) {
    Transfer transfer = new Transfer(id, order, rail, correlationId, riskScore, now, 0, SagaState.DEBIT_PENDING, 0, now, null, now);
    transfer.journal(Step.RISK_CHECK, SUCCEEDED, "score " + riskScore, now);
    transfer.raise(new TransferEvent.Accepted(id, rail, now));
    return transfer;
  }

  public static Transfer rejectByRisk(UUID id, TransferOrder order, Rail rail, int riskScore, String reason,
      String correlationId, Instant now) {
    Transfer transfer = new Transfer(id, order, rail, correlationId, riskScore, now, 0, SagaState.REJECTED, 0, null, reason, now);
    transfer.journal(Step.RISK_CHECK, REJECTED, reason, now);
    transfer.raise(new TransferEvent.Rejected(id, reason, now));
    return transfer;
  }

  public TransferStatus status() {
    return state.status();
  }

  public boolean isTerminal() {
    return state.isTerminal();
  }

  public boolean isDue(Instant now) {
    return !isTerminal() && nextAttemptAt != null && !nextAttemptAt.isAfter(now);
  }

  public SagaCommand nextCommand() {
    return switch (state) {
      case DEBIT_PENDING -> new SagaCommand.Debit(debitOperationId(), order.sourceAccount(), order.money(), reference());
      case CREDIT_PENDING -> new SagaCommand.Credit(creditOperationId(), order.beneficiary().accountNumber(), order.money(), reference());
      case AWAITING_PAYMENT_RESULT -> new SagaCommand.ResolvePaymentTimeout(paymentOrderId());
      case COMPENSATING -> new SagaCommand.Compensate(compensableOperations(), statusReason);
      case COMPLETED, REJECTED, COMPENSATED, FAILED -> throw new IllegalStateException("Transfer %s is already %s".formatted(id, state));
    };
  }

  public void debitSucceeded(Instant now, SagaPolicy policy) {
    require(SagaState.DEBIT_PENDING);
    journal(Step.DEBIT, SUCCEEDED, null, now);
    raise(new TransferEvent.FundsDebited(id, now));
    if (rail.settlesInCore()) {
      moveTo(SagaState.CREDIT_PENDING, now, now);
    } else {
      moveTo(SagaState.AWAITING_PAYMENT_RESULT, now.plus(policy.paymentResultTimeout()), now);
      requestPayment(now);
    }
  }

  public void debitRejected(String reason, Instant now) {
    require(SagaState.DEBIT_PENDING);
    journal(Step.DEBIT, REJECTED, reason, now);
    finish(SagaState.REJECTED, reason, now);
    raise(new TransferEvent.Rejected(id, reason, now));
  }

  public void creditSucceeded(Instant now) {
    require(SagaState.CREDIT_PENDING);
    journal(Step.CREDIT, SUCCEEDED, null, now);
    complete(now);
  }

  public void creditRejected(String reason, Instant now) {
    require(SagaState.CREDIT_PENDING);
    journal(Step.CREDIT, REJECTED, reason, now);
    startCompensation("Credit rejected: " + reason, now);
  }

  /** Returns false when the result is late, duplicated or for another order, so redeliveries are harmless. */
  public boolean paymentSettled(UUID orderId, Instant now) {
    if (!awaits(orderId)) {
      return false;
    }
    journal(Step.PAYMENT_RESULT, SUCCEEDED, null, now);
    complete(now);
    return true;
  }

  public boolean paymentRejected(UUID orderId, String reason, Instant now) {
    if (!awaits(orderId)) {
      return false;
    }
    journal(Step.PAYMENT_RESULT, REJECTED, reason, now);
    startCompensation("Payment rejected by the clearing network: " + reason, now);
    return true;
  }

  /**
   * The order is re-sent with the same id, which works as a status inquiry because the connector deduplicates.
   * Compensating here would be wrong: the money may already be at the other bank, so after the last inquiry
   * the transfer goes to FAILED for manual reconciliation.
   */
  public void paymentResultTimedOut(Instant now, SagaPolicy policy) {
    require(SagaState.AWAITING_PAYMENT_RESULT);
    if (attempts < policy.maxPaymentInquiries()) {
      journal(Step.PAYMENT_RESULT, TIMED_OUT, "no answer, inquiry " + (attempts + 1) + " of " + policy.maxPaymentInquiries(), now);
      attempts++;
      nextAttemptAt = now.plus(policy.paymentResultTimeout());
      updatedAt = now;
      requestPayment(now);
      return;
    }
    journal(Step.PAYMENT_RESULT, FAILED, "no answer after " + attempts + " inquiries", now);
    fail("Payment outcome unknown; manual reconciliation required", now);
  }

  public void compensationSucceeded(Instant now) {
    require(SagaState.COMPENSATING);
    journal(Step.COMPENSATION, SUCCEEDED, null, now);
    finish(SagaState.COMPENSATED, statusReason, now);
    raise(new TransferEvent.Reversed(id, statusReason, now));
  }

  /**
   * The participant did not answer (timeout, 5xx, open circuit). The step is retried with backoff because it is
   * idempotent. When retries run out, a forward step is compensated defensively and a compensation is escalated.
   */
  public void stepFailedTransiently(String error, Instant now, SagaPolicy policy) {
    Step step = switch (state) {
      case DEBIT_PENDING -> Step.DEBIT;
      case CREDIT_PENDING -> Step.CREDIT;
      case COMPENSATING -> Step.COMPENSATION;
      default -> throw new IllegalStateException("No remote step runs in state " + state);
    };
    attempts++;
    if (attempts < policy.maxAttempts()) {
      journal(step, RETRY_SCHEDULED, error, now, attempts);
      nextAttemptAt = now.plus(policy.backoffAfter(attempts));
      updatedAt = now;
      return;
    }
    journal(step, FAILED, "gave up after " + attempts + " attempts: " + error, now, attempts);
    if (state == SagaState.COMPENSATING) {
      fail("Compensation could not be completed; manual reconciliation required", now);
    } else {
      startCompensation(step + " outcome unknown after " + attempts + " attempts", now);
    }
  }

  public UUID debitOperationId() {
    return derivedId("debit");
  }

  public UUID creditOperationId() {
    return derivedId("credit");
  }

  public UUID paymentOrderId() {
    return derivedId("payment-order");
  }

  /** Hands the new journal lines to persistence exactly once. */
  public List<SagaStepLog> pullStepLogs() {
    List<SagaStepLog> pulled = List.copyOf(newSteps);
    newSteps.clear();
    return pulled;
  }

  /** Hands the new events to the outbox exactly once. */
  public List<TransferEvent> pullEvents() {
    List<TransferEvent> pulled = List.copyOf(newEvents);
    newEvents.clear();
    return pulled;
  }

  // A credit that never happened answers NOT_FOUND on reversal, so the full list is always safe to reverse.
  private List<UUID> compensableOperations() {
    return rail.settlesInCore() ? List.of(creditOperationId(), debitOperationId()) : List.of(debitOperationId());
  }

  private void requestPayment(Instant now) {
    journal(Step.PAYMENT_ORDER, REQUESTED, rail.name(), now);
    raise(new TransferEvent.PaymentOrderRequested(id, paymentOrderId(), rail, order, now));
  }

  private void complete(Instant now) {
    finish(SagaState.COMPLETED, null, now);
    raise(new TransferEvent.Completed(id, now));
  }

  private void startCompensation(String reason, Instant now) {
    statusReason = reason;
    moveTo(SagaState.COMPENSATING, now, now);
    raise(new TransferEvent.CompensationStarted(id, reason, now));
  }

  private void fail(String reason, Instant now) {
    finish(SagaState.FAILED, reason, now);
    raise(new TransferEvent.Failed(id, reason, now));
  }

  private void moveTo(SagaState next, Instant dueAt, Instant now) {
    state = next;
    attempts = 0;
    nextAttemptAt = dueAt;
    updatedAt = now;
  }

  private void finish(SagaState terminal, String reason, Instant now) {
    state = terminal;
    nextAttemptAt = null;
    statusReason = reason;
    updatedAt = now;
  }

  private boolean awaits(UUID orderId) {
    return state == SagaState.AWAITING_PAYMENT_RESULT && paymentOrderId().equals(orderId);
  }

  private void require(SagaState expected) {
    if (state != expected) {
      throw new IllegalStateException("Transfer %s is %s, expected %s".formatted(id, state, expected));
    }
  }

  private String reference() {
    return "TRF " + id;
  }

  private UUID derivedId(String purpose) {
    return UUID.nameUUIDFromBytes((id + ":" + purpose).getBytes(StandardCharsets.UTF_8));
  }

  private void journal(Step step, Outcome outcome, String detail, Instant now) {
    journal(step, outcome, detail, now, attempts + 1);
  }

  private void journal(Step step, Outcome outcome, String detail, Instant now, int attempt) {
    newSteps.add(new SagaStepLog(step, outcome, attempt, detail, now));
  }

  private void raise(TransferEvent event) {
    newEvents.add(event);
  }
}
