package ec.fin.corebanking.domain.model;

import ec.fin.commons.error.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.Getter;

/** Aggregate: a customer account. Debits and credits enforce the business rules; reversals bypass them on purpose. */
@Getter
public final class Account {

  private final String number;
  private final String holderName;
  private final String currency;
  private final AccountStatus status;
  private BigDecimal balance;

  public Account(String number, String holderName, String currency, AccountStatus status, BigDecimal balance) {
    this.number = Objects.requireNonNull(number, "number");
    this.holderName = Objects.requireNonNull(holderName, "holderName");
    this.currency = Objects.requireNonNull(currency, "currency");
    this.status = Objects.requireNonNull(status, "status");
    this.balance = balance.setScale(2);
  }

  public void debit(BigDecimal amount) {
    requireOperational();
    if (balance.compareTo(amount) < 0) {
      throw new BusinessRuleViolationException("INSUFFICIENT_FUNDS", "Account %s has insufficient funds".formatted(number));
    }
    balance = balance.subtract(amount);
  }

  public void credit(BigDecimal amount) {
    if (status == AccountStatus.CLOSED) {
      throw new BusinessRuleViolationException("ACCOUNT_CLOSED", "Account %s is closed".formatted(number));
    }
    balance = balance.add(amount);
  }

  /** Reversals bypass business checks: a compensation must always be applicable, even if the balance goes negative. */
  public void forceAdjust(BigDecimal delta) {
    balance = balance.add(delta);
  }

  private void requireOperational() {
    switch (status) {
      case BLOCKED -> throw new BusinessRuleViolationException("ACCOUNT_BLOCKED", "Account %s is blocked".formatted(number));
      case CLOSED -> throw new BusinessRuleViolationException("ACCOUNT_CLOSED", "Account %s is closed".formatted(number));
      case ACTIVE -> {
        // operational
      }
    }
  }
}
