package ec.fin.corebanking.application.port.out;

import ec.fin.corebanking.domain.model.LedgerOperation;
import java.util.Optional;
import java.util.UUID;

/** Driven port: storage of ledger operations, keyed by the operation id chosen by the caller. */
public interface LedgerOperationRepository {

  Optional<LedgerOperation> find(UUID operationId);

  void save(LedgerOperation operation);
}
