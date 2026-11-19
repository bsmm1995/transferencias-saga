package ec.fin.corebanking.application.port.in;

import ec.fin.corebanking.domain.model.ReversalOutcome;
import java.util.UUID;

public interface ReverseLedgerOperationUseCase {

  ReversalOutcome reverse(UUID operationId, String reason);
}
