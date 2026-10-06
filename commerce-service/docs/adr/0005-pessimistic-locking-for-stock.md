# 0005. Pessimistic locking for stock reservation

- **Status:** Accepted
- **Date:** 2026-10-01

## Context

Popular products sell out: many customers may order the last units at the same moment. Two concurrent transactions
reading "1 available" would both reserve it and oversell.

## Decision

Stock reservation locks the inventory rows (`SELECT ... FOR UPDATE`) before checking and updating them
(`InventoryItemRepository#findAllForUpdate`):

- rows are always locked **in product ID order**, so two orders containing the same products cannot deadlock;
- all lines are checked before any is reserved, and the error lists every product that is short;
- database check constraints (`reserved <= on_hand`, no negative values) are a second line of defence.

Other aggregates (orders, products, customers) use optimistic locking (`@Version`): conflicts there are rare and a
`409 CONCURRENT_MODIFICATION` is an acceptable outcome.

We rejected optimistic locking for stock: under contention most attempts would fail and need retries.
An atomic `UPDATE ... SET reserved = reserved + n WHERE on_hand - reserved >= n` would also work but makes the
multi-line, all-or-nothing check and the error details harder to express.

## Consequences

- Overselling is impossible, verified by `ConcurrentOrdersIT`.
- Orders for the same product are serialised for the duration of the transaction: transactions must stay short (no
  remote calls while holding locks).
