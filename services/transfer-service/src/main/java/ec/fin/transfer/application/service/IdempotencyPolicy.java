package ec.fin.transfer.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * How long an {@code Idempotency-Key} protects a request. Within the retention a retry replays the original
 * transfer; afterwards the key is free again, so storage stays bounded and clients cannot rely on keys forever.
 */
public record IdempotencyPolicy(Duration retention) {

  public IdempotencyPolicy {
    Objects.requireNonNull(retention, "retention");
    if (retention.isNegative() || retention.isZero()) {
      throw new IllegalArgumentException("Idempotency retention must be positive");
    }
  }

  /** Records created before this instant are expired. */
  public Instant validSince(Instant now) {
    return now.minus(retention);
  }
}
