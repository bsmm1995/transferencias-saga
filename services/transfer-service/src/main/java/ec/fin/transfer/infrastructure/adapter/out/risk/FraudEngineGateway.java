package ec.fin.transfer.infrastructure.adapter.out.risk;

import ec.fin.transfer.infrastructure.adapter.out.risk.client.api.ApiException;
import ec.fin.transfer.infrastructure.adapter.out.risk.client.api.RiskApi;
import ec.fin.transfer.infrastructure.adapter.out.risk.client.api.model.RiskEvaluation;
import ec.fin.transfer.infrastructure.adapter.out.risk.client.api.model.RiskEvaluationRequest;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/** Resilience boundary (timeout and circuit breaker) around the generated fraud engine client. */
@ApplicationScoped
public class FraudEngineGateway {

  private final RiskApi riskApi;

  public FraudEngineGateway(@RestClient RiskApi riskApi) {
    this.riskApi = riskApi;
  }

  @Timeout(value = 2, unit = ChronoUnit.SECONDS)
  @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10, delayUnit = ChronoUnit.SECONDS)
  public RiskEvaluation evaluate(RiskEvaluationRequest request) {
    try {
      return riskApi.evaluateRisk(request);
    } catch (ApiException e) {
      throw new IllegalStateException("Fraud engine answered HTTP " + e.getResponse().getStatus(), e);
    }
  }
}
