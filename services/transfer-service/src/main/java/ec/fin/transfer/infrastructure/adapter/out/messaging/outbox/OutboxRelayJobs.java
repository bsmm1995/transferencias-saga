package ec.fin.transfer.infrastructure.adapter.out.messaging.outbox;

import ec.fin.transfer.infrastructure.config.TransferProperties;
import ec.fin.transfer.infrastructure.config.UntilApplicationStarted;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Drives the outbox relay. It belongs to the publishing adapter, not to the driving scheduler adapter, because it
 * runs no use case: it is how this adapter delivers what {@link OutboxEventAppender} stored. In the AWS target it
 * could be replaced by CDC on the outbox table.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class OutboxRelayJobs {

  private final OutboxRelay outboxRelay;
  private final TransferProperties properties;

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
}
