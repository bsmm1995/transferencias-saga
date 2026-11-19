# Testing guide

## 1. Start everything

Either dev mode (Dev Services provide PostgreSQL and Kafka, one terminal per service):

```bash
./gradlew :services:transfer-service:quarkusDev -Ddebug=5005
./gradlew :services:core-banking-mock:quarkusDev -Ddebug=5006
./gradlew :services:fraud-mock:quarkusDev -Ddebug=5007
./gradlew :services:payment-connector-mock:quarkusDev -Ddebug=5008
```

or prod mode:

```bash
docker compose up -d                 # PostgreSQL, Kafka, topics, Kafka UI
./gradlew assemble -x test          # then the four java -jar commands in the README
```

See the [README](../README.md#quick-start) for details.

Check health:

```bash
curl http://localhost:8080/q/health/ready
```

## 2. Test data

### Accounts in the core mock

| Account | Holder | Balance | Status | Use it to |
|---|---|---|---|---|
| `1000000001` | Ana Torres | 5,000.00 | ACTIVE | send money (happy paths) |
| `1000000002` | Luis Paredes | 20.00 | ACTIVE | trigger `INSUFFICIENT_FUNDS` |
| `1000000003` | Marta Rios | 1,000.00 | BLOCKED | trigger `ACCOUNT_BLOCKED` |
| `2000000001` | Carlos Vega | 100.00 | ACTIVE | receive an own-bank credit |
| `2000000002` | Sofia Leon | 0.00 | CLOSED | make the credit fail and force a compensation |

The balances live in memory and reset when the core mock restarts.

### Rails, from the beneficiary bank code

| Bank code | Rail | Settles through |
|---|---|---|
| `001` | `OWN_BANK` | core debit + core credit |
| `010`, `017`, `036` | `INSTANT` | BCE instant payments network (RPI), ~300 ms |
| any other, e.g. `099` | `DEFERRED` | BCE SPI, ~3 s |

### Interbank beneficiary account suffix (payment connector)

| Account ends with | Clearing result |
|---|---|
| `0000` | `REJECTED` AC01 incorrect account number |
| `4040` | `REJECTED` AC04 closed account |
| `7777` | never answers (lost order) |
| anything else | `SETTLED` |

### Fraud engine

| Condition | Decision |
|---|---|
| amount > 10,000 | `DENY` (`AMOUNT_ABOVE_LIMIT`) |
| beneficiary account `9999999999` | `DENY` (`BENEFICIARY_BLOCKLISTED`) |
| otherwise | `APPROVE`, score proportional to the amount |

## 3. Scenario catalogue

All requests are in [`http/transfers.http`](../http/transfers.http). Use a new `Idempotency-Key` for each new transfer.

| # | Request (source → bank / beneficiary, amount) | Expected status | Expected journal |
|---|---|---|---|
| 1 | `1000000001` → `001` / `2000000001`, 100 | `COMPLETED` | RISK_CHECK ✓ · DEBIT ✓ · CREDIT ✓ |
| 2 | `1000000001` → `010` / `3000000001`, 50 | `COMPLETED` | … · PAYMENT_ORDER requested · PAYMENT_RESULT ✓ |
| 3 | `1000000001` → `099` / `3000000002`, 25 | `COMPLETED` after ~3 s | same as 2, through SPI |
| 4 | `1000000002` → `001` / `2000000001`, 500 | `REJECTED` | DEBIT rejected `INSUFFICIENT_FUNDS` |
| 5 | `1000000003` → `001` / `2000000001`, 10 | `REJECTED` | DEBIT rejected `ACCOUNT_BLOCKED` |
| 6 | `1000000001` → `001` / `2000000002`, 40 | `REVERSED` | DEBIT ✓ · CREDIT rejected · COMPENSATION ✓ |
| 7 | `1000000001` → `010` / `3000000000`, 30 | `REVERSED` | … · PAYMENT_RESULT rejected AC01 · COMPENSATION ✓ |
| 8 | `1000000001` → `010` / `3000007777`, 5 | `FAILED` after ~45 s | PAYMENT_RESULT timed out ×2 · failed |
| 9 | `1000000001` → `001` / `9999999999`, 10 | `REJECTED` immediately | RISK_CHECK rejected |
| 10 | same key and body as a previous request | same transfer, header `Idempotent-Replayed: true` | — |
| 11 | same key, different amount | `409` `IDEMPOTENCY_KEY_REUSED` | — |
| 12 | `bankCode: "1"`, `amount: -5` | `400` `VALIDATION_FAILED` with `violations` | — |
| 13 | `1000000001` → `001` / `1000000001` | `422` `SAME_ACCOUNT` | — |

After scenarios 1 to 7, the balance of `1000000001` should be `5000 − 100 − 50 − 25 = 4825.00`: rejected and reversed
transfers leave no trace in the balance.

```bash
curl http://localhost:8081/core/v1/accounts/1000000001
```

## 4. Chaos experiments

### Unknown outcomes (the interesting one)

Make half of the ledger postings apply **and then** answer 503:

```bash
curl -X PUT http://localhost:8081/core/v1/admin/chaos \
  -H "Content-Type: application/json" -d '{"failureRate":0.5,"mode":"AFTER_APPLY"}'
```

Send several own-bank transfers of 10.00 and watch the journals: you will see `RETRY_SCHEDULED` lines, retries that the
core answers as replays, and some transfers that exhaust their retries and get compensated. Then compare balances:

> source decreases and beneficiary increases by exactly `10 × number of COMPLETED transfers`.

No transfer is applied twice and no compensated transfer leaves money behind, even though the saga never knew whether
its calls had worked. Turn chaos off with `{"failureRate":0,"mode":"BEFORE_APPLY"}`.

Things to notice:

- With a high failure rate the **circuit breaker** of `CoreBankingGateway` opens (10 calls, 50 % failures) and fails
  fast for 10 s. The saga keeps retrying with backoff (1, 2, 4, 8, 16 s), which is why `max-attempts=6` is enough to outlive
  one open window.
- `BEFORE_APPLY` failures are the easy case: nothing happened, the retry simply applies the operation.

### Slow fraud engine

Start the fraud mock with `-Dfraud.simulated-latency=3s`. Requests now exceed the 2 s timeout in `FraudEngineGateway`, the
API answers `503` with `Retry-After`, and no transfer is created, so retrying with the same key is safe.

### Crash in the middle of a saga

1. Start an SPI transfer (scenario 3); it waits about 3 s for the clearing result.
2. Stop the transfer service right away (Ctrl+C in its terminal).
3. Start it again. The payment result waits in Kafka and the consumer applies it on restart; anything overdue is
   picked up by the recovery scheduler.

### Stopped participant

Stop `core-banking-mock` and create an own-bank transfer. The saga retries with backoff (`RETRY_SCHEDULED` in the journal).
Start the core again before the retries run out (about 30 s) and the transfer completes. If it stays down longer, the
saga switches to compensation, and if the core is still down when those retries run out too, the transfer ends in `FAILED`.

## 5. Observe

| What | Where |
|---|---|
| Saga journal of a transfer | `GET /api/v1/transfers/{id}` → `saga.steps` |
| Logs with correlation id | each service's console output (`[cid:...]`); send `X-Correlation-Id` to choose it |
| Messages per topic | Kafka UI <http://localhost:8090> → Topics → `payment-orders`, `payment-results`, `transfer-events` |
| Database (dev mode) | `psql -h localhost -p 5433 -U transfers -d transfers` (password `transfers`), or Dev UI → Agroal → Database view |
| Database (prod mode) | `docker compose exec postgres psql -U transfers -d transfers` |
| Kafka (dev mode) | Dev UI → Apache Kafka Client → Topics / Messages |

Useful queries:

```sql
-- sagas by state
select saga_state, count(*) from transfer group by 1;

-- journal of the last transfer
select step, outcome, attempt, detail, occurred_at
from saga_step where transfer_id = (select id from transfer order by created_at desc limit 1)
order by id;

-- outbox backlog (should be empty a second after any activity)
select topic, event_type, attempts, last_error from outbox_message where status = 'PENDING';
```

## 6. Automated tests

```bash
./gradlew build                                        # everything
./gradlew :services:transfer-service:test              # saga domain, integration and architecture tests
./gradlew :services:transfer-service:test --tests '*TransferSagaTest*'
```

`TransferSagaIT` needs Docker (it starts PostgreSQL through Quarkus Dev Services). The rest run without Docker.
