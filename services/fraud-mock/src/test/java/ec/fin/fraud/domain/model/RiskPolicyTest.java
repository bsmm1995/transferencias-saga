package ec.fin.fraud.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import ec.fin.fraud.domain.model.RiskPolicy.Decision;
import ec.fin.fraud.domain.model.RiskPolicy.RiskRequest;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RiskPolicyTest {

  private final RiskPolicy policy = new RiskPolicy(new BigDecimal("10000"), Set.of("9999999999"));

  @Test
  void ordinaryTransferIsApprovedWithAProportionalScore() {
    var assessment = policy.evaluate(request("2000000001", "5000"));

    assertThat(assessment.decision()).isEqualTo(Decision.APPROVE);
    assertThat(assessment.score()).isEqualTo(30);
  }

  @Test
  void amountAboveTheLimitIsDenied() {
    var assessment = policy.evaluate(request("2000000001", "10000.01"));

    assertThat(assessment.decision()).isEqualTo(Decision.DENY);
    assertThat(assessment.reasons()).containsExactly("AMOUNT_ABOVE_LIMIT");
  }

  @Test
  void blocklistedBeneficiaryIsDenied() {
    var assessment = policy.evaluate(request("9999999999", "1"));

    assertThat(assessment.decision()).isEqualTo(Decision.DENY);
    assertThat(assessment.reasons()).containsExactly("BENEFICIARY_BLOCKLISTED");
  }

  private static RiskRequest request(String beneficiary, String amount) {
    return new RiskRequest("C-1", "1000000001", "001", beneficiary, new BigDecimal(amount), "USD");
  }
}
