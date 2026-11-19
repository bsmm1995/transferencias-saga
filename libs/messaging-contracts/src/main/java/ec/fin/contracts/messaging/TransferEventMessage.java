package ec.fin.contracts.messaging;

import java.time.Instant;
import java.util.UUID;

/** Integration event published for any interested consumer (notifications, audit, analytics). */
public record TransferEventMessage(
    UUID eventId,
    String type,
    UUID transferId,
    String status,
    String detail,
    Instant occurredAt) {
}
