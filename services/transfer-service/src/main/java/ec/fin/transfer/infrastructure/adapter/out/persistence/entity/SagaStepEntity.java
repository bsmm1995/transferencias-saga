package ec.fin.transfer.infrastructure.adapter.out.persistence.entity;

import ec.fin.transfer.domain.saga.SagaStepLog;
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

/** JPA mapping of the saga_step table: the append-only journal of every saga. */
@Entity
@Table(name = "saga_step")
@Getter
@Setter
public class SagaStepEntity {

  public static final int DETAIL_LENGTH = 400;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "transfer_id", nullable = false)
  private UUID transferId;

  @Enumerated(EnumType.STRING)
  @Column(name = "step", nullable = false, length = 20)
  private SagaStepLog.Step step;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, length = 20)
  private SagaStepLog.Outcome outcome;

  @Column(name = "attempt", nullable = false)
  private int attempt;

  @Column(name = "detail", length = DETAIL_LENGTH)
  private String detail;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}
