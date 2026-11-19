package ec.fin.paymentconnector.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import ec.fin.paymentconnector.domain.model.ClearingResult;
import ec.fin.paymentconnector.domain.model.PaymentOrder;
import ec.fin.paymentconnector.infrastructure.adapter.out.clearing.SimulatedBceNetwork;
import ec.fin.paymentconnector.infrastructure.adapter.out.memory.InMemoryProcessedOrderRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessPaymentOrderServiceTest {

  private final List<ClearingResult> published = new ArrayList<>();
  private final ProcessPaymentOrderService service = new ProcessPaymentOrderService(
      new InMemoryProcessedOrderRegistry(), new SimulatedBceNetwork(Duration.ZERO, Duration.ZERO), published::add);

  @Test
  void resentOrderRepublishesTheSameResultInsteadOfPayingTwice() {
    PaymentOrder order = order("3000000001");

    service.process(order);
    service.process(order);

    assertThat(published).hasSize(2).allSatisfy(result -> assertThat(result.outcome()).isEqualTo(ClearingResult.Outcome.SETTLED));
    assertThat(published.get(0)).isSameAs(published.get(1));
  }

  @Test
  void unknownCreditorAccountIsRejectedWithIsoReasonCode() {
    service.process(order("3000000000"));

    assertThat(published).singleElement().satisfies(result -> {
      assertThat(result.outcome()).isEqualTo(ClearingResult.Outcome.REJECTED);
      assertThat(result.reasonCode()).isEqualTo("AC01");
    });
  }

  @Test
  void lostOrderPublishesNothing() {
    service.process(order("3000007777"));

    assertThat(published).isEmpty();
  }

  private static PaymentOrder order(String creditorAccount) {
    return new PaymentOrder(UUID.randomUUID(), UUID.randomUUID(), PaymentOrder.ClearingNetwork.RPI, "1000000001", "010",
        creditorAccount, "Creditor", BigDecimal.TEN, "USD");
  }
}
