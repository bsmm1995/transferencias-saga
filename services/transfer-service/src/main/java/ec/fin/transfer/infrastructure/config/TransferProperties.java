package ec.fin.transfer.infrastructure.config;

import io.smallrye.config.ConfigMapping;
import java.time.Duration;
import java.util.Set;

/** Typed configuration of the transfer service, bound to the {@code transfer.*} properties. */
@ConfigMapping(prefix = "transfer")
public interface TransferProperties {

  Rails rails();

  Saga saga();

  Outbox outbox();

  Idempotency idempotency();

  interface Rails {

    String ownBankCode();

    Set<String> instantBankCodes();
  }

  interface Saga {

    Duration pollInterval();

    int batchSize();

    int maxAttempts();

    Duration initialBackoff();

    double backoffMultiplier();

    Duration maxBackoff();

    Duration paymentResultTimeout();

    int maxPaymentInquiries();
  }

  interface Outbox {

    Duration pollInterval();

    int batchSize();

    Duration retention();
  }

  interface Idempotency {

    /** How long a key replays its original transfer; afterwards it is purged and may be reused. */
    Duration retention();

    Duration purgeInterval();
  }
}
