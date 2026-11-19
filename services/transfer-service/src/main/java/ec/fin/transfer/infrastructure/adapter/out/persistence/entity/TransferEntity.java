package ec.fin.transfer.infrastructure.adapter.out.persistence.entity;

import ec.fin.transfer.domain.model.Rail;
import ec.fin.transfer.domain.saga.SagaState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** JPA mapping of the transfer table: business data plus the persisted saga state. */
@Entity
@Table(name = "transfer")
@Getter
@Setter
public class TransferEntity {

  @Id
  private UUID id;

  @Column(name = "customer_id", nullable = false, length = 40)
  private String customerId;

  @Column(name = "correlation_id", length = 64)
  private String correlationId;

  @Column(name = "source_account", nullable = false, length = 10)
  private String sourceAccount;

  @Column(name = "beneficiary_bank_code", nullable = false, length = 3)
  private String beneficiaryBankCode;

  @Column(name = "beneficiary_account", nullable = false, length = 10)
  private String beneficiaryAccount;

  @Column(name = "beneficiary_name", nullable = false, length = 80)
  private String beneficiaryName;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal amount;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "concept", length = 140)
  private String concept;

  @Enumerated(EnumType.STRING)
  @Column(name = "rail", nullable = false, length = 20)
  private Rail rail;

  @Column(name = "risk_score")
  private Integer riskScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "saga_state", nullable = false, length = 30)
  private SagaState sagaState;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "status_reason", length = 400)
  private String statusReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;
}
