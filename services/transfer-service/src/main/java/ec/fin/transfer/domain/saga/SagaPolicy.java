package ec.fin.transfer.domain.saga;

import java.time.Duration;
import java.util.Objects;

/**
 * How patient the saga is. Retries of a step use exponential backoff; once {@code maxAttempts} is reached
 * the outcome of a forward step is treated as unknown and the saga compensates.
 */
public record SagaPolicy(
    int maxAttempts,
    Duration initialBackoff,
    double backoffMultiplier,
    Duration maxBackoff,
    Duration paymentResultTimeout,
    int maxPaymentInquiries) {

  public SagaPolicy {
    if (maxAttempts < 1 || maxPaymentInquiries < 0 || backoffMultiplier < 1) {
      throw new IllegalArgumentException("Invalid saga policy");
    }
    Objects.requireNonNull(initialBackoff, "initialBackoff");
    Objects.requireNonNull(maxBackoff, "maxBackoff");
    Objects.requireNonNull(paymentResultTimeout, "paymentResultTimeout");
  }

  /** Delay before retry number {@code failedAttempts} (1-based). */
  public Duration backoffAfter(int failedAttempts) {
    double factor = Math.pow(backoffMultiplier, Math.max(0, failedAttempts - 1));
    long millis = (long) Math.min(initialBackoff.toMillis() * factor, maxBackoff.toMillis());
    return Duration.ofMillis(millis);
  }
}
