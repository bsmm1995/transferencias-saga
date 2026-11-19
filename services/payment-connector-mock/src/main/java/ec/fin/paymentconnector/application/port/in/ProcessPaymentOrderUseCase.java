package ec.fin.paymentconnector.application.port.in;

import ec.fin.paymentconnector.domain.model.PaymentOrder;

/** Driving port: clears a payment order through the central bank. */
public interface ProcessPaymentOrderUseCase {

  /** Idempotent by order id: a re-sent order republishes the original result instead of paying twice. */
  void process(PaymentOrder order);
}
