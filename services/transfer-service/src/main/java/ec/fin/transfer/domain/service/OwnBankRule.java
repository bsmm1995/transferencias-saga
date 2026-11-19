package ec.fin.transfer.domain.service;

import ec.fin.commons.error.BusinessRuleViolationException;
import ec.fin.transfer.domain.model.Rail;
import ec.fin.transfer.domain.model.TransferOrder;
import java.util.Objects;
import java.util.Optional;

/** Concrete strategy: the beneficiary is a customer of our own bank, so both legs are local postings. */
public record OwnBankRule(String ownBankCode) implements RailRoutingRule {

  public OwnBankRule {
    Objects.requireNonNull(ownBankCode, "ownBankCode");
  }

  @Override
  public Optional<Rail> evaluate(TransferOrder order) {
    if (!ownBankCode.equals(order.beneficiary().bankCode())) {
      return Optional.empty();
    }
    // Only an internal transfer can target the very same account; external rails would reject it downstream anyway.
    if (order.sourceAccount().equals(order.beneficiary().accountNumber())) {
      throw new BusinessRuleViolationException("SAME_ACCOUNT", "The source and beneficiary accounts must be different");
    }
    return Optional.of(Rail.OWN_BANK);
  }
}
