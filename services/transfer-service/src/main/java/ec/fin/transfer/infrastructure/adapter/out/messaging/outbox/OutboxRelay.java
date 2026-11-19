package ec.fin.transfer.infrastructure.adapter.out.messaging.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;

import ec.fin.contracts.messaging.MessageHeaders;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Publishes outbox rows to the broker (at-least-once). A row is marked as published only after the broker
 * acknowledged it, so a crash in between re-publishes it: consumers deduplicate by {@code event-id} or by state.
 */
@Slf4j
@ApplicationScoped
public class OutboxRelay {

  public static final String CHANNEL = "outbox";

  private final OutboxJpaRepository outbox;
  private final MutinyEmitter<String> emitter;
  private final Clock clock;

  public OutboxRelay(OutboxJpaRepository outbox, @Channel(CHANNEL) MutinyEmitter<String> emitter, Clock clock) {
    this.outbox = outbox;
    this.emitter = emitter;
    this.clock = clock;
  }

  @Transactional
  public int relayBatch(int batchSize) {
    // False until the Kafka producer is connected at startup, and while it applies backpressure: rows wait as PENDING.
    if (!emitter.hasRequests()) {
      log.debug("Outbox channel not ready to accept messages, skipping this run");
      return 0;
    }
    if (!outbox.tryAcquireRelayLock()) {
      return 0;
    }
    List<OutboxMessageEntity> pending = outbox.lockPendingBatch(batchSize);
    // Ordering only matters per transfer: one failing message holds back its own transfer, not the whole outbox.
    Set<UUID> blockedAggregates = new HashSet<>();
    int published = 0;
    for (OutboxMessageEntity message : pending) {
      if (blockedAggregates.contains(message.getAggregateId())) {
        continue;
      }
      try {
        emitter.sendMessageAndAwait(toKafkaMessage(message));
        message.markPublished(clock.instant());
        published++;
      } catch (RuntimeException e) {
        message.markFailedAttempt(e.getMessage());
        blockedAggregates.add(message.getAggregateId());
        log.warn("Outbox message {} of transfer {} could not be published (attempt {}), will retry: {}",
            message.getId(), message.getAggregateId(), message.getAttempts(), e.getMessage());
      }
    }
    if (published > 0) {
      log.debug("Relayed {} outbox messages", published);
    }
    return published;
  }

  @Transactional
  public long purgePublishedOlderThan(Duration retention) {
    return outbox.deletePublishedBefore(clock.instant().minus(retention));
  }

  private static Message<String> toKafkaMessage(OutboxMessageEntity message) {
    RecordHeaders headers = new RecordHeaders();
    headers.add(MessageHeaders.EVENT_ID, message.getEventId().toString().getBytes(UTF_8));
    headers.add(MessageHeaders.EVENT_TYPE, message.getEventType().getBytes(UTF_8));
    if (message.getCorrelationId() != null) {
      headers.add(MessageHeaders.CORRELATION_ID, message.getCorrelationId().getBytes(UTF_8));
    }
    OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
        .withTopic(message.getTopic())
        // Keying by transfer keeps all its messages in one partition, hence in order (the SQS FIFO group equivalent).
        .withKey(message.getAggregateId().toString())
        .withHeaders(headers)
        .build();
    return Message.of(message.getPayload()).addMetadata(metadata);
  }
}
