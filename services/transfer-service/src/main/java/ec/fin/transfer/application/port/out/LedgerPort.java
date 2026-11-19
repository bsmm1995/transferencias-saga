package ec.fin.transfer.application.port.out;

import ec.fin.transfer.domain.saga.SagaCommand;
import java.util.UUID;

/** Core banking postings. Implementations never throw: every outcome is a {@link LedgerResult}. */
public interface LedgerPort {

  LedgerResult debit(SagaCommand.Debit command);

  LedgerResult credit(SagaCommand.Credit command);

  /** Applied also covers "already reversed" and "nothing to reverse". */
  LedgerResult reverse(UUID operationId, String reason);

  sealed interface LedgerResult {

    record Applied() implements LedgerResult {
    }

    /** Business refusal (insufficient funds, blocked account...). Retrying will not help. */
    record Rejected(String code, String detail) implements LedgerResult {

      public String describe() {
        return detail == null ? code : code + " - " + detail;
      }
    }

    /** No usable answer (timeout, 5xx, open circuit). The operation may or may not have been applied. */
    record Unavailable(String detail) implements LedgerResult {
    }
  }
}
