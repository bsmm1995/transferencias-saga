package ec.fin.transfer.domain.model;

import ec.fin.commons.error.BusinessRuleViolationException;
import java.util.Arrays;

/** Supported currencies. Ecuador operates in US dollars. */
public enum Currency {
  USD;

  public static Currency fromCode(String code) {
    return Arrays.stream(values())
        .filter(currency -> currency.name().equals(code))
        .findFirst()
        .orElseThrow(() -> new BusinessRuleViolationException("UNSUPPORTED_CURRENCY", "Currency '%s' is not supported".formatted(code)));
  }
}
