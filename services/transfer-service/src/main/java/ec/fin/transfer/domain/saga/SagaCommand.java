package ec.fin.transfer.domain.saga;

import ec.fin.transfer.domain.model.Money;
import java.util.List;
import java.util.UUID;

/**
 * The next thing the orchestrator must do for a transfer. The domain decides, the application executes it
 * against the participants and feeds the outcome back. Operation ids are deterministic, which is what makes
 * every retry idempotent on the participant side.
 */
public sealed interface SagaCommand {

  record Debit(UUID operationId, String account, Money money, String reference) implements SagaCommand {
  }

  record Credit(UUID operationId, String account, Money money, String reference) implements SagaCommand {
  }

  /** Reverses the given ledger operations in order. Reversing an operation that never happened is a no-op. */
  record Compensate(List<UUID> operationIds, String reason) implements SagaCommand {
  }

  /** The clearing network did not answer in time: either ask again or give up. */
  record ResolvePaymentTimeout(UUID orderId) implements SagaCommand {
  }
}
