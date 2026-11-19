package ec.fin.transfer.infrastructure.adapter.out.risk;

import ec.fin.commons.error.DependencyUnavailableException;
import ec.fin.transfer.application.port.out.RiskAssessmentPort;
import ec.fin.transfer.domain.model.TransferOrder;
import ec.fin.transfer.infrastructure.adapter.out.risk.client.api.model.RiskEvaluation;
import ec.fin.transfer.infrastructure.adapter.out.risk.client.api.model.RiskEvaluationRequest;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** Adaptador Antifraude of the L3 diagram. Fails closed: without a risk answer no money moves. */
@ApplicationScoped
@RequiredArgsConstructor
public class FraudEngineRiskAdapter implements RiskAssessmentPort {

  private final FraudEngineGateway gateway;

  @Override
  public RiskAssessment assess(UUID transferId, TransferOrder order) {
    RiskEvaluationRequest request = new RiskEvaluationRequest()
        .transferId(transferId)
        .customerId(order.customerId())
        .sourceAccount(order.sourceAccount())
        .beneficiaryBankCode(order.beneficiary().bankCode())
        .beneficiaryAccount(order.beneficiary().accountNumber())
        .amount(order.money().amount())
        .currency(order.money().currency().name());
    try {
      RiskEvaluation evaluation = gateway.evaluate(request);
      return new RiskAssessment(
          evaluation.getDecision() == RiskEvaluation.DecisionEnum.APPROVE,
          evaluation.getScore() == null ? 0 : evaluation.getScore(),
          evaluation.getReasons() == null ? List.of() : List.copyOf(evaluation.getReasons()));
    } catch (RuntimeException e) {
      throw new DependencyUnavailableException("fraud-engine", "The fraud engine is not available, retry later", e);
    }
  }
}
