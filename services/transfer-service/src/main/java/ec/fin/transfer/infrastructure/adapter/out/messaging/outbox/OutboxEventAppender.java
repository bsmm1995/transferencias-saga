package ec.fin.transfer.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.fin.contracts.messaging.ClearingSystem;
import ec.fin.contracts.messaging.PaymentOrderMessage;
import ec.fin.contracts.messaging.Topics;
import ec.fin.contracts.messaging.TransferEventMessage;
import ec.fin.transfer.application.port.out.EventOutbox;
import ec.fin.transfer.domain.event.TransferEvent;
import ec.fin.transfer.domain.model.Rail;
import ec.fin.transfer.domain.model.TransferOrder;
import ec.fin.transfer.domain.model.TransferStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** Translates domain events into integration messages and stores them in the outbox table. */
@ApplicationScoped
@RequiredArgsConstructor
public class OutboxEventAppender implements EventOutbox {

  private final OutboxJpaRepository outbox;
  private final ObjectMapper json;

  @Override
  public void append(String correlationId, List<TransferEvent> events) {
    events.forEach(event -> outbox.persist(toOutboxMessage(event, correlationId)));
  }

  private OutboxMessageEntity toOutboxMessage(TransferEvent event, String correlationId) {
    UUID eventId = UUID.randomUUID();
    OutboxMessageEntity message = new OutboxMessageEntity();
    message.setEventId(eventId);
    message.setAggregateId(event.transferId());
    message.setCorrelationId(correlationId);
    message.setStatus(OutboxMessageEntity.Status.PENDING);
    message.setCreatedAt(event.occurredAt());

    if (event instanceof TransferEvent.PaymentOrderRequested order) {
      message.setTopic(Topics.PAYMENT_ORDERS);
      message.setEventType(PaymentOrderMessage.TYPE);
      message.setPayload(write(toPaymentOrder(order)));
      return message;
    }

    IntegrationEvent integration = switch (event) {
      case TransferEvent.Accepted accepted -> new IntegrationEvent("TransferAccepted", TransferStatus.PENDING, accepted.rail().name());
      case TransferEvent.Rejected rejected -> new IntegrationEvent("TransferRejected", TransferStatus.REJECTED, rejected.reason());
      case TransferEvent.FundsDebited debited -> new IntegrationEvent("TransferFundsDebited", TransferStatus.PENDING, null);
      case TransferEvent.Completed completed -> new IntegrationEvent("TransferCompleted", TransferStatus.COMPLETED, null);
      case TransferEvent.CompensationStarted started -> new IntegrationEvent("TransferCompensationStarted", TransferStatus.PENDING, started.reason());
      case TransferEvent.Reversed reversed -> new IntegrationEvent("TransferReversed", TransferStatus.REVERSED, reversed.reason());
      case TransferEvent.Failed failed -> new IntegrationEvent("TransferFailed", TransferStatus.FAILED, failed.reason());
      case TransferEvent.PaymentOrderRequested order -> throw new IllegalStateException("Handled above");
    };
    message.setTopic(Topics.TRANSFER_EVENTS);
    message.setEventType(integration.type());
    message.setPayload(write(new TransferEventMessage(
        eventId, integration.type(), event.transferId(), integration.status().name(), integration.detail(), event.occurredAt())));
    return message;
  }

  private static PaymentOrderMessage toPaymentOrder(TransferEvent.PaymentOrderRequested event) {
    TransferOrder order = event.order();
    return new PaymentOrderMessage(
        event.orderId(),
        event.transferId(),
        clearingSystemOf(event.rail()),
        order.sourceAccount(),
        order.beneficiary().bankCode(),
        order.beneficiary().accountNumber(),
        order.beneficiary().holderName(),
        order.money().amount(),
        order.money().currency().name(),
        order.concept(),
        event.occurredAt());
  }

  // Exhaustive on purpose: a new rail must not compile until someone decides which network clears it.
  private static ClearingSystem clearingSystemOf(Rail rail) {
    return switch (rail) {
      case INSTANT -> ClearingSystem.RPI;
      case DEFERRED -> ClearingSystem.SPI;
      case OWN_BANK -> throw new IllegalStateException("OWN_BANK transfers settle in the core, never through a clearing network");
    };
  }

  private String write(Object payload) {
    try {
      return json.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize " + payload.getClass().getSimpleName(), e);
    }
  }

  private record IntegrationEvent(String type, TransferStatus status, String detail) {
  }
}
