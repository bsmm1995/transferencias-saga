package ec.fin.transfer.infrastructure.adapter.out.messaging.outbox;

import ec.fin.commons.text.Texts;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Row of the transactional outbox: a message written in the same transaction as the state change it announces. */
@Entity
@Table(name = "outbox_message")
@Getter
@Setter
public class OutboxMessageEntity {

  static final int LAST_ERROR_LENGTH = 400;

  /** Monotonic sequence: publishing in id order keeps the events of a transfer in the order they happened. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false, unique = true)
  private UUID eventId;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "topic", nullable = false, length = 120)
  private String topic;

  @Column(name = "event_type", nullable = false, length = 80)
  private String eventType;

  @Column(name = "payload", nullable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "correlation_id", length = 64)
  private String correlationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Status status;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "last_error", length = LAST_ERROR_LENGTH)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  public enum Status {
    PENDING,
    PUBLISHED
  }

  public void markPublished(Instant now) {
    status = Status.PUBLISHED;
    publishedAt = now;
    lastError = null;
  }

  public void markFailedAttempt(String error) {
    attempts++;
    lastError = Texts.truncate(error, LAST_ERROR_LENGTH);
  }
}
