package ec.fin.fraud.infrastructure.adapter.in.rest;

import ec.fin.fraud.application.port.in.EvaluateRiskUseCase;
import ec.fin.fraud.domain.model.RiskPolicy.RiskAssessment;
import ec.fin.fraud.domain.model.RiskPolicy.RiskRequest;
import ec.fin.fraud.infrastructure.adapter.in.rest.api.RiskApi;
import ec.fin.fraud.infrastructure.adapter.in.rest.api.model.RiskEvaluation;
import ec.fin.fraud.infrastructure.adapter.in.rest.api.model.RiskEvaluationRequest;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Driving adapter: implements the generated RiskApi. Optional simulated latency exercises the timeouts of the caller. */
@RunOnVirtualThread
public class RiskResource implements RiskApi {

  private final EvaluateRiskUseCase evaluateRisk;
  // Lets you push the transfer service into its timeout and circuit breaker paths.
  private final Duration simulatedLatency;

  public RiskResource(EvaluateRiskUseCase evaluateRisk, @ConfigProperty(name = "fraud.simulated-latency") Duration simulatedLatency) {
    this.evaluateRisk = evaluateRisk;
    this.simulatedLatency = simulatedLatency;
  }

  @Override
  public Response evaluateRisk(RiskEvaluationRequest request) {
    simulateLatency();
    RiskAssessment assessment = evaluateRisk.evaluate(new RiskRequest(
        request.getCustomerId(),
        request.getSourceAccount(),
        request.getBeneficiaryBankCode(),
        request.getBeneficiaryAccount(),
        request.getAmount(),
        request.getCurrency()));
    return Response.ok(new RiskEvaluation()
            .decision(RiskEvaluation.DecisionEnum.fromValue(assessment.decision().name()))
            .score(assessment.score())
            .reasons(assessment.reasons()))
        .build();
  }

  private void simulateLatency() {
    if (!simulatedLatency.isZero()) {
      try {
        Thread.sleep(simulatedLatency);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
