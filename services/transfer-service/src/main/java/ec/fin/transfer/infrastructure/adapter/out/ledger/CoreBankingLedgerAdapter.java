package ec.fin.transfer.infrastructure.adapter.out.ledger;

import ec.fin.commons.text.Texts;
import ec.fin.transfer.application.port.out.LedgerPort;
import ec.fin.transfer.domain.model.Money;
import ec.fin.transfer.domain.saga.SagaCommand;
import ec.fin.transfer.infrastructure.adapter.out.ledger.client.api.model.LedgerOperationRequest;
import ec.fin.transfer.infrastructure.adapter.out.ledger.client.api.model.OperationType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Adaptador Ledger of the L3 diagram: translates saga commands into core banking postings. */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CoreBankingLedgerAdapter implements LedgerPort {

  private static final int MAX_REASON_LENGTH = 200;

  private final CoreBankingGateway gateway;

  @Override
  public LedgerResult debit(SagaCommand.Debit command) {
    return post(command.operationId(), OperationType.DEBIT, command.account(), command.money(), command.reference());
  }

  @Override
  public LedgerResult credit(SagaCommand.Credit command) {
    return post(command.operationId(), OperationType.CREDIT, command.account(), command.money(), command.reference());
  }

  @Override
  public LedgerResult reverse(UUID operationId, String reason) {
    return call(() -> gateway.reverse(operationId, Texts.truncate(reason == null ? "Saga compensation" : reason, MAX_REASON_LENGTH)));
  }

  private LedgerResult post(UUID operationId, OperationType type, String account, Money money, String reference) {
    LedgerOperationRequest request = new LedgerOperationRequest()
        .operationId(operationId)
        .type(type)
        .accountNumber(account)
        .amount(money.amount())
        .currency(LedgerOperationRequest.CurrencyEnum.fromValue(money.currency().name()))
        .reference(reference);
    return call(() -> gateway.post(request));
  }

  private static LedgerResult call(Runnable remoteCall) {
    try {
      remoteCall.run();
      return new LedgerResult.Applied();
    } catch (LedgerRejectedException e) {
      return new LedgerResult.Rejected(e.getCode(), e.getDetail());
    } catch (RuntimeException e) {
      log.warn("Core banking unavailable: {}", e.toString());
      return new LedgerResult.Unavailable(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }
}
