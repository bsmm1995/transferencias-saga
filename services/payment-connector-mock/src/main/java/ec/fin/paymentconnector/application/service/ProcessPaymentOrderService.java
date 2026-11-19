package ec.fin.paymentconnector.application.service;

import ec.fin.paymentconnector.application.port.in.ProcessPaymentOrderUseCase;
import ec.fin.paymentconnector.application.port.out.ClearingNetworkPort;
import ec.fin.paymentconnector.application.port.out.PaymentResultPublisher;
import ec.fin.paymentconnector.application.port.out.ProcessedOrderRegistry;
import ec.fin.paymentconnector.domain.model.ClearingResult;
import ec.fin.paymentconnector.domain.model.PaymentOrder;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Use case: idempotent receiver of payment orders. A re-sent order republishes its original result. */
@Slf4j
@RequiredArgsConstructor
public class ProcessPaymentOrderService implements ProcessPaymentOrderUseCase {

  private final ProcessedOrderRegistry registry;
  private final ClearingNetworkPort network;
  private final PaymentResultPublisher publisher;

  @Override
  public void process(PaymentOrder order) {
    Optional<ClearingResult> known = registry.find(order.orderId());
    if (known.isPresent()) {
      log.info("Order {} already cleared as {}, republishing the result", order.orderId(), known.get().outcome());
      publisher.publish(known.get());
      return;
    }
    Optional<ClearingResult> result = network.submit(order);
    if (result.isEmpty()) {
      log.warn("Order {} got no answer from {} (simulated loss)", order.orderId(), order.network());
      return;
    }
    registry.remember(result.get());
    publisher.publish(result.get());
    log.info("Order {} of transfer {} cleared through {}: {}", order.orderId(), order.transferId(), order.network(), result.get().outcome());
  }
}
