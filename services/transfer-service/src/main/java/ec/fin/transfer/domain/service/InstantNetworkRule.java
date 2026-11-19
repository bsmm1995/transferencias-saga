package ec.fin.transfer.domain.service;

import ec.fin.transfer.domain.model.Rail;
import ec.fin.transfer.domain.model.TransferOrder;
import java.util.Optional;
import java.util.Set;

/** Concrete strategy: the beneficiary bank is enrolled in the central bank instant payments network. */
public record InstantNetworkRule(Set<String> instantBankCodes) implements RailRoutingRule {

  public InstantNetworkRule {
    instantBankCodes = Set.copyOf(instantBankCodes);
  }

  @Override
  public Optional<Rail> evaluate(TransferOrder order) {
    return instantBankCodes.contains(order.beneficiary().bankCode()) ? Optional.of(Rail.INSTANT) : Optional.empty();
  }
}
