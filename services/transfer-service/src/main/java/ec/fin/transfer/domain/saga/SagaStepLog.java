package ec.fin.transfer.domain.saga;

import java.time.Instant;

/** One line of the saga journal: what was attempted, how it ended and when. */
public record SagaStepLog(Step step, Outcome outcome, int attempt, String detail, Instant occurredAt) {

  public enum Step {
    RISK_CHECK,
    DEBIT,
    CREDIT,
    PAYMENT_ORDER,
    PAYMENT_RESULT,
    COMPENSATION
  }

  public enum Outcome {
    REQUESTED,
    SUCCEEDED,
    REJECTED,
    RETRY_SCHEDULED,
    TIMED_OUT,
    FAILED
  }
}
