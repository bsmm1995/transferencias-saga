package ec.fin.paymentconnector.infrastructure.config;

import ec.fin.paymentconnector.application.port.out.ClearingNetworkPort;
import ec.fin.paymentconnector.application.port.out.PaymentResultPublisher;
import ec.fin.paymentconnector.application.port.out.ProcessedOrderRegistry;
import ec.fin.paymentconnector.application.service.ProcessPaymentOrderService;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/** Composition root: wires the framework-free use case with its adapters. */
@Dependent
public class UseCaseConfiguration {

  @Produces
  @Singleton
  ProcessPaymentOrderService processPaymentOrder(ProcessedOrderRegistry registry, ClearingNetworkPort network,
      PaymentResultPublisher publisher) {
    return new ProcessPaymentOrderService(registry, network, publisher);
  }
}
