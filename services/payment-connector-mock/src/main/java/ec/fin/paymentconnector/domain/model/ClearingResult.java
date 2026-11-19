package ec.fin.paymentconnector.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Outcome of clearing a payment order, with an ISO 20022 reason code when rejected. */
public record ClearingResult(UUID orderId, UUID transferId, Outcome outcome, String reasonCode, String reasonDetail, Instant processedAt) {

  public static ClearingResult settled(PaymentOrder order, Instant now) {
    return new ClearingResult(order.orderId(), order.transferId(), Outcome.SETTLED, null, null, now);
  }

  public static ClearingResult rejected(PaymentOrder order, String reasonCode, String reasonDetail, Instant now) {
    return new ClearingResult(order.orderId(), order.transferId(), Outcome.REJECTED, reasonCode, reasonDetail, now);
  }

  public enum Outcome {
    SETTLED,
    REJECTED
  }
}
