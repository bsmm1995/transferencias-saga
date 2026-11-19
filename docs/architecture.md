# Architecture

## 1. Context

The transfer service receives transfer requests from the channel BFFs and coordinates three systems that share no
transaction: the core banking ledger, the fraud engine and the central bank (BCE) clearing networks.

```mermaid
flowchart TB
    customer([Customer]) --> bff[BFF Web / BFF Mobile]
    bff -- "POST /api/v1/transfers<br/>Idempotency-Key" --> ts[Transfer service]
    ts -- "risk score before the debit" --> fraud[Fraud engine]
    ts -- "debit, credit, reversal" --> core[Core banking<br/>through its anti-corruption layer]
    ts -- "payment orders (ISO 20022)" --> bce[BCE connector<br/>RPI instant / SPI deferred]
    ts -- "transfer events" --> consumers[Notifications, audit, analytics]
```

## 2. Containers

```mermaid
flowchart LR
    subgraph local [docker compose]
        pg[(PostgreSQL 18<br/>transfers DB)]
        kafka[(Kafka 4.4)]
        ui[Kafka UI :8090]
    end
    ts[transfer-service :8080<br/>Quarkus · saga orchestrator]
    core[core-banking-mock :8081<br/>Quarkus · in-memory ledger]
    fraud[fraud-mock :8082<br/>Quarkus · rules]
    pc[payment-connector-mock :8083<br/>Quarkus · RPI/SPI simulator]

    ts -- JDBC --> pg
    ts -- "REST (generated client)" --> core
    ts -- "REST (generated client)" --> fraud
    ts -- "produce payment-orders, transfer-events" --> kafka
    kafka -- "consume payment-results" --> ts
    kafka -- "consume payment-orders" --> pc
    pc -- "produce payment-results" --> kafka
    ui --- kafka
```

| Topic | Producer | Consumer | Key | Purpose |
|---|---|---|---|---|
| `payment-orders` | transfer-service (outbox) | payment-connector-mock | transfer id | command to clear an interbank payment |
| `payment-results` | payment-connector-mock | transfer-service | transfer id | settlement or rejection (ISO 20022 reason code) |
| `transfer-events` | transfer-service (outbox) | anyone (Kafka UI here) | transfer id | integration events: accepted, debited, completed, reversed, failed |
| `*-dlq` | SmallRye | operators | — | messages that keep failing |

## 3. Components of the transfer service (L3)

The implementation follows the L3 diagram of the platform proposal one to one:

| L3 component | Implementation |
|---|---|
| API de Transferencias | [`TransferResource`](../services/transfer-service/src/main/java/ec/fin/transfer/infrastructure/adapter/in/rest/TransferResource.java), implementing the `TransfersApi` interface generated from [`transfer-api.yaml`](../contracts/openapi/transfer-api.yaml) |
| Guardián de Idempotencia | `InitiateTransferService.replayOf` + [`IdempotencyPersistenceAdapter`](../services/transfer-service/src/main/java/ec/fin/transfer/infrastructure/adapter/out/persistence/IdempotencyPersistenceAdapter.java) (primary key on the idempotency key) |
| Iniciar Transferencia | [`InitiateTransferService`](../services/transfer-service/src/main/java/ec/fin/transfer/application/service/InitiateTransferService.java) |
| Router de Rieles | [`RailRouter`](../services/transfer-service/src/main/java/ec/fin/transfer/domain/service/RailRouter.java) with one `RailRoutingRule` strategy per rail ([ADR-006](adr/ADR-006-rail-routing-strategy.md)) |
| Orquestador de Saga | [`SagaOrchestrator`](../services/transfer-service/src/main/java/ec/fin/transfer/application/service/SagaOrchestrator.java) + the state machine in [`Transfer`](../services/transfer-service/src/main/java/ec/fin/transfer/domain/model/Transfer.java) |
| Adaptador Antifraude | [`FraudEngineRiskAdapter`](../services/transfer-service/src/main/java/ec/fin/transfer/infrastructure/adapter/out/risk/FraudEngineRiskAdapter.java) + `FraudEngineGateway` |
| Adaptador Ledger | [`CoreBankingLedgerAdapter`](../services/transfer-service/src/main/java/ec/fin/transfer/infrastructure/adapter/out/ledger/CoreBankingLedgerAdapter.java) + `CoreBankingGateway` |
| Repositorio | [`TransferPersistenceAdapter`](../services/transfer-service/src/main/java/ec/fin/transfer/infrastructure/adapter/out/persistence/TransferPersistenceAdapter.java), [`OutboxEventAppender`](../services/transfer-service/src/main/java/ec/fin/transfer/infrastructure/adapter/out/messaging/outbox/OutboxEventAppender.java), [`QuarkusUnitOfWork`](../services/transfer-service/src/main/java/ec/fin/transfer/infrastructure/adapter/out/persistence/QuarkusUnitOfWork.java) |
| Publicador Outbox | [`OutboxRelay`](../services/transfer-service/src/main/java/ec/fin/transfer/infrastructure/adapter/out/messaging/outbox/OutboxRelay.java) driven by its own [`OutboxRelayJobs`](../services/transfer-service/src/main/java/ec/fin/transfer/infrastructure/adapter/out/messaging/outbox/OutboxRelayJobs.java) schedule |
| Consumidor de Resultados | [`PaymentResultConsumer`](../services/transfer-service/src/main/java/ec/fin/transfer/infrastructure/adapter/in/messaging/PaymentResultConsumer.java) |
| Adaptador Core (ACL) | `core-banking-mock` (canonical ledger contract) |
| Motor Antifraude | `fraud-mock` |
| Conector de Pagos BCE | `payment-connector-mock` |
| Bus de Eventos + Colas por consumidor | Kafka topics with one consumer group per service |

### Hexagonal view

```mermaid
flowchart LR
    subgraph in [Driving adapters]
        rest[TransferResource<br/>REST]
        consumer[PaymentResultConsumer<br/>Kafka]
        jobs[SagaJobs<br/>scheduler]
        dispatcher[VirtualThreadSagaDispatcher]
    end
    subgraph app [Application — plain Java]
        pin{{port/in<br/>InitiateTransfer · GetTransfer<br/>AdvanceSaga · HandlePaymentResult}}
        svc[InitiateTransferService<br/>SagaOrchestrator<br/>TransferQueryService]
        pout{{port/out<br/>TransferRepository · IdempotencyStore · EventOutbox<br/>LedgerPort · RiskAssessmentPort · UnitOfWork · SagaDispatcher}}
    end
    subgraph dom [Domain — plain Java]
        transfer[Transfer aggregate<br/>saga state machine]
        router[RailRouter<br/>RailRoutingRule strategies]
        events[TransferEvent · SagaCommand]
    end
    subgraph out [Driven adapters]
        jpa[Persistence<br/>Hibernate + Panache]
        outbox[Outbox appender + scheduled relay]
        ledger[CoreBankingLedgerAdapter<br/>generated REST client]
        risk[FraudEngineRiskAdapter<br/>generated REST client]
        uow[QuarkusUnitOfWork]
    end
    rest --> pin
    consumer --> pin
    jobs --> pin
    dispatcher --> pin
    pin --> svc --> dom
    svc --> pout
    jpa -.implements.-> pout
    outbox -.implements.-> pout
    ledger -.implements.-> pout
    risk -.implements.-> pout
    uow -.implements.-> pout
    dispatcher -.implements.-> pout
```

The rules are enforced by [`HexagonalArchitectureRules`](../libs/architecture-rules/src/main/java/ec/fin/architecture/HexagonalArchitectureRules.java):

1. `domain` and `application` do not depend on `jakarta`, `io.quarkus`, `io.smallrye`, `org.hibernate`, `com.fasterxml` or Kafka.
2. `infrastructure` is accessed by nobody; `application` only by `infrastructure`; `domain` by both.
3. The domain does not log: it reports through return values and events.
4. Adapters reach use cases only through `port/in` interfaces, never through `application.service` classes.
5. Driving adapters (`adapter/in`) and driven adapters (`adapter/out`) never depend on each other.
6. `port/in` holds one interface per file, named `*UseCase`; its commands and results are nested in it.
7. `application/service` holds only use case implementations; shared helpers live in their own package
   (for example `application/idempotency`).

Use cases are wired in a composition root (`infrastructure/config/UseCaseConfiguration`) with CDI producers, so they
carry no framework annotation. Transactions are explicit through the `UnitOfWork` port, which keeps every saga step a
short local transaction and guarantees no remote call runs inside one.

## 4. Data model

```mermaid
erDiagram
    TRANSFER ||--o{ SAGA_STEP : "journal"
    TRANSFER ||--|| IDEMPOTENCY_KEY : "created by"
    TRANSFER ||..o{ OUTBOX_MESSAGE : "aggregate_id"
    TRANSFER {
        uuid id PK
        varchar customer_id
        varchar source_account
        varchar beneficiary_bank_code
        varchar beneficiary_account
        numeric amount
        varchar rail "OWN_BANK | INSTANT | DEFERRED"
        varchar saga_state "DEBIT_PENDING ... COMPLETED"
        int attempts
        timestamptz next_attempt_at "when the saga is due again"
        varchar status_reason
        bigint version "optimistic lock"
    }
    SAGA_STEP {
        bigint id PK
        uuid transfer_id FK
        varchar step "RISK_CHECK | DEBIT | CREDIT | PAYMENT_ORDER | PAYMENT_RESULT | COMPENSATION"
        varchar outcome "REQUESTED | SUCCEEDED | REJECTED | RETRY_SCHEDULED | TIMED_OUT | FAILED"
        int attempt
        varchar detail
    }
    IDEMPOTENCY_KEY {
        varchar idempotency_key PK
        varchar request_fingerprint "SHA-256 of the payload"
        uuid transfer_id FK
    }
    OUTBOX_MESSAGE {
        bigint id PK "publishing order"
        uuid event_id
        uuid aggregate_id
        varchar topic
        varchar event_type
        text payload
        varchar status "PENDING | PUBLISHED"
        int attempts
    }
```

The schema is owned by Liquibase ([`0001-initial-schema.sql`](../services/transfer-service/src/main/resources/db/changelog/changes/0001-initial-schema.sql));
Hibernate only validates that the mapping matches it (`schema-management.strategy=validate`). Two partial indexes
keep the background jobs cheap: active sagas by `next_attempt_at`, and pending outbox rows.

The customer-facing `status` is never stored: it is derived from `saga_state`, so they can never disagree.

## 5. Build

```mermaid
flowchart LR
    toml[gradle/libs.versions.toml] --> bl[build-logic<br/>saga.java-conventions<br/>saga.quarkus-service-conventions<br/>OpenApiCodegen.kt]
    contracts[contracts/openapi/*.yaml] --> gen[openApiGenerate* tasks]
    bl --> services
    gen -- "JAX-RS interfaces + models (providers)<br/>MicroProfile REST clients (consumers)" --> services[services/*]
    libs[libs/*] --> services
```

- `saga.java-conventions`: Java 25 toolchain, Lombok, JUnit platform, compiler flags.
- `saga.quarkus-service-conventions`: Quarkus plugin, the BOM as an enforced platform, the shared libs, the common extensions
  and Jib container images (`-Dquarkus.container-image.build=true`, group `transfer-saga`, tag = project version).
- `openApiServer(...)` / `openApiClient(...)`: generate code into `build/generated/openapi`, add it to the sources, and serve the
  contract itself as the service's OpenAPI document (`mp.openapi.scan.disable=true`): what is published is exactly what was designed.

CI ([`.github/workflows/build.yml`](../.github/workflows/build.yml)) runs `./gradlew build` on every push to `main` and
every pull request, so the architecture rules and the integration tests guard each change.

To add a service: create `services/<name>/build.gradle.kts` with `id("saga.quarkus-service-conventions")`, include it in
`settings.gradle.kts`, add its contract under `contracts/openapi` and call `openApiServer(...)`.
