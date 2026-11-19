package ec.fin.transfer.domain.service;

import ec.fin.transfer.domain.model.Rail;
import ec.fin.transfer.domain.model.TransferOrder;
import java.util.Optional;

/**
 * Strategy: one interchangeable way of deciding the settlement rail.
 *
 * <p>Each implementation answers a single question ("does this order belong to MY rail?") and returns
 * {@link Optional#empty()} when it does not, so the {@link RailRouter} can ask the next rule. A rule may also
 * throw a business exception when the order is invalid for its rail.
 */
@FunctionalInterface
public interface RailRoutingRule {

  Optional<Rail> evaluate(TransferOrder order);
}
