package ec.fin.paymentconnector.infrastructure.adapter.out.memory;

import ec.fin.paymentconnector.application.port.out.ProcessedOrderRegistry;
import ec.fin.paymentconnector.domain.model.ClearingResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Driven adapter: in-memory registry of cleared orders, used for deduplication. */
@ApplicationScoped
public class InMemoryProcessedOrderRegistry implements ProcessedOrderRegistry {

  private final Map<UUID, ClearingResult> results = new ConcurrentHashMap<>();

  @Override
  public Optional<ClearingResult> find(UUID orderId) {
    return Optional.ofNullable(results.get(orderId));
  }

  @Override
  public void remember(ClearingResult result) {
    results.putIfAbsent(result.orderId(), result);
  }
}
