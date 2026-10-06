# 0002. Modular monolith

- **Status:** Accepted
- **Date:** 2026-10-01

## Context

The shop needs a catalog, inventory, customers, orders and payments. These capabilities are tightly related: placing an
order reads products, reserves stock and records the customer, and must do so atomically. One team of a few engineers
owns all of them.

## Decision

We build one deployable service, split into business modules (`catalog`, `inventory`, `customer`, `order`,
`payment`) with explicit boundaries:

- modules call each other through application services, never through repositories or controllers;
- aggregates of different modules reference each other by ID only;
- no dependency cycles between modules.

The rules are enforced by `ArchitectureTest` (ArchUnit) on every build.

We rejected microservices for now: they would turn order placement into a distributed transaction (sagas,
compensation, eventual consistency between stock and orders), and add network failures, separate deployments and
operational overhead that a single team does not need.

## Consequences

- A single database transaction covers stock reservation, order creation and the outgoing event.
- Simple local development, testing and deployment.
- All modules scale together and share one database. If a module needs to scale or be released independently, its
  clean boundaries make extraction feasible: its application service becomes an HTTP API or events.
- The boundaries only hold if we keep the ArchUnit rules green. Changing a rule requires a new ADR.
