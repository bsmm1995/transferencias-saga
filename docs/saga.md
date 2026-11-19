# Saga walkthrough

A transfer touches the core ledger and, for interbank payments, the central bank. There is no transaction that spans
both, so the transfer service runs an **orchestrated saga**: it records every step, decides the next one, and when a
forward step cannot be completed it **compensates** the steps already applied.

## 1. State machine

The whole saga is one aggregate, [`Transfer`](../services/transfer-service/src/main/java/ec/fin/transfer/domain/model/Transfer.java),
written in plain Java and tested without containers ([`TransferSagaTest`](../services/transfer-service/src/test/java/ec/fin/transfer/domain/model/TransferSagaTest.java)).

```mermaid
stateDiagram-v2
    [*] --> REJECTED: fraud engine denies
    [*] --> DEBIT_PENDING: fraud engine approves

    DEBIT_PENDING --> CREDIT_PENDING: debit applied (OWN_BANK)
    DEBIT_PENDING --> AWAITING_PAYMENT_RESULT: debit applied (INSTANT or DEFERRED), emits PaymentOrderRequested
    DEBIT_PENDING --> REJECTED: debit refused (insufficient funds, blocked...)
    DEBIT_PENDING --> DEBIT_PENDING: no answer → retry with backoff
    DEBIT_PENDING --> COMPENSATING: retries exhausted (outcome unknown)

    CREDIT_PENDING --> COMPLETED: credit applied
    CREDIT_PENDING --> COMPENSATING: credit refused / retries exhausted
    CREDIT_PENDING --> CREDIT_PENDING: no answer → retry with backoff

    AWAITING_PAYMENT_RESULT --> COMPLETED: SETTLED
    AWAITING_PAYMENT_RESULT --> COMPENSATING: REJECTED (AC01, AC04...)
    AWAITING_PAYMENT_RESULT --> AWAITING_PAYMENT_RESULT: timeout → re-send same order id
    AWAITING_PAYMENT_RESULT --> FAILED: no answer after N inquiries

    COMPENSATING --> COMPENSATED: reversals applied
    COMPENSATING --> COMPENSATING: no answer → retry with backoff
    COMPENSATING --> FAILED: retries exhausted

    COMPLETED --> [*]
    REJECTED --> [*]
    COMPENSATED --> [*]
    FAILED --> [*]
```

| Saga state | Customer status | Next command (`Transfer.nextCommand()`) |
|---|---|---|
| `DEBIT_PENDING` | `PENDING` | `Debit(debitOperationId)` |
| `CREDIT_PENDING` | `PENDING` | `Credit(creditOperationId)` |
| `AWAITING_PAYMENT_RESULT` | `PENDING` | `ResolvePaymentTimeout(paymentOrderId)` when the deadline passes |
| `COMPENSATING` | `PENDING` | `Compensate([creditOperationId,] debitOperationId)` |
| `COMPLETED` | `COMPLETED` | — |
| `REJECTED` | `REJECTED` | — |
| `COMPENSATED` | `REVERSED` | — |
| `FAILED` | `FAILED` | — (manual reconciliation) |

## 2. How a step runs

```mermaid
sequenceDiagram
    autonumber
    participant W as Worker (dispatcher or scheduler)
    participant O as SagaOrchestrator
    participant DB as PostgreSQL
    participant P as Participant (core)

    W->>O: advance(transferId)
    O->>DB: tx 1: load transfer (version v)
    O->>O: command = transfer.nextCommand()
    O->>P: execute command (no transaction open)
    P-->>O: Applied | Rejected | Unavailable
    O->>DB: tx 2: reload, check version == v,<br/>apply transition, insert journal + outbox rows
    Note over O,DB: if another worker moved the saga, tx 2 is skipped<br/>(version check or optimistic lock)
    O->>O: loop while the next step is already due
```

Why it is safe:

- **No remote call inside a transaction.** Connections and row locks are never held while waiting on the network.
- **Every participant call is idempotent.** Operation ids are derived from the transfer id
  (`UUID.nameUUIDFromBytes(transferId + ":debit")`), so a retry, a duplicate worker or a crash between the call and
  tx 2 repeats the same operation, and the core answers with the original result.
- **Lost updates are impossible.** tx 2 checks the version it read in tx 1 and Hibernate's `@Version` guards the
  update itself; the loser simply stops.
- **Nothing gets stuck.** The API dispatches the saga on a virtual thread right after commit, and a scheduler
  (`SagaJobs.advanceDueSagas`, every second) picks up anything whose `next_attempt_at` has passed: retries, timeouts
  or dispatches lost in a crash.

## 3. Paths

### Own bank transfer (happy path)

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant API as TransferResource
    participant UC as InitiateTransferService
    participant F as fraud-mock
    participant O as SagaOrchestrator
    participant CB as core-banking-mock
    participant DB as PostgreSQL

    C->>API: POST /transfers (Idempotency-Key)
    API->>UC: initiate(command)
    UC->>DB: idempotency key known?
    UC->>F: evaluate risk
    F-->>UC: APPROVE (score)
    UC->>DB: tx: insert transfer (DEBIT_PENDING) + key + outbox(TransferAccepted)
    UC-->>API: transfer
    API-->>C: 202 Accepted, Location
    UC->>O: dispatch (virtual thread)
    O->>CB: POST ledger operation DEBIT (op = debitId)
    CB-->>O: 201 APPLIED
    O->>DB: tx: CREDIT_PENDING + outbox(FundsDebited)
    O->>CB: POST ledger operation CREDIT (op = creditId)
    CB-->>O: 201 APPLIED
    O->>DB: tx: COMPLETED + outbox(TransferCompleted)
```

### Interbank transfer (instant or deferred)

```mermaid
sequenceDiagram
    autonumber
    participant O as SagaOrchestrator
    participant CB as core-banking-mock
    participant DB as PostgreSQL
    participant R as OutboxRelay
    participant K as Kafka
    participant PC as payment-connector-mock
    participant RC as PaymentResultConsumer

    O->>CB: DEBIT
    CB-->>O: APPLIED
    O->>DB: tx: AWAITING_PAYMENT_RESULT (deadline = now + 15s)<br/>+ outbox(PaymentOrderRequested, FundsDebited)
    loop every 500 ms
        R->>DB: pg_try_advisory_xact_lock (one relay at a time)<br/>select pending ... for update skip locked
        R->>K: publish payment-orders (key = transferId)
        R->>DB: mark published
    end
    K->>PC: payment order
    PC->>PC: dedupe by orderId, simulate RPI (300 ms) or SPI (3 s)
    PC->>K: payment-results SETTLED
    K->>RC: result
    RC->>O: handle(result)
    O->>DB: tx: COMPLETED + outbox(TransferCompleted)
```

### Compensation

```mermaid
sequenceDiagram
    autonumber
    participant O as SagaOrchestrator
    participant CB as core-banking-mock
    participant DB as PostgreSQL

    O->>CB: CREDIT to a closed account
    CB-->>O: 422 ACCOUNT_CLOSED
    O->>DB: tx: COMPENSATING + outbox(TransferCompensationStarted)
    O->>CB: reversal of creditId
    CB-->>O: NOT_FOUND (credit never applied: fine)
    O->>CB: reversal of debitId
    CB-->>O: REVERSED
    O->>DB: tx: COMPENSATED + outbox(TransferReversed)
```

Compensations run in reverse order and treat `NOT_FOUND` and `ALREADY_REVERSED` as success, so the full list can always
be replayed after a crash or a timeout.

### Unknown outcome

The worst case in a saga is not a refusal, it is **no answer**: the core may or may not have applied the debit.

```mermaid
sequenceDiagram
    autonumber
    participant O as SagaOrchestrator
    participant CB as core-banking-mock (chaos AFTER_APPLY)

    O->>CB: DEBIT (op = debitId)
    CB->>CB: applies the debit
    CB-->>O: 503
    Note over O: attempt 1 failed → retry in 1 s
    O->>CB: DEBIT (same op = debitId)
    CB-->>O: 200 replay of the original operation
    Note over O: continues as if the first call had succeeded
```

If the retries run out (`max-attempts`), a forward step is **compensated defensively**: reversing a debit that never
happened is a no-op, reversing one that did happen returns the money. If even the compensation cannot be completed,
the transfer ends in `FAILED` for manual reconciliation.

### Lost payment order: why timeouts do not compensate

When the clearing network does not answer, the money may already be at the other bank. Reversing the debit could
pay the customer twice. So the saga **re-sends the same order id** (the connector deduplicates, which turns the
re-send into a status inquiry) and after `max-payment-inquiries` it stops in `FAILED` with the reason
"Payment outcome unknown; manual reconciliation required".

## 4. Design decisions

| Decision | Alternative | Why |
|---|---|---|
| Orchestration | Choreography | The flow has branches per rail and compensations in a specific order; one owner that knows the whole state is easier to reason about, test and operate. |
| State machine inside the aggregate | Separate saga engine or framework | The transitions are business rules; keeping them in plain Java makes them testable in milliseconds and portable. |
| Saga status derived, not stored | Separate `status` column | Two columns can disagree; one cannot. |
| Synchronous core calls, asynchronous clearing | Everything asynchronous | Matches the L3 diagram: the core answers in milliseconds through the ACL; the BCE answers in seconds or in batches. |
| Persisted retries with backoff | In-memory retries (`@Retry`) | Retries survive restarts and are visible in the journal. The circuit breaker only fails fast. |
| Deterministic operation ids | Random ids stored per step | No extra state and every retry is idempotent by construction. |
| Tombstone on reversal of an unknown operation | Ignore it | A delayed debit that arrives after its compensation would otherwise move money nobody will return. |
| Outbox relay by polling, one replica at a time (advisory lock) | CDC (Debezium) | Simple to run locally and safe with several replicas: the advisory lock keeps per-transfer order, which `SKIP LOCKED` alone would break by splitting consecutive rows between replicas. CDC is the natural next step. |
| Fraud check before the transaction, fail closed | Fraud check inside the saga | No money moves without a risk answer, and a 503 lets the client retry with the same key. |
| Timeout → inquiry → manual reconciliation | Timeout → compensation | Compensating an interbank payment of unknown outcome can pay twice. |

## 5. Guarantees and limits

- **At-least-once everywhere.** The outbox can publish a row twice (crash after the broker ack) and Kafka can redeliver.
  Every consumer is idempotent: the orchestrator ignores results it no longer waits for, the connector deduplicates by
  order id, and integration events carry an `event-id` header.
- **Ordering per transfer.** Only one replica relays at a time (PostgreSQL advisory lock), rows are published in id
  order and keyed by transfer id, so one partition keeps them in order (the equivalent of an SQS FIFO message group).
  A message that fails holds back the later messages of its own transfer only; the rest of the outbox keeps flowing.
  `outbox_message.attempts` and `last_error` show a stuck message and should be alerted on.
- **Idempotency key scope and expiry.** Keys expire after `transfer.idempotency.retention` (24 h): an expired key
  starts a new transfer even before the hourly purge job deletes it. Keys are global in this exercise; in production
  they would also be scoped by customer.
- **No authentication.** The BFF and the gateway own authentication in the target design; it is out of scope here.
- **The mocks keep their state in memory**, so restarting the core mock resets balances.
