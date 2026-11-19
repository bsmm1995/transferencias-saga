package ec.fin.paymentconnector.application.port.out;

import ec.fin.paymentconnector.domain.model.ClearingResult;

/** Driven port: reports the clearing result back to the transfer service. */
public interface PaymentResultPublisher {

  void publish(ClearingResult result);
}
