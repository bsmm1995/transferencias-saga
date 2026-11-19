package ec.fin.corebanking.application.port.out;

import ec.fin.corebanking.domain.model.Account;
import java.util.Optional;

/** Driven port: storage of accounts. */
public interface AccountRepository {

  Optional<Account> find(String accountNumber);

  void save(Account account);
}
