package ec.fin.transfer.infrastructure.adapter.in.scheduler;

import ec.fin.transfer.application.port.in.AdvanceSagaUseCase;
import ec.fin.transfer.application.port.in.PurgeExpiredIdempotencyKeysUseCase;
import ec.fin.transfer.infrastructure.config.UntilApplicationStarted;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Background use cases. In the AWS target these would run as scheduled tasks (EventBridge Scheduler + Lambda). */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SagaJobs {

  private final AdvanceSagaUseCase sagas;
  private final PurgeExpiredIdempotencyKeysUseCase idempotencyKeys;

  @RunOnVirtualThread
  @Scheduled(identity = "saga-recovery", every = "${transfer.saga.poll-interval}", delayed = "2s",
      concurrentExecution = ConcurrentExecution.SKIP, skipExecutionIf = UntilApplicationStarted.class)
  void advanceDueSagas() {
    sagas.advanceDue();
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
