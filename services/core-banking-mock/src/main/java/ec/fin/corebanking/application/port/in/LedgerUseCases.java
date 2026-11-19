package ec.fin.corebanking.application.port.in;

import ec.fin.corebanking.domain.model.Account;
import ec.fin.corebanking.domain.model.LedgerOperation;
import ec.fin.corebanking.domain.model.OperationType;
import ec.fin.corebanking.domain.model.ReversalOutcome;
import java.math.BigDecimal;
import java.util.UUID;

/** Inbound ports of the core mock, grouped because each one is a single method. */
public final class LedgerUseCases {

  private LedgerUseCases() {
  }

  public interface PostLedgerOperationUseCase {

    /** Idempotent by operation id: a replay returns the original operation with {@code replayed = true}. */
    PostingResult post(PostingCommand command);
  }

  public interface ReverseLedgerOperationUseCase {

    ReversalOutcome reverse(UUID operationId, String reason);
  }

  public interface GetAccountUseCase {

    Account get(String accountNumber);
  }

  public record PostingCommand(UUID operationId, OperationType type, String accountNumber, BigDecimal amount,
      String currency, String reference) {
  }

  public record PostingResult(LedgerOperation operation, boolean replayed) {
  }
}
