# Architecture decision records

An ADR captures one significant decision: the context, the decision and its consequences. They explain *why* the
code is the way it is, long after the people who decided have moved on.

- Write one when a decision is hard to reverse, affects several modules or teams, or will surprise a newcomer.
- Copy [template.md](template.md), number it, open a pull request: the discussion happens in the review.
- ADRs are immutable once accepted. To change a decision, write a new ADR that supersedes the old one.

| #    | Decision                                                                       | Status   |
|------|--------------------------------------------------------------------------------|----------|
| 0001 | [Record architecture decisions](0001-record-architecture-decisions.md)         | Accepted |
| 0002 | [Modular monolith](0002-modular-monolith.md)                                   | Accepted |
| 0003 | [Transactional outbox for events](0003-transactional-outbox.md)                | Accepted |
| 0004 | [Idempotency keys for order placement](0004-idempotency-keys.md)               | Accepted |
| 0005 | [Pessimistic locking for stock reservation](0005-pessimistic-locking-for-stock.md) | Accepted |
| 0006 | [Stateless JWT resource server](0006-jwt-resource-server.md)                   | Accepted |
| 0007 | [Flyway for schema migrations](0007-flyway-migrations.md)                      | Accepted |
