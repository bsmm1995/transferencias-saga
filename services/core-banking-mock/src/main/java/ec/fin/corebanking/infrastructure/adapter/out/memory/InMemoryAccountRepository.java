package ec.fin.corebanking.infrastructure.adapter.out.memory;

import ec.fin.corebanking.application.port.out.AccountRepository;
import ec.fin.corebanking.domain.model.Account;
import ec.fin.corebanking.domain.model.AccountStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** In-memory accounts seeded with the scenarios described in the README. State resets on restart. */
@ApplicationScoped
public class InMemoryAccountRepository implements AccountRepository {

  private final Map<String, Account> accounts = new ConcurrentHashMap<>();

  public InMemoryAccountRepository() {
    Stream.of(
            new Account("1000000001", "Ana Torres", "USD", AccountStatus.ACTIVE, new BigDecimal("5000.00")),
            new Account("1000000002", "Luis Paredes", "USD", AccountStatus.ACTIVE, new BigDecimal("20.00")),
            new Account("1000000003", "Marta Rios", "USD", AccountStatus.BLOCKED, new BigDecimal("1000.00")),
            new Account("2000000001", "Carlos Vega", "USD", AccountStatus.ACTIVE, new BigDecimal("100.00")),
            new Account("2000000002", "Sofia Leon", "USD", AccountStatus.CLOSED, BigDecimal.ZERO))
        .forEach(this::save);
  }

  @Override
  public Optional<Account> find(String accountNumber) {
    return Optional.ofNullable(accounts.get(accountNumber));
  }

  @Override
  public void save(Account account) {
    accounts.put(account.getNumber(), account);
  }
}
