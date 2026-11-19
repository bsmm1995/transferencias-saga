package ec.fin.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IdempotencyPolicyTest {

  @Test
  void keysCreatedWithinTheRetentionAreStillValid() {
    Instant now = Instant.parse("2026-11-18T10:00:00Z");

    assertThat(new IdempotencyPolicy(Duration.ofHours(24)).validSince(now))
        .isEqualTo(Instant.parse("2026-11-17T10:00:00Z"));
  }

  @Test
  void rejectsANonPositiveRetention() {
    assertThatThrownBy(() -> new IdempotencyPolicy(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
  }
}
