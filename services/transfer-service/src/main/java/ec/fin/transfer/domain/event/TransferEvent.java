package ec.fin.transfer.domain.event;

import ec.fin.transfer.domain.model.Rail;
import ec.fin.transfer.domain.model.TransferOrder;
import java.time.Instant;
import java.util.UUID;

/** Facts raised by the transfer aggregate. They are stored in the outbox in the same transaction as the state change. */
public sealed interface TransferEvent {

  UUID transferId();

  Instant occurredAt();

  record Accepted(UUID transferId, Rail rail, Instant occurredAt) implements TransferEvent {
  }

  record Rejected(UUID transferId, String reason, Instant occurredAt) implements TransferEvent {
  }

  record FundsDebited(UUID transferId, Instant occurredAt) implements TransferEvent {
  }

  /** Command for the payment connector, emitted as an event so it travels through the outbox. */
  record PaymentOrderRequested(UUID transferId, UUID orderId, Rail rail, TransferOrder order, Instant occurredAt)
      implements TransferEvent {
  }

  record Completed(UUID transferId, Instant occurredAt) implements TransferEvent {
  }

  record CompensationStarted(UUID transferId, String reason, Instant occurredAt) implements TransferEvent {
  }

  record Reversed(UUID transferId, String reason, Instant occurredAt) implements TransferEvent {
  }

  record Failed(UUID transferId, String reason, Instant occurredAt) implements TransferEvent {
  }
}
