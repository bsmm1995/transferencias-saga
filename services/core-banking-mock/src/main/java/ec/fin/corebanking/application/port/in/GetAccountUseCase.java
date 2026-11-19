package ec.fin.corebanking.application.port.in;

import ec.fin.corebanking.domain.model.Account;

public interface GetAccountUseCase {

  Account get(String accountNumber);
}
