package ec.fin.transfer.domain.service;

import ec.fin.transfer.domain.model.Rail;
import ec.fin.transfer.domain.model.TransferOrder;
import java.util.Optional;

/** Concrete strategy and catch-all: every bank in the country is reachable through SPI batches, so it must go last. */
public record DeferredFallbackRule() implements RailRoutingRule {

  @Override
  public Optional<Rail> evaluate(TransferOrder order) {
    return Optional.of(Rail.DEFERRED);
  }
}
