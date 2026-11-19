package ec.fin.transfer.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.fin.contracts.messaging.PaymentResultMessage;
import ec.fin.transfer.application.port.in.HandlePaymentResultUseCase;
import ec.fin.transfer.application.port.in.HandlePaymentResultUseCase.PaymentResult;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Consumidor de Resultados of the L3 diagram. Redeliveries are safe because the aggregate ignores results it
 * no longer waits for; a message that keeps failing ends in the dead letter topic.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PaymentResultConsumer {

  public static final String CHANNEL = "payment-results";

  private final HandlePaymentResultUseCase paymentResults;
  private final ObjectMapper json;

  @Incoming(CHANNEL)
  @RunOnVirtualThread
  public void consume(String payload) throws JsonProcessingException {
    PaymentResultMessage message = json.readValue(payload, PaymentResultMessage.class);
    log.info("Payment result {} for transfer {}: {}", message.orderId(), message.transferId(), message.status());
    paymentResults.handle(new PaymentResult(
        message.orderId(),
        message.transferId(),
        message.status() == PaymentResultMessage.PaymentStatus.SETTLED,
        message.reasonCode(),
        message.reasonDetail()));
  }
}
