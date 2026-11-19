# ADR-002: Quarkus with Hibernate ORM on virtual threads

Status: Accepted. Date: 2026-11-18.

## Context

The platform proposal uses Spring Boot WebFlux with R2DBC for domain services. This exercise is about Quarkus, and
the saga relies on short JTA transactions that combine the aggregate, the journal, the idempotency key and the outbox.

## Decision

Quarkus 3.40 with Quarkus REST, Hibernate ORM with Panache repositories, and blocking code that runs on virtual
threads (`@RunOnVirtualThread` on endpoints, consumers and scheduled jobs). Java 25 no longer pins virtual threads on
`synchronized` (JEP 491), which removes the main caveat of using JDBC on them.

## Alternatives

- Hibernate Reactive / reactive SQL clients: comparable throughput with harder transaction composition and debugging.
- Spring Boot: aligned with the target platform but not with the goal of this exercise.

## Consequences

The code reads top to bottom and transactions are explicit through the `UnitOfWork` port. Scalability comes from
virtual threads rather than an event loop. The application layer does not change if the persistence adapter is
replaced by a reactive one.
