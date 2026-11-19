# ADR-004: API-first contracts with generated servers and clients

Status: Accepted. Date: 2026-11-18.

## Context

Three HTTP contracts exist (transfer, core banking, fraud), each one implemented by one service and consumed by
another. Hand-written clients drift from the servers they call.

## Decision

- Contracts are written first in `contracts/openapi/*.yaml` and are the single source of truth.
- Providers generate JAX-RS interfaces and models (`jaxrs-spec`, interface only) and implement them.
- Consumers generate MicroProfile REST clients (`java` / `microprofile`) registered with a config key.
- Each service serves its contract as its OpenAPI document instead of scanning the code.
- Errors use RFC 9457 problem details with a stable `code`, mapped centrally in `commons-web`.

## Consequences

A contract change breaks the build of both sides immediately. Generated code lives in `build/generated` and is never
edited; adapters translate generated models to domain types, so the domain does not depend on the contract.
