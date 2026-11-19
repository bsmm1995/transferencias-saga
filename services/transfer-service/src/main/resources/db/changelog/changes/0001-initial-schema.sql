--liquibase formatted sql

--changeset transfer-service:0001-transfer
create table transfer (
    id                    uuid primary key,
    customer_id           varchar(40)    not null,
    correlation_id        varchar(64),
    source_account        varchar(10)    not null,
    beneficiary_bank_code varchar(3)     not null,
    beneficiary_account   varchar(10)    not null,
    beneficiary_name      varchar(80)    not null,
    amount                numeric(18, 2) not null check (amount > 0),
    currency              varchar(3)     not null,
    concept               varchar(140),
    rail                  varchar(20)    not null,
    risk_score            integer,
    saga_state            varchar(30)    not null,
    attempts              integer        not null default 0,
    next_attempt_at       timestamp(6) with time zone,
    status_reason         varchar(400),
    created_at            timestamp(6) with time zone not null,
    updated_at            timestamp(6) with time zone not null,
    version               bigint         not null default 0
);
-- Partial index: the recovery job only ever scans active sagas.
create index transfer_due_idx on transfer (next_attempt_at)
    where saga_state in ('DEBIT_PENDING', 'CREDIT_PENDING', 'AWAITING_PAYMENT_RESULT', 'COMPENSATING');
--rollback drop table transfer;

--changeset transfer-service:0001-saga-step
create table saga_step (
    id          bigint generated always as identity primary key,
    transfer_id uuid         not null references transfer (id),
    step        varchar(20)  not null,
    outcome     varchar(20)  not null,
    attempt     integer      not null,
    detail      varchar(400),
    occurred_at timestamp(6) with time zone not null
);
create index saga_step_transfer_idx on saga_step (transfer_id, id);
--rollback drop table saga_step;

--changeset transfer-service:0001-idempotency-key
create table idempotency_key (
    idempotency_key     varchar(80) primary key,
    request_fingerprint varchar(64) not null,
    transfer_id         uuid        not null references transfer (id),
    created_at          timestamp(6) with time zone not null
);
--rollback drop table idempotency_key;

--changeset transfer-service:0001-outbox-message
create table outbox_message (
    id             bigint generated always as identity primary key,
    event_id       uuid         not null unique,
    aggregate_id   uuid         not null,
    topic          varchar(120) not null,
    event_type     varchar(80)  not null,
    payload        text         not null,
    correlation_id varchar(64),
    status         varchar(20)  not null,
    attempts       integer      not null default 0,
    last_error     varchar(400),
    created_at     timestamp(6) with time zone not null,
    published_at   timestamp(6) with time zone
);
create index outbox_pending_idx on outbox_message (id) where status = 'PENDING';
--rollback drop table outbox_message;
