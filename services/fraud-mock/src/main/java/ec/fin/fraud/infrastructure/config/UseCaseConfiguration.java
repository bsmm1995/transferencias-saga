package ec.fin.fraud.infrastructure.config;

import ec.fin.fraud.application.service.EvaluateRiskService;
import ec.fin.fraud.domain.model.RiskPolicy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Composition root: builds the risk policy from configuration and wires the use case. */
@Dependent
public class UseCaseConfiguration {

  @Produces
  @Singleton
  EvaluateRiskService evaluateRiskService(
      @ConfigProperty(name = "fraud.policy.max-amount") BigDecimal maxAmount,
      @ConfigProperty(name = "fraud.policy.blocked-accounts") Set<String> blockedAccounts) {
    return new EvaluateRiskService(new RiskPolicy(maxAmount, blockedAccounts));
  }
}
