# 0003. Transactional outbox for events

- **Status:** Accepted
- **Date:** 2026-10-01

## Context

Other teams need to know when orders are placed, paid, shipped... We publish events on Kafka. The naive approach,
"save the order, then send to Kafka", has two failure modes:

- the database commit succeeds but the Kafka send fails or the process crashes: the event is lost;
- we send to Kafka inside the transaction, then the commit fails: we published an order that does not exist.

Distributed transactions between PostgreSQL and Kafka are not an option.

## Decision

We use the **transactional outbox** pattern:

1. Use cases write events to the `outbox_events` table in the same transaction as the business change
   (`OutboxWriter`, which requires an existing transaction).
2. A scheduled relay (`OutboxRelay`) reads unpublished rows in order, sends them to Kafka, waits for the
   acknowledgement and marks them as published.
3. Several replicas run the relay concurrently; `SELECT ... FOR UPDATE SKIP LOCKED` gives each a disjoint batch.
4. The Kafka key is the aggregate ID, so the events of one order stay ordered.

Alternatives: change data capture with Debezium (more infrastructure to run, a good option at higher volume) and
Spring Modulith's event publication registry (less explicit; we preferred code the team fully understands).

## Consequences

- No lost or phantom events. Delivery is **at least once**: an event can be published twice if the process dies
  between the Kafka ack and the database update. Consumers deduplicate on `eventId`.
- Events reach Kafka with a small delay (about a second).
- The table grows forever unless published rows are purged (backlog KST-107).
- A single event that can never be published blocks the following ones; monitored by the `CommerceOutboxBacklog`
  alert.
