# ADR-006: Rail routing as an ordered chain of strategies

Status: Accepted. Date: 2026-11-18.

## Context

Choosing the settlement rail (own bank, instant RPI, deferred SPI) started as one `if/else` in `RailRouter`, and the
consequences of the choice were scattered as `rail == Rail.X` comparisons in the aggregate and in the outbox. Every
new rail (cards, a second instant network, cross-border) would edit all of those places and risk a silent default,
e.g. an unknown rail falling through to SPI.

## Decision

- **Strategy pattern.** `RailRoutingRule` is the strategy; `OwnBankRule`, `InstantNetworkRule` and
  `DeferredFallbackRule` are the concrete strategies; `RailRouter` is the context.
- **First match wins.** The router asks the rules in order and keeps the first non-empty answer. The order is the
  business priority (cheapest rail that can serve the beneficiary first) and is assembled in one place,
  `RailRouter.standard(...)`. A rule may throw a business exception (e.g. `SAME_ACCOUNT`).
- **A fallback is mandatory.** `DeferredFallbackRule` always answers and goes last; a router without a matching rule
  fails loudly instead of returning `null`.
- **Choosing is not executing.** How a rail settles is a property of the rail (`Rail.settlesInCore()`), and the
  outbox maps each external rail to its clearing network with an exhaustive `switch`, so a new rail does not compile
  until someone decides how it clears.

## Consequences

Adding a rail is: a new `Rail` constant, a new rule registered in `RailRouter.standard(...)`, and the compiler pointing
at every `switch` that must handle it. Existing rules are not touched (Open/Closed) and each one is unit-testable on
its own. The cost is four small classes instead of one method, acceptable because rail selection is a business rule
expected to grow.
