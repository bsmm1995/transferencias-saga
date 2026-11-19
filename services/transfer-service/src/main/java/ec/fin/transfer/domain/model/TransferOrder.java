package ec.fin.transfer.domain.model;

import java.util.Objects;

/** What the customer asked to move. Immutable once the transfer exists. */
public record TransferOrder(String customerId, String sourceAccount, Beneficiary beneficiary, Money money, String concept) {

  public TransferOrder {
    Objects.requireNonNull(customerId, "customerId");
    Objects.requireNonNull(sourceAccount, "sourceAccount");
    Objects.requireNonNull(beneficiary, "beneficiary");
    Objects.requireNonNull(money, "money");
  }
}
