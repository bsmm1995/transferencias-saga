# ADR-001: Orchestrated saga as a persisted state machine

Status: Accepted. Date: 2026-11-18.

## Context

A transfer spans the core ledger, the fraud engine and, for interbank payments, the central bank. None of them share
a transaction. Participants can refuse, fail, time out, or apply an operation and then fail to answer.

## Decision

- The transfer service orchestrates the saga. The state machine lives in the `Transfer` aggregate (plain Java); the
  `SagaOrchestrator` executes the command the aggregate asks for and feeds the outcome back.
- Each step is two short local transactions around a remote call made with no transaction open.
- Participant operations use ids derived from the transfer id, so every retry is idempotent.
- Retries are persisted (`attempts`, `next_attempt_at`) with exponential backoff; a scheduler resumes overdue sagas.
- Exhausted forward steps are compensated; exhausted compensations and unanswered interbank payments end in `FAILED`
  for manual reconciliation.
- State changes and their events are written atomically through a transactional outbox.

## Alternatives

- Choreography: no single place knows the state of a transfer, and the compensation order becomes implicit.
- A workflow engine (Temporal, Camunda): powerful, but it hides the mechanics this exercise is meant to practise and
  adds infrastructure.
- In-memory retries: lost on restart and invisible in the journal.

## Consequences

Positive: every transition is unit-testable without infrastructure, the journal explains every transfer, and crashes
or duplicate workers cannot apply an operation twice.

Negative: participants must offer idempotent operations and tolerant reversals, and a polling scheduler adds up to one
poll interval of latency to retries.
