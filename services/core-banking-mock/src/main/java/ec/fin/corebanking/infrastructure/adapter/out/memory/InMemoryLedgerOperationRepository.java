package ec.fin.corebanking.infrastructure.adapter.out.memory;

import ec.fin.corebanking.application.port.out.LedgerOperationRepository;
import ec.fin.corebanking.domain.model.LedgerOperation;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Driven adapter: in-memory ledger operations. State resets on restart. */
@ApplicationScoped
public class InMemoryLedgerOperationRepository implements LedgerOperationRepository {

  private final Map<UUID, LedgerOperation> operations = new ConcurrentHashMap<>();

  @Override
  public Optional<LedgerOperation> find(UUID operationId) {
    return Optional.ofNullable(operations.get(operationId));
  }

  @Override
  public void save(LedgerOperation operation) {
    operations.put(operation.getId(), operation);
  }
}
