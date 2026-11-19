package ec.fin.transfer.infrastructure.adapter.out.persistence.mapper;

import ec.fin.commons.text.Texts;
import ec.fin.transfer.domain.model.Beneficiary;
import ec.fin.transfer.domain.model.Money;
import ec.fin.transfer.domain.model.Transfer;
import ec.fin.transfer.domain.model.TransferOrder;
import ec.fin.transfer.domain.saga.SagaStepLog;
import ec.fin.transfer.infrastructure.adapter.out.persistence.entity.SagaStepEntity;
import ec.fin.transfer.infrastructure.adapter.out.persistence.entity.TransferEntity;
import java.util.UUID;

/** Maps the transfer aggregate to and from its JPA entities. */
public final class TransferEntityMapper {

  private TransferEntityMapper() {
  }

  public static Transfer toDomain(TransferEntity entity) {
    TransferOrder order = new TransferOrder(
        entity.getCustomerId(),
        entity.getSourceAccount(),
        new Beneficiary(entity.getBeneficiaryBankCode(), entity.getBeneficiaryAccount(), entity.getBeneficiaryName()),
        Money.of(entity.getAmount(), entity.getCurrency()),
        entity.getConcept());
    return Transfer.rehydrate()
        .id(entity.getId())
        .order(order)
        .rail(entity.getRail())
        .correlationId(entity.getCorrelationId())
        .riskScore(entity.getRiskScore())
        .createdAt(entity.getCreatedAt())
        .version(entity.getVersion())
        .state(entity.getSagaState())
        .attempts(entity.getAttempts())
        .nextAttemptAt(entity.getNextAttemptAt())
        .statusReason(entity.getStatusReason())
        .updatedAt(entity.getUpdatedAt())
        .restore();
  }

  public static TransferEntity newEntity(Transfer transfer) {
    TransferOrder order = transfer.getOrder();
    TransferEntity entity = new TransferEntity();
    entity.setId(transfer.getId());
    entity.setCustomerId(order.customerId());
    entity.setCorrelationId(transfer.getCorrelationId());
    entity.setSourceAccount(order.sourceAccount());
    entity.setBeneficiaryBankCode(order.beneficiary().bankCode());
    entity.setBeneficiaryAccount(order.beneficiary().accountNumber());
    entity.setBeneficiaryName(order.beneficiary().holderName());
    entity.setAmount(order.money().amount());
    entity.setCurrency(order.money().currency().name());
    entity.setConcept(order.concept());
    entity.setRail(transfer.getRail());
    entity.setRiskScore(transfer.getRiskScore());
    entity.setCreatedAt(transfer.getCreatedAt());
    copySagaState(transfer, entity);
    return entity;
  }

  /** Only the saga part of a transfer changes after creation. */
  public static void copySagaState(Transfer transfer, TransferEntity entity) {
    entity.setSagaState(transfer.getState());
    entity.setAttempts(transfer.getAttempts());
    entity.setNextAttemptAt(transfer.getNextAttemptAt());
    entity.setStatusReason(transfer.getStatusReason());
    entity.setUpdatedAt(transfer.getUpdatedAt());
  }

  public static SagaStepEntity toEntity(UUID transferId, SagaStepLog log) {
    SagaStepEntity entity = new SagaStepEntity();
    entity.setTransferId(transferId);
    entity.setStep(log.step());
    entity.setOutcome(log.outcome());
    entity.setAttempt(log.attempt());
    entity.setDetail(Texts.truncate(log.detail(), SagaStepEntity.DETAIL_LENGTH));
    entity.setOccurredAt(log.occurredAt());
    return entity;
  }

  public static SagaStepLog toDomain(SagaStepEntity entity) {
    return new SagaStepLog(entity.getStep(), entity.getOutcome(), entity.getAttempt(), entity.getDetail(), entity.getOccurredAt());
  }
}
