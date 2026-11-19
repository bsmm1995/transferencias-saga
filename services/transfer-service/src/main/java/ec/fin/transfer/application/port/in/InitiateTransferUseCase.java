package ec.fin.transfer.application.port.in;

import ec.fin.transfer.domain.model.Beneficiary;
import ec.fin.transfer.domain.model.Money;
import ec.fin.transfer.domain.model.Transfer;
import ec.fin.transfer.domain.model.TransferOrder;
import java.math.BigDecimal;

/** Driving port: registers a transfer idempotently and starts its saga. */
public interface InitiateTransferUseCase {

  InitiationResult initiate(InitiateTransferCommand command);

  record InitiateTransferCommand(
      String idempotencyKey,
      String correlationId,
      String customerId,
      String sourceAccount,
      String beneficiaryBankCode,
      String beneficiaryAccount,
      String beneficiaryName,
      BigDecimal amount,
      String currency,
      String concept) {

    public TransferOrder toOrder() {
      return new TransferOrder(
          customerId,
          sourceAccount,
          new Beneficiary(beneficiaryBankCode, beneficiaryAccount, beneficiaryName),
          Money.of(amount, currency),
          concept);
    }
  }

  /** {@code replayed} is true when the idempotency key matched an earlier request. */
  record InitiationResult(Transfer transfer, boolean replayed) {
  }
}
