package ec.fin.transfer.domain.model;

import ec.fin.commons.error.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.util.Objects;

/** Value object: a positive amount with at most two decimals in a supported currency. */
public record Money(BigDecimal amount, Currency currency) {

  public Money {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(currency, "currency");
    if (amount.signum() <= 0) {
      throw new BusinessRuleViolationException("INVALID_AMOUNT", "The amount must be greater than zero");
    }
    if (amount.stripTrailingZeros().scale() > 2) {
      throw new BusinessRuleViolationException("INVALID_AMOUNT", "The amount supports at most two decimals");
    }
    amount = amount.setScale(2);
  }

  public static Money of(BigDecimal amount, String currencyCode) {
    return new Money(amount, Currency.fromCode(currencyCode));
  }
}
