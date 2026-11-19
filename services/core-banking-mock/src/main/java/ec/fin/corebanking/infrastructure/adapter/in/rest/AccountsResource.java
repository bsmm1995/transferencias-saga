package ec.fin.corebanking.infrastructure.adapter.in.rest;

import ec.fin.corebanking.application.port.in.LedgerUseCases.GetAccountUseCase;
import ec.fin.corebanking.domain.model.Account;
import ec.fin.corebanking.infrastructure.adapter.in.rest.api.AccountsApi;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

/** Driving adapter: implements the generated AccountsApi to expose balances. */
@RequiredArgsConstructor
public class AccountsResource implements AccountsApi {

  private final GetAccountUseCase getAccount;

  @Override
  public Response getAccount(String accountNumber) {
    Account account = getAccount.get(accountNumber);
    return Response.ok(new ec.fin.corebanking.infrastructure.adapter.in.rest.api.model.Account()
            .accountNumber(account.getNumber())
            .holderName(account.getHolderName())
            .balance(account.getBalance())
            .currency(account.getCurrency())
            .status(ec.fin.corebanking.infrastructure.adapter.in.rest.api.model.Account.StatusEnum.fromValue(account.getStatus().name())))
        .build();
  }
}
