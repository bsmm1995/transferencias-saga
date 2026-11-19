package ec.fin.corebanking.application.port.in;

import ec.fin.corebanking.domain.model.LedgerOperation;
import ec.fin.corebanking.domain.model.OperationType;
import java.math.BigDecimal;
import java.util.UUID;

public interface PostLedgerOperationUseCase {

  /** Idempotent by operation id: a replay returns the original operation with {@code replayed = true}. */
  PostingResult post(PostingCommand command);

  record PostingCommand(UUID operationId, OperationType type, String accountNumber, BigDecimal amount,
      String currency, String reference) {
  }

  record PostingResult(LedgerOperation operation, boolean replayed) {
  }
}
