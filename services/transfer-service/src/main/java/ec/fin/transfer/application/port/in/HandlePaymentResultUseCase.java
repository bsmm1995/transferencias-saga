package ec.fin.transfer.application.port.in;

import java.util.UUID;

/** Driving port: applies the clearing result reported by the payment connector. */
public interface HandlePaymentResultUseCase {

  void handle(PaymentResult result);

  record PaymentResult(UUID orderId, UUID transferId, boolean settled, String reasonCode, String reasonDetail) {

    public String describe() {
      return reasonDetail == null ? reasonCode : reasonCode + " - " + reasonDetail;
    }
  }
}
