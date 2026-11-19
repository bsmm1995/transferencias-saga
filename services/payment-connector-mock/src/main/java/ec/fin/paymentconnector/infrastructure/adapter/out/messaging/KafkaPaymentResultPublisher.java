package ec.fin.paymentconnector.infrastructure.adapter.out.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.fin.contracts.messaging.MessageHeaders;
import ec.fin.contracts.messaging.PaymentResultMessage;
import ec.fin.paymentconnector.application.port.out.PaymentResultPublisher;
import ec.fin.paymentconnector.domain.model.ClearingResult;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;

/** Publishes directly to keep the mock stateless; a real connector would go through its own outbox. */
@ApplicationScoped
public class KafkaPaymentResultPublisher implements PaymentResultPublisher {

  public static final String CHANNEL = "payment-results";

  private final MutinyEmitter<String> emitter;
  private final ObjectMapper json;

  public KafkaPaymentResultPublisher(@Channel(CHANNEL) MutinyEmitter<String> emitter, ObjectMapper json) {
    this.emitter = emitter;
    this.json = json;
  }

  @Override
  public void publish(ClearingResult result) {
    PaymentResultMessage message = new PaymentResultMessage(
        result.orderId(),
        result.transferId(),
        PaymentResultMessage.PaymentStatus.valueOf(result.outcome().name()),
        result.reasonCode(),
        result.reasonDetail(),
        result.processedAt());
    RecordHeaders headers = new RecordHeaders();
    headers.add(MessageHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(UTF_8));
    headers.add(MessageHeaders.EVENT_TYPE, PaymentResultMessage.TYPE.getBytes(UTF_8));
    OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
        .withKey(result.transferId().toString())
        .withHeaders(headers)
        .build();
    emitter.sendMessageAndAwait(Message.of(write(message)).addMetadata(metadata));
  }

  private String write(PaymentResultMessage message) {
    try {
      return json.writeValueAsString(message);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize payment result", e);
    }
  }
}
