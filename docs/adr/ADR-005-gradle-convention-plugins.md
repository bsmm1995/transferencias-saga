# ADR-005: Gradle multi-project build with convention plugins

Status: Accepted. Date: 2026-11-18.

## Context

Four services and four libraries share the toolchain, Lombok, Quarkus, test setup and code generation. Copying that
configuration, or using `subprojects {}` blocks, makes every change a multi-file edit.

## Decision

- Gradle 9 wrapper with Kotlin DSL and a version catalog (`gradle/libs.versions.toml`).
- An included build, `build-logic`, provides `saga.java-conventions`, `saga.quarkus-service-conventions` and the
  `openApiServer` / `openApiClient` helpers.
- Each module's build script declares only what is specific to it.
- The Quarkus BOM is an enforced platform, so every Quarkus-managed dependency has one version.

## Consequences

A new service is a few lines of build script. Upgrading Quarkus, Java or the generator is a one-line change in the
catalog.
