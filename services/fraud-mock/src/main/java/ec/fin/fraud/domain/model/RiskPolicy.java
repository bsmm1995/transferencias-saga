package ec.fin.fraud.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic scoring rules, enough to exercise the approve and deny branches of the saga. */
public final class RiskPolicy {

  private static final int DENY_SCORE = 80;

  private final BigDecimal maxAmount;
  private final Set<String> blockedAccounts;

  public RiskPolicy(BigDecimal maxAmount, Set<String> blockedAccounts) {
    this.maxAmount = Objects.requireNonNull(maxAmount, "maxAmount");
    this.blockedAccounts = Set.copyOf(blockedAccounts);
  }

  public RiskAssessment evaluate(RiskRequest request) {
    List<String> reasons = new ArrayList<>();
    int score = request.amount()
        .multiply(BigDecimal.valueOf(60))
        .divide(maxAmount, 0, RoundingMode.HALF_UP)
        .min(BigDecimal.valueOf(60))
        .intValue();
    if (request.amount().compareTo(maxAmount) > 0) {
      reasons.add("AMOUNT_ABOVE_LIMIT");
      score = Math.max(score, 90);
    }
    if (blockedAccounts.contains(request.beneficiaryAccount())) {
      reasons.add("BENEFICIARY_BLOCKLISTED");
      score = Math.max(score, 95);
    }
    Decision decision = score >= DENY_SCORE ? Decision.DENY : Decision.APPROVE;
    return new RiskAssessment(decision, score, List.copyOf(reasons));
  }

  public enum Decision {
    APPROVE,
    DENY
  }

  public record RiskRequest(String customerId, String sourceAccount, String beneficiaryBankCode, String beneficiaryAccount,
      BigDecimal amount, String currency) {
  }

  public record RiskAssessment(Decision decision, int score, List<String> reasons) {
  }
}
