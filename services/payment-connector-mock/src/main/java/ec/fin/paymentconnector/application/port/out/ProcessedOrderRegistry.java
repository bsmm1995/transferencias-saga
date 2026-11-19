package ec.fin.paymentconnector.application.port.out;

import ec.fin.paymentconnector.domain.model.ClearingResult;
import java.util.Optional;
import java.util.UUID;

/** Idempotent receiver: remembers the result of every order already cleared. */
public interface ProcessedOrderRegistry {

  Optional<ClearingResult> find(UUID orderId);

  void remember(ClearingResult result);
}
