package ec.fin.fraud.application.port.in;

import ec.fin.fraud.domain.model.RiskPolicy.RiskAssessment;
import ec.fin.fraud.domain.model.RiskPolicy.RiskRequest;

/** Driving port: scores a transfer before any money moves. */
public interface EvaluateRiskUseCase {

  RiskAssessment evaluate(RiskRequest request);
}
