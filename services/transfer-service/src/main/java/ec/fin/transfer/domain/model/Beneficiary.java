package ec.fin.transfer.domain.model;

import java.util.Objects;

/** Value object: who receives the money and at which bank. */
public record Beneficiary(String bankCode, String accountNumber, String holderName) {

  public Beneficiary {
    Objects.requireNonNull(bankCode, "bankCode");
    Objects.requireNonNull(accountNumber, "accountNumber");
    Objects.requireNonNull(holderName, "holderName");
  }
}
