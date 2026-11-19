package ec.fin.paymentconnector.application.port.out;

import ec.fin.paymentconnector.domain.model.ClearingResult;
import ec.fin.paymentconnector.domain.model.PaymentOrder;
import java.util.Optional;

/** Driven port: the central bank clearing network (RPI or SPI). */
public interface ClearingNetworkPort {

  /** Empty when the network never answers, which forces the saga into its timeout path. */
  Optional<ClearingResult> submit(PaymentOrder order);
}
