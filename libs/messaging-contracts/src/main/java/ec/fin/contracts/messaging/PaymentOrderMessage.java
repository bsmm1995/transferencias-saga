package ec.fin.contracts.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Command sent to the payment connector. {@code orderId} is stable across re-sends so the connector can deduplicate. */
public record PaymentOrderMessage(
    UUID orderId,
    UUID transferId,
    ClearingSystem clearingSystem,
    String debtorAccount,
    String creditorBankCode,
    String creditorAccount,
    String creditorName,
    BigDecimal amount,
    String currency,
    String remittanceInformation,
    Instant requestedAt) {

  public static final String TYPE = "PaymentOrderRequested";
}
