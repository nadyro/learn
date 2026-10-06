# 0007. Flyway for schema migrations

- **Status:** Accepted
- **Date:** 2026-10-01

## Context

The database schema evolves with the code and must be identical in every environment. Letting Hibernate generate it
(`ddl-auto=update`) is unpredictable, cannot express data migrations and silently drifts.

## Decision

- The schema is defined by versioned SQL scripts in `src/main/resources/db/migration`, applied by Flyway at startup.
- Hibernate only validates that entities match the schema (`ddl-auto=validate`): a mismatch fails at startup, not in
  production traffic.
- Migrations are immutable once merged, and backward compatible with the previous version of the code (expand/
  contract), because old and new code run side by side during deployments.
- Local demo data lives in a separate location (`db/seed`), only enabled by the `local` profile.

## Consequences

- Reviewable, versioned, reproducible schema changes; tests run against the real migrations (Testcontainers).
- Developers write SQL by hand, including constraints and indexes, which is a feature: the database is designed, not
  generated.
- Very long migrations must not run at startup (see [deployment.md](../deployment.md#database-migrations)).
