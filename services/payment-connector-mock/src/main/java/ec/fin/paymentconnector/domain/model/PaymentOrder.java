package ec.fin.paymentconnector.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Interbank payment order to clear. {@code orderId} is stable across re-sends. */
public record PaymentOrder(
    UUID orderId,
    UUID transferId,
    ClearingNetwork network,
    String debtorAccount,
    String creditorBankCode,
    String creditorAccount,
    String creditorName,
    BigDecimal amount,
    String currency) {

  public PaymentOrder {
    Objects.requireNonNull(orderId, "orderId");
    Objects.requireNonNull(transferId, "transferId");
    Objects.requireNonNull(network, "network");
  }

  public enum ClearingNetwork {
    /** Red de Pagos Instantaneos. */
    RPI,
    /** Sistema de Pagos Interbancarios, deferred batches. */
    SPI
  }
}
