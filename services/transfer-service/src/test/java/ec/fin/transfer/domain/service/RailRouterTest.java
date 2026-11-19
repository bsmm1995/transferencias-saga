package ec.fin.transfer.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.fin.commons.error.BusinessRuleViolationException;
import ec.fin.transfer.domain.model.Beneficiary;
import ec.fin.transfer.domain.model.Money;
import ec.fin.transfer.domain.model.Rail;
import ec.fin.transfer.domain.model.TransferOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class RailRouterTest {

  private final RailRouter router = RailRouter.standard("001", Set.of("010"));

  @ParameterizedTest
  @CsvSource({"001, OWN_BANK", "010, INSTANT", "099, DEFERRED"})
  void routesByBeneficiaryBank(String bankCode, Rail expected) {
    assertThat(router.route(order(bankCode, "2000000001"))).isEqualTo(expected);
  }

  @Test
  void refusesATransferToTheSameAccount() {
    assertThatThrownBy(() -> router.route(order("001", "1000000001")))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "SAME_ACCOUNT");
  }

  @Test
  void firstMatchingRuleWinsSoANewStrategyCanBePluggedInWithoutTouchingTheOthers() {
    RailRoutingRule forceInstantForBank099 =
        order -> "099".equals(order.beneficiary().bankCode()) ? Optional.of(Rail.INSTANT) : Optional.empty();
    RailRouter custom = new RailRouter(List.of(
        new OwnBankRule("001"), forceInstantForBank099, new InstantNetworkRule(Set.of("010")), new DeferredFallbackRule()));

    assertThat(custom.route(order("099", "2000000001"))).isEqualTo(Rail.INSTANT);
    assertThat(custom.route(order("050", "2000000001"))).isEqualTo(Rail.DEFERRED);
  }

  @Test
  void failsLoudlyWhenNoRuleMatches() {
    RailRouter withoutFallback = new RailRouter(List.of(new OwnBankRule("001")));

    assertThatThrownBy(() -> withoutFallback.route(order("099", "2000000001")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsAmountsWithMoreThanTwoDecimals() {
    assertThatThrownBy(() -> Money.of(new BigDecimal("1.001"), "USD"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "INVALID_AMOUNT");
  }

  private static TransferOrder order(String bankCode, String account) {
    return new TransferOrder("C-1", "1000000001", new Beneficiary(bankCode, account, "Someone"),
        Money.of(BigDecimal.TEN, "USD"), null);
  }
}
