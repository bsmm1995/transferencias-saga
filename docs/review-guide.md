# Review guide

A checklist to review and validate the solution on your own machine, followed by the inventory of what was used.

## 1. Validation checklist

Run the commands from the repository root. Each step lists what you should see.

### 1.1 Toolchain

```bash
java -version            # 25.x
./gradlew --version      # Gradle 9.9.1, JVM 25
docker info              # Docker running (Dev Services, Testcontainers and compose need it)
```

### 1.2 Build, code generation and tests

```bash
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL`, 56 tests, 0 failures (transfer-service 33, core-banking-mock 11, fraud-mock 6,
payment-connector-mock 6). Reports: `services/*/build/reports/tests/test/index.html`.

Targeted runs:

```bash
./gradlew :services:transfer-service:test --tests '*TransferSagaTest*'   # saga state machine, no Docker
./gradlew :services:transfer-service:test --tests '*TransferSagaIT*'     # full saga with PostgreSQL (Testcontainers)
./gradlew test --tests '*ArchitectureTest*'                              # hexagonal rules in all 4 services
```

### 1.3 API-first contracts

```bash
./gradlew :services:transfer-service:openApiGenerateServer :services:transfer-service:openApiGenerateCoreBankingClient
```

- Contracts: `contracts/openapi/*.yaml`.
- Generated code: `services/*/build/generated/openapi/**` (never committed or edited).
- `TransferResource implements TransfersApi`, `LedgerResource implements LedgerApi`: the servers implement generated
  interfaces; `CoreBankingGateway` and `FraudEngineGateway` use generated MicroProfile REST clients. No hand-written
  HTTP client exists: `grep -rn "HttpClient\|WebTarget" services/*/src` returns nothing.

### 1.4 Hexagonal architecture

What to check in the code:

| Check | How |
|---|---|
| Domain and use cases are framework-free | `grep -rn "import jakarta\|import io.quarkus\|import org.hibernate\|import com.fasterxml" services/*/src/main/java/ec/fin/*/domain services/*/src/main/java/ec/fin/*/application` returns nothing |
| Dependencies point inwards | `ArchitectureTest` in each service (shared rules in `libs/architecture-rules`) |
| Use cases have no framework annotation | `application/service/*` are plain classes; they are wired in `infrastructure/config/UseCaseConfiguration` |
| Transactions are explicit | `UnitOfWork` port, implemented by `QuarkusUnitOfWork` |
| Every layer is documented | `package-info.java` in each layer and a Javadoc on every class |

### 1.5 Run it (dev mode)

Four terminals, transfer service first:

```bash
./gradlew :services:transfer-service:quarkusDev -Ddebug=5005
./gradlew :services:core-banking-mock:quarkusDev -Ddebug=5006
./gradlew :services:fraud-mock:quarkusDev -Ddebug=5007
./gradlew :services:payment-connector-mock:quarkusDev -Ddebug=5008
```

Expected in the logs: `Dev Services for default datasource (postgresql) started`, `Dev Services for Kafka started`,
`contains following topics with partitions: {transfer-events=3, payment-orders=3, payment-results=3}`, Liquibase
applying `0001-initial-schema.sql`, and `Listening on: http://localhost:808x`.

```bash
curl http://localhost:8080/q/health/ready          # {"status":"UP", ...} with the datasource and messaging readiness checks
```

Open:

- Dev UI: <http://localhost:8080/q/dev-ui> (beans, config, Hibernate, Liquibase, scheduler, Kafka topics and messages)
- Swagger UI: <http://localhost:8080/q/swagger-ui>, <http://localhost:8081/q/swagger-ui>, <http://localhost:8082/q/swagger-ui>

### 1.6 Saga scenarios

Run the requests in [`http/transfers.http`](../http/transfers.http) (VS Code REST Client or IntelliJ), or with curl:

```bash
curl -i -X POST http://localhost:8080/api/v1/transfers -H "Content-Type: application/json" \
  -H "Idempotency-Key: review-0001" \
  -d '{"customerId":"C-100","sourceAccount":"1000000001","beneficiary":{"bankCode":"001","accountNumber":"2000000001","holderName":"Carlos Vega"},"amount":100,"currency":"USD"}'

curl http://localhost:8080/api/v1/transfers/<transferId>
```

| Check | Request | Expected |
|---|---|---|
| Happy path, own bank | scenario 1 | `COMPLETED`, journal RISK_CHECK → DEBIT → CREDIT |
| Happy path, interbank | scenarios 2 and 3 | `COMPLETED`, journal includes PAYMENT_ORDER and PAYMENT_RESULT |
| Business rejection | scenarios 4, 5 | `REJECTED`, no compensation |
| Compensation | scenarios 6, 7 | `REVERSED`, journal ends in COMPENSATION:SUCCEEDED |
| Timeout without compensation | scenario 8 | `FAILED` after ~45 s, "manual reconciliation required" |
| Fraud | scenario 9 | `REJECTED`, ledger never called |
| Idempotency | scenarios 10a twice, then 10b | same id + `Idempotent-Replayed: true`; then `409 IDEMPOTENCY_KEY_REUSED` |
| Validation | scenario 11 | `400` `application/problem+json` with `violations` |
| Balances | `GET http://localhost:8081/core/v1/accounts/1000000001` | reduced only by completed transfers |

### 1.7 Resilience

```bash
# Unknown outcomes: the core applies the posting and then answers 503
curl -X PUT http://localhost:8081/core/v1/admin/chaos -H "Content-Type: application/json" \
  -d '{"failureRate":0.5,"mode":"AFTER_APPLY"}'
# ...send several own-bank transfers of 10.00, wait ~40 s, then:
curl -X PUT http://localhost:8081/core/v1/admin/chaos -H "Content-Type: application/json" \
  -d '{"failureRate":0,"mode":"BEFORE_APPLY"}'
```

Expected: each transfer ends `COMPLETED` or `REVERSED`; the journals show `RETRY_SCHEDULED`; the source balance decreased
by exactly `10 × COMPLETED` and the beneficiary increased by the same amount. No double debit, no lost money.

Other experiments (stopped participant, slow fraud engine, crash mid-saga) are in the
[testing guide](testing-guide.md#4-chaos-experiments).

### 1.8 Persistence and messaging

```bash
psql -h localhost -p 5433 -U transfers -d transfers     # dev mode, password: transfers
```

```sql
select saga_state, count(*) from transfer group by 1;                     -- saga state per transfer
select step, outcome, attempt, detail from saga_step order by id desc limit 20;  -- saga journal
select status, count(*) from outbox_message group by 1;                   -- all PUBLISHED a second later
select * from idempotency_key order by created_at desc limit 5;           -- key → transfer + fingerprint
```

Kafka: Dev UI → Apache Kafka Client → `payment-orders`, `payment-results`, `transfer-events` (in prod mode, Kafka UI at
<http://localhost:8090>). Messages are keyed by transfer id and carry `event-id`, `event-type` and `correlation-id` headers.

### 1.9 Prod mode (optional)

```bash
docker compose up -d
./scripts/start-all.ps1        # or the four java -jar commands in the README
./scripts/stop-all.ps1
docker compose down
```

## 2. What was used

### Messaging: decoupling with topics

| Need | Choice |
|---|---|
| Broker | Apache Kafka (target design: EventBridge + SQS; see [ADR-003](adr/ADR-003-kafka-as-local-event-bus.md)) |
| Quarkus integration | `quarkus-messaging-kafka` (SmallRye Reactive Messaging): `@Incoming` consumers, `MutinyEmitter` producers |
| Topics | `payment-orders` (command to the BCE connector), `payment-results` (its reply), `transfer-events` (integration events for any consumer), `*-dlq` |
| Ordering | key = transfer id → one partition per transfer (FIFO per transfer) |
| Reliability | transactional outbox in PostgreSQL + relay with `FOR UPDATE SKIP LOCKED`, `acks=all`, idempotent producer, dead letter topics, idempotent consumers |
| Dev / test | Dev Services Kafka shared between services in dev mode; in-memory connector in integration tests |

Synchronous calls (fraud score, core postings) use generated REST clients because the L3 diagram defines them as
request/response; the slow, asynchronous part (central bank clearing) goes through topics.

### Database: the saga is persisted

| Need | Choice |
|---|---|
| Engine | PostgreSQL 18 (target design: Aurora PostgreSQL) |
| Access | Hibernate ORM with Panache repositories (`quarkus-hibernate-orm-panache`), JDBC on virtual threads |
| Schema | Liquibase (`quarkus-liquibase`), Hibernate in `validate` mode only |
| Transactions | Narayana JTA through `QuarkusTransaction`, behind the `UnitOfWork` port |
| Saga state | table `transfer`: `saga_state`, `attempts`, `next_attempt_at`, `status_reason`, `version` (optimistic lock) |
| Saga journal | table `saga_step`: every step, outcome, attempt and detail |
| Idempotency | table `idempotency_key`: key → transfer id + SHA-256 fingerprint of the request |
| Outbox | table `outbox_message`: events and commands written in the same transaction as the state change |
| Dev / test | Dev Services PostgreSQL (port 5433 in dev, random in tests) |

The mocks keep their state in memory: they stand for external systems and are not part of the saga's persistence.

### Quarkus-native features in use

| Feature | Extension / API | Where |
|---|---|---|
| Dev Services (PostgreSQL, Kafka) | built into the extensions | dev mode and tests |
| Dev UI, live reload, continuous testing | `quarkusDev` | all services |
| REST server | Quarkus REST (`quarkus-rest-jackson`), `@ServerExceptionMapper`, `@ServerRequestFilter` | resources, `commons-web` |
| REST clients | `quarkus-rest-client-jackson` (MicroProfile REST Client) | ledger and fraud adapters |
| CDI | ArC: constructor injection, producers, `Instance<T>` | composition roots |
| Typed configuration | SmallRye Config `@ConfigMapping` | `TransferProperties` |
| Virtual threads | `@RunOnVirtualThread` | endpoints, consumers, scheduled jobs |
| Transactions | Narayana `QuarkusTransaction`, `@Transactional` | `QuarkusUnitOfWork`, `OutboxRelay` |
| Persistence | Hibernate ORM + Panache repositories, Liquibase | transfer service |
| Messaging | SmallRye Reactive Messaging Kafka, dead letter queue strategy | transfer service, connector |
| Scheduling | `quarkus-scheduler` `@Scheduled` with `ConcurrentExecution.SKIP` | saga recovery, outbox relay and purge |
| Resilience | SmallRye Fault Tolerance `@Timeout`, `@CircuitBreaker` | gateways |
| Validation | Hibernate Validator on generated models | REST input |
| Health | SmallRye Health (datasource and messaging readiness) | `/q/health` |
| OpenAPI | SmallRye OpenAPI serving the static contract, Swagger UI | `/q/openapi`, `/q/swagger-ui` |
| Testing | `@QuarkusTest`, `@InjectMock`, `InMemoryConnector`, REST Assured | `TransferSagaIT`, `LedgerResourceTest` |
