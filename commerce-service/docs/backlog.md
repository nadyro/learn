# Backlog

The team's upcoming work, ordered roughly by difficulty. Each ticket is written the way the product manager and the
tech lead would write it: a need, acceptance criteria, and technical notes. The notes are hints, not orders: if you
find a better approach, propose it in your pull request.

**How to work on a ticket:** branch from `main` (`feature/KST-101-...`), follow the
[definition of done](../../CONTRIBUTING.md#definition-of-done), open a pull request with the template. Optionally,
create a GitHub issue from the ticket first so the work is tracked like in a real team.

| Ticket  | Title                                                        | Level        | You will learn                                     |
|---------|--------------------------------------------------------------|--------------|----------------------------------------------------|
| KST-101 | Back-office can see the stock adjustment history             | Starter      | All layers, pagination, security rules             |
| KST-102 | Show stock availability in the catalog                       | Starter      | Cross-module queries, avoiding N+1 queries         |
| KST-103 | Filter back-office order search by date                      | Starter      | Dynamic queries with Specifications, indexes       |
| KST-104 | Customers can save a default shipping address                | Intermediate | Migrations, embeddables, API design                |
| KST-105 | Cancel orders that are not paid within 30 minutes            | Intermediate | Scheduled jobs on several replicas, time in tests  |
| KST-106 | Handle failed payments                                       | Intermediate | Webhooks, state machines, events                   |
| KST-107 | Purge old idempotency keys and outbox events                 | Intermediate | Data retention, batch deletes, operations          |
| KST-108 | Prevent lost updates on products with ETags                  | Intermediate | HTTP conditional requests, optimistic locking      |
| KST-109 | Promotion codes                                              | Advanced     | Domain modelling, money arithmetic, concurrency    |
| KST-110 | Refund paid orders on cancellation                           | Advanced     | Calling external APIs: timeouts, retries, idempotency |
| KST-111 | Cache the product catalog                                    | Advanced     | Caching, invalidation, measuring                   |
| KST-112 | Bug: late payments for cancelled orders are retried forever  | Advanced     | Failure analysis, distributed edge cases           |

---

## KST-101 — Back-office can see the stock adjustment history

**Story.** As a warehouse manager, I want to see the history of manual stock changes of a product, so that I can
understand why its stock differs from what I expected.

**Acceptance criteria**

- `GET /api/v1/admin/inventory/{productId}/adjustments?page=0&size=20` returns the adjustments of the product, newest
  first, with `delta`, `reason`, `note`, `performedBy`, `onHandAfter` and `createdAt`.
- Paginated like every list (`PageResponse`), maximum page size 100.
- Unknown product → `404 INVENTORY_ITEM_NOT_FOUND`. Customers → `403`.

**Technical notes**

- `StockAdjustmentRepository` needs a paginated query; check that the existing index
  `ix_stock_adjustments_product_created_at` supports it.
- Add the endpoint to the matrix in `SecurityIT`, and an integration test in `InventoryIT`.

## KST-102 — Show stock availability in the catalog

**Story.** As a shopper, I want to see whether a product is in stock before adding it to my cart.

**Acceptance criteria**

- Product responses of the public catalog contain `"availability": "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK"`
  (low stock: fewer than 5 units available).
- We do not reveal exact stock quantities to the public.
- Listing 100 products runs a constant number of SQL queries, not one per product.

**Technical notes**

- The catalog module may only use the inventory module through `InventoryService`: add a method that returns the
  availability of many products at once.
- Check the number of queries with `LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG` or Hibernate statistics in a test.

## KST-103 — Filter back-office order search by date

**Story.** As a support agent, I want to find the orders placed between two dates, to answer questions like "my order
from last Tuesday".

**Acceptance criteria**

- `GET /api/v1/admin/orders` accepts optional `placedFrom` and `placedTo` (ISO-8601 instants), combinable with the
  existing filters.
- `placedFrom` after `placedTo` → `400 VALIDATION_FAILED`.

**Technical notes**

- Add specifications to `OrderSpecifications`. Is there an index that serves a date-only search?
  (`EXPLAIN` the query in `make db`.)

## KST-104 — Customers can save a default shipping address

**Story.** As a returning customer, I don't want to type my address for every order.

**Acceptance criteria**

- `PUT /api/v1/customers/me/default-shipping-address` saves an address (same validation as orders);
  `GET /api/v1/customers/me` returns it.
- `shippingAddress` becomes optional in `POST /api/v1/orders`: when absent, the default address is used; when there
  is none, `422` with a dedicated error code.
- Orders keep their own copy: changing the default address does not change past orders.

**Technical notes**

- New migration adding nullable columns to `customers` (a new `V8__...` file, never edit an existing one).
- `ShippingAddress` is an embeddable record: can you reuse it in `Customer`?
- Making a required field optional is a backward-compatible API change. Why?

## KST-105 — Cancel orders that are not paid within 30 minutes

**Story.** As the operations team, we want unpaid orders to release their stock, so that products are not blocked by
customers who abandoned their payment.

**Acceptance criteria**

- Orders still `PENDING_PAYMENT` 30 minutes after `placedAt` are cancelled with reason `PAYMENT_TIMEOUT`, their stock
  is released and an `order.cancelled` event is published.
- The delay is configurable (`commerce.orders.payment-timeout`).
- With three replicas running, each expired order is cancelled exactly once.
- A metric counts the orders cancelled by the job.

**Technical notes**

- A `@Scheduled` job runs on every replica. Look at how `OutboxRelay` handles that (`FOR UPDATE SKIP LOCKED`), or use
  a distributed lock library such as ShedLock. Write down the trade-off in your PR.
- Process expired orders in small batches, each in its own transaction.
- Testing time-based logic: inject the `Clock` and use a controllable one in tests instead of sleeping.
- Which index makes "pending orders placed before X" fast?

## KST-106 — Handle failed payments

**Story.** As a customer whose card was declined, I want my order to tell me the payment failed, so that I can retry
or cancel it.

**Acceptance criteria**

- A `payment.failed` webhook (`data`: `orderId`, `paymentReference`, `failureReason`) records the failure on the
  order: the order stays `PENDING_PAYMENT`, exposes `lastPaymentFailure` (reason and date) and an
  `order.payment_failed` event is published.
- A later `payment.succeeded` for the same order still works.
- Webhook processing stays idempotent.

**Technical notes**

- Start in `PaymentWebhookService`. Update `docs/events.md` with the new event.

## KST-107 — Purge old idempotency keys and outbox events

**Story.** As the team operating the database, we want tables that only grow to be cleaned up, so that storage costs
and backup times stay under control.

**Acceptance criteria**

- Idempotency keys older than 24 hours and outbox events published more than 7 days ago are deleted daily.
- Retention periods are configurable.
- Deletion happens in batches (for example 1,000 rows per transaction) to avoid long locks and huge transactions.
- The number of deleted rows is logged and exposed as a metric.

**Technical notes**

- Same multi-replica concern as KST-105.
- Update the [runbook](runbook.md) and the "known limitations" of [architecture.md](architecture.md).

## KST-108 — Prevent lost updates on products with ETags

**Story.** As a catalog manager, I don't want my price change to be silently overwritten by a colleague who edited
the same product at the same time.

**Acceptance criteria**

- `GET /api/v1/admin/products/{id}` returns an `ETag` header derived from the product version.
- `PUT /api/v1/admin/products/{id}` requires `If-Match`. A stale value → `412 Precondition Failed`; missing →
  `428 Precondition Required`.

**Technical notes**

- The entity already has a `@Version` column (`AuditableEntity`), but it only protects against concurrent
  *transactions*, not against a client that read the product five minutes ago. Explain why in your PR.

## KST-109 — Promotion codes

**Story.** As the marketing team, we want to run campaigns with promotion codes (e.g. `SUMMER10` for 10% off).

**Acceptance criteria**

- Admins create codes: percentage or fixed amount, validity period, optional maximum number of uses, optional minimum
  order total.
- Customers pass an optional `promotionCode` when placing an order. The order stores the code, the subtotal, the
  discount and the total.
- Invalid, expired or exhausted codes → `422` with a dedicated error code.
- A code with 1 remaining use cannot be used twice by concurrent orders.
- Rounding of percentage discounts is explicit and tested (`HALF_EVEN`, 2 decimals).

**Technical notes**

- A new module (`promotion`) or part of `order`? Discuss it and consider an ADR.
- Events: is adding `discountAmount` to `order.placed` backward compatible?

## KST-110 — Refund paid orders on cancellation

**Story.** As a customer, I want to cancel a paid order that has not shipped yet and get my money back.

**Acceptance criteria**

- Paid, unshipped orders can be cancelled by the customer and the back-office.
- A refund is requested from the payment provider's API (`POST /v1/refunds` with an idempotency key); the order
  records the refund reference and an `order.refunded` event is published.
- If the provider is down, the cancellation still succeeds and the refund is retried later.

**Technical notes**

- Never call a remote API while holding a database transaction (and stock locks). One approach: record a "refund
  requested" state and let a job call the provider (outbox-like).
- Use Spring's `RestClient` with connect/read timeouts, and test with WireMock (including timeouts and 5xx errors).
- The state machine changes (`PAID → CANCELLED`): update `OrderStatus`, the diagram in `architecture.md` and the
  tests.

## KST-111 — Cache the product catalog

**Story.** As the platform team, we expect 10x traffic during the winter sales, mostly on the catalog.

**Acceptance criteria**

- Product reads by ID are served from an in-memory cache; updates and archiving evict the entry.
- Cache hit ratio is visible in Prometheus.
- A benchmark (a simple load test, e.g. with k6) shows the improvement; the numbers are in the PR.

**Technical notes**

- Spring Cache with Caffeine. With several replicas, each has its own cache: what is the consequence for a price
  change? Is it acceptable, and for how long?

## KST-112 — Bug: late payments for cancelled orders are retried forever

**Report from support.** "A customer was charged but their order shows as cancelled."

**What happens.** A customer cancels an unpaid order while their payment is being processed. The payment provider
then sends `payment.succeeded` for the cancelled order. We answer `409 INVALID_ORDER_STATE`, the provider retries for
three days, and the customer has paid for nothing.

**Acceptance criteria**

- Write a failing integration test reproducing the scenario first.
- A payment for a cancelled order is acknowledged (2xx, so the provider stops retrying), flagged for a refund
  (see KST-110, or at minimum an `order.payment_received_after_cancellation` event and an ERROR log that alerts
  support), and visible in the order.

**Technical notes**

- This is a classic distributed systems edge case: two systems (us, the provider) changing the same thing at the same
  time. Which other webhook outcomes have the same problem?
