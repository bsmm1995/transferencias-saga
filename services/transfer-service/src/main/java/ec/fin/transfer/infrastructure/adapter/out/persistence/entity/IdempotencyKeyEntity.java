package ec.fin.transfer.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** JPA mapping of the idempotency_key table. Its primary key serializes concurrent requests with the same key. */
@Entity
@Table(name = "idempotency_key")
@Getter
@Setter
public class IdempotencyKeyEntity {

  @Id
  @Column(name = "idempotency_key", length = 80)
  private String key;

  @Column(name = "request_fingerprint", nullable = false, length = 64)
  private String requestFingerprint;

  @Column(name = "transfer_id", nullable = false)
  private UUID transferId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
