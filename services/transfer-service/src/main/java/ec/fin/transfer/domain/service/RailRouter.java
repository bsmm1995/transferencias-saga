package ec.fin.transfer.domain.service;

import ec.fin.transfer.domain.model.Rail;
import ec.fin.transfer.domain.model.TransferOrder;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Strategy pattern, context role: picks the rail a transfer settles on by asking an ordered list of
 * {@link RailRoutingRule}s and keeping the first answer.
 *
 * <pre>
 *   InitiateTransferService ──route(order)──> RailRouter (context)
 *                                                 │ asks in order, first non-empty wins
 *                                                 ├─ OwnBankRule          → OWN_BANK  (or SAME_ACCOUNT error)
 *                                                 ├─ InstantNetworkRule   → INSTANT
 *                                                 └─ DeferredFallbackRule → DEFERRED  (always answers)
 * </pre>
 *
 * <p>Why: the router no longer knows how any rail is chosen. Adding a rail (e.g. a card network) means writing a
 * new rule and registering it in {@link #standard}, without touching the existing rules (Open/Closed). Order is
 * the business priority: the cheapest rail that can serve the beneficiary goes first.
 *
 * <p>Choosing a rail and executing it are separate concerns: how a rail settles is described by
 * {@link Rail#settlesInCore()}, and the outbox maps each external rail to its clearing network.
 */
public final class RailRouter {

  private final List<RailRoutingRule> rules;

  public RailRouter(List<RailRoutingRule> rules) {
    if (rules.isEmpty()) {
      throw new IllegalArgumentException("At least one rail routing rule is required");
    }
    this.rules = List.copyOf(rules);
  }

  public static RailRouter standard(String ownBankCode, Set<String> instantBankCodes) {
    return new RailRouter(List.of(
        new OwnBankRule(ownBankCode),
        new InstantNetworkRule(instantBankCodes),
        new DeferredFallbackRule()));
  }

  public Rail route(TransferOrder order) {
    return rules.stream()
        .map(rule -> rule.evaluate(order))
        .flatMap(Optional::stream)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "No rail routing rule matched bank " + order.beneficiary().bankCode() + "; register a fallback rule last"));
  }
}
