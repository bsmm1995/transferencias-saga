package ec.fin.transfer.infrastructure.adapter.out.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoreBankingGatewayTest {

  @ParameterizedTest
  @ValueSource(ints = {400, 404, 409, 422})
  void clientErrorsAreDefinitiveSoTheSagaDoesNotRetryThem(int status) {
    assertThat(CoreBankingGateway.isDefinitiveRejection(status)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {408, 429, 500, 502, 503, 504})
  void timeoutsThrottlingAndServerErrorsAreRetried(int status) {
    assertThat(CoreBankingGateway.isDefinitiveRejection(status)).isFalse();
  }
}
