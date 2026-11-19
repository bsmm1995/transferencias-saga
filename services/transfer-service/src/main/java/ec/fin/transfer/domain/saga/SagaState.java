package ec.fin.transfer.domain.saga;

import ec.fin.transfer.domain.model.TransferStatus;
import java.util.Arrays;
import java.util.List;

/** States of the transfer saga. Each one maps to the status the customer sees; terminal states end the saga. */
public enum SagaState {
  DEBIT_PENDING(TransferStatus.PENDING),
  CREDIT_PENDING(TransferStatus.PENDING),
  AWAITING_PAYMENT_RESULT(TransferStatus.PENDING),
  COMPENSATING(TransferStatus.PENDING),
  COMPLETED(TransferStatus.COMPLETED),
  REJECTED(TransferStatus.REJECTED),
  COMPENSATED(TransferStatus.REVERSED),
  FAILED(TransferStatus.FAILED);

  private final TransferStatus status;

  SagaState(TransferStatus status) {
    this.status = status;
  }

  public TransferStatus status() {
    return status;
  }

  public boolean isTerminal() {
    return status != TransferStatus.PENDING;
  }

  public static List<SagaState> active() {
    return Arrays.stream(values()).filter(state -> !state.isTerminal()).toList();
  }
}
