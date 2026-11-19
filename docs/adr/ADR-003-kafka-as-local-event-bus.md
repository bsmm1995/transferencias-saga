# ADR-003: Kafka as the local stand-in for EventBridge + SQS

Status: Accepted. Date: 2026-11-18.

## Context

The target design routes events through EventBridge and gives each consumer an SQS queue (FIFO for payment orders).
The exercise must run on a laptop with `docker compose`.

## Decision

Use Apache Kafka through SmallRye Reactive Messaging:

- One topic per message type (`payment-orders`, `payment-results`, `transfer-events`), one consumer group per service.
- Messages keyed by transfer id, so all messages of a transfer land in the same partition and keep their order (the
  equivalent of an SQS FIFO message group).
- `failure-strategy=dead-letter-queue` on every consumer (the equivalent of an SQS DLQ).
- Producers never talk to Kafka from a use case: the outbox relay and the publisher adapters are the only Kafka code.

## Alternatives

- LocalStack with EventBridge and SQS: closer to the target, heavier, and with partial emulation of FIFO semantics.
- RabbitMQ: fine for commands, weaker for replaying events.

## Consequences

Moving to EventBridge + SQS means replacing two adapters (`OutboxRelay` publishing, the `@Incoming` consumers). The
domain, the use cases and the message contracts stay the same.
