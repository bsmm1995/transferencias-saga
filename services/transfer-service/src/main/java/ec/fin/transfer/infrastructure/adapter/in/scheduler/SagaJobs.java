package ec.fin.transfer.infrastructure.adapter.in.scheduler;

import ec.fin.transfer.application.port.in.AdvanceSagaUseCase;
import ec.fin.transfer.application.port.in.PurgeExpiredIdempotencyKeysUseCase;
import ec.fin.transfer.infrastructure.adapter.out.messaging.outbox.OutboxRelay;
import ec.fin.transfer.infrastructure.config.TransferProperties;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Background work. In the AWS target these would run as scheduled tasks too; the outbox relay could also be
 * replaced by CDC on the outbox table.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SagaJobs {

  private final AdvanceSagaUseCase sagas;
  private final PurgeExpiredIdempotencyKeysUseCase idempotencyKeys;
  private final OutboxRelay outboxRelay;
  private final TransferProperties properties;

  @RunOnVirtualThread
  @Scheduled(identity = "saga-recovery", every = "${transfer.saga.poll-interval}", delayed = "2s",
      concurrentExecution = ConcurrentExecution.SKIP, skipExecutionIf = UntilApplicationStarted.class)
  void advanceDueSagas() {
    sagas.advanceDue();
  }

  @RunOnVirtualThread
  @Scheduled(identity = "outbox-relay", every = "${transfer.outbox.poll-interval}", delayed = "1s",
      concurrentExecution = ConcurrentExecution.SKIP, skipExecutionIf = UntilApplicationStarted.class)
  void relayOutbox() {
    outboxRelay.relayBatch(properties.outbox().batchSize());
  }

  @RunOnVirtualThread
  @Scheduled(identity = "outbox-purge", every = "1h", delayed = "1m", concurrentExecution = ConcurrentExecution.SKIP,
      skipExecutionIf = UntilApplicationStarted.class)
  void purgeOutbox() {
    long purged = outboxRelay.purgePublishedOlderThan(properties.outbox().retention());
    if (purged > 0) {
      log.info("Purged {} published outbox messages", purged);
    }
  }

  @RunOnVirtualThread
  @Scheduled(identity = "idempotency-key-purge", every = "${transfer.idempotency.purge-interval}", delayed = "1m",
      concurrentExecution = ConcurrentExecution.SKIP, skipExecutionIf = UntilApplicationStarted.class)
  void purgeIdempotencyKeys() {
    long purged = idempotencyKeys.purgeExpired();
    if (purged > 0) {
      log.info("Purged {} expired idempotency keys", purged);
    }
  }
}
