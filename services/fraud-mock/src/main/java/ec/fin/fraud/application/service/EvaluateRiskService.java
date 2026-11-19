package ec.fin.fraud.application.service;

import ec.fin.fraud.application.port.in.EvaluateRiskUseCase;
import ec.fin.fraud.domain.model.RiskPolicy;
import ec.fin.fraud.domain.model.RiskPolicy.RiskAssessment;
import ec.fin.fraud.domain.model.RiskPolicy.RiskRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Use case: applies the risk policy and records the decision. */
@Slf4j
@RequiredArgsConstructor
public class EvaluateRiskService implements EvaluateRiskUseCase {

  private final RiskPolicy policy;

  @Override
  public RiskAssessment evaluate(RiskRequest request) {
    RiskAssessment assessment = policy.evaluate(request);
    log.info("Risk for {} {} to {}: {} (score {}, {})", request.amount(), request.currency(), request.beneficiaryAccount(),
        assessment.decision(), assessment.score(), assessment.reasons());
    return assessment;
  }
}
