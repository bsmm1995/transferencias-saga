package ec.fin.contracts.messaging;

import java.time.Instant;
import java.util.UUID;

/** Reply from the payment connector. Reason codes follow ISO 20022 (e.g. AC01 incorrect account, AC04 closed account). */
public record PaymentResultMessage(
    UUID orderId,
    UUID transferId,
    PaymentStatus status,
    String reasonCode,
    String reasonDetail,
    Instant processedAt) {

  public static final String TYPE = "PaymentResultReported";

  public enum PaymentStatus {
    SETTLED,
    REJECTED
  }
}
