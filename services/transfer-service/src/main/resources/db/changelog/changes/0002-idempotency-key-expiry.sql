--liquibase formatted sql

--changeset transfer-service:0002-idempotency-key-created-at-idx
-- The purge job deletes by age; without this index it would scan the whole table every hour.
create index idempotency_key_created_at_idx on idempotency_key (created_at);
--rollback drop index idempotency_key_created_at_idx;
