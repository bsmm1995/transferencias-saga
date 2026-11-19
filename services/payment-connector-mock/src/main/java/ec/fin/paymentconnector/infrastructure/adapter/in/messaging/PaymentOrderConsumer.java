package ec.fin.paymentconnector.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.fin.contracts.messaging.PaymentOrderMessage;
import ec.fin.paymentconnector.application.port.in.ProcessPaymentOrderUseCase;
import ec.fin.paymentconnector.domain.model.PaymentOrder;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/** Driving adapter: consumes the payment-orders topic. Messages that keep failing go to the dead letter topic. */
@ApplicationScoped
@RequiredArgsConstructor
public class PaymentOrderConsumer {

  public static final String CHANNEL = "payment-orders";

  private final ProcessPaymentOrderUseCase processOrder;
  private final ObjectMapper json;

  @Incoming(CHANNEL)
  @RunOnVirtualThread
  public void consume(String payload) throws JsonProcessingException {
    PaymentOrderMessage message = json.readValue(payload, PaymentOrderMessage.class);
    processOrder.process(new PaymentOrder(
        message.orderId(),
        message.transferId(),
        PaymentOrder.ClearingNetwork.valueOf(message.clearingSystem().name()),
        message.debtorAccount(),
        message.creditorBankCode(),
        message.creditorAccount(),
        message.creditorName(),
        message.amount(),
        message.currency()));
  }
}
