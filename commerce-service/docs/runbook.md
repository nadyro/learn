# Runbook

What to do when something goes wrong with commerce-service. Every alert in
[`infra/prometheus/alerts.yml`](../infra/prometheus/alerts.yml) has a section here.

**First steps for any incident**

1. Check the service dashboard (Grafana → Commerce → commerce-service): traffic, errors, latency, outbox, database pool.
2. Check recent deployments and configuration changes: most incidents follow a change. Rolling back is often the
   fastest fix (see [deployment.md](deployment.md#rolling-back)).
3. Search the logs with the `requestId` of a failing request, then follow its `traceId` in the tracing tool.
4. Communicate early in the incident channel, even if you don't know the cause yet.

---

## CommerceServiceDown

**Meaning:** no replica answers on its metrics endpoint for 2 minutes. The shop cannot take orders.

1. `kubectl -n commerce-production get pods -l app.kubernetes.io/name=commerce-service`: are pods running, restarting,
   pending?
2. Pods restarting: `kubectl logs <pod> --previous`. Common causes:
   - the application refused to start because of missing configuration (`Binding to target ... failed`) → fix the
     ConfigMap/secret;
   - a Flyway migration failed (`FlywayException`) → see [Failed migration](#failed-migration);
   - `OutOfMemoryError` → the container exits on purpose (`ExitOnOutOfMemoryError`); raise memory and investigate
     with a heap dump.
3. Pods not ready: the readiness probe includes the database. Check the database is up and reachable.

## CommerceHighErrorRate

**Meaning:** more than 2% of requests fail with a 5xx error for 5 minutes.

1. Dashboard: which endpoints fail? Did it start with a deployment?
2. Logs: filter on `level=ERROR`. `Unhandled exception while processing ...` lines contain the stack trace.
3. Database errors (`JDBCConnectionException`, timeouts) → check the database and
   [CommerceDatabasePoolExhausted](#commercedatabasepoolexhausted).
4. If it started with a deployment, roll back first, investigate after.

## CommerceHighLatency

**Meaning:** the 95th percentile of API latency is above 500 ms for 10 minutes.

1. Dashboard "p95 latency by endpoint": one endpoint or all of them?
2. All of them: database or pool saturation, CPU throttling, or garbage collection (JVM heap panel).
3. One endpoint: look at a slow trace for this endpoint. Many SQL queries for one request usually means an N+1 query
   problem; one slow query means a missing index (`EXPLAIN ANALYZE` it on a replica).
4. Lock contention: many concurrent orders on the same product wait for each other's stock lock. Check
   `pg_stat_activity` for `wait_event_type = 'Lock'`.

## CommerceOutboxBacklog

**Meaning:** more than 100 order events wait to be published to Kafka for 5 minutes. Orders still work, but other
teams (emails, warehouse...) don't hear about them.

1. Logs: `Could not publish outbox event` / `Still cannot publish outbox event` give the Kafka error.
2. Kafka unreachable or topic missing → check with the platform team. Events are safe in the database and are
   published automatically once Kafka is back, in order.
3. One event failing forever (e.g. message too large) blocks the ones after it. Find it:
   ```sql
   select id, event_type, attempts, last_error from outbox_events
   where published_at is null order by occurred_at limit 10;
   ```
   Fix the cause. As a last resort, after agreement with the consumer teams, mark it as published manually
   (`update outbox_events set published_at = now() where id = '...'`) and record it in the incident report.
4. The relay can be paused without losing events with `COMMERCE_OUTBOX_RELAY_ENABLED=false`.

## CommerceDatabasePoolExhausted

**Meaning:** requests are waiting for a free database connection. Requests that wait more than 5 s fail.

1. Is traffic unusually high (dashboard)? Scale out, but check first: `replicas × pool size` must stay below the
   database's `max_connections`.
2. Are connections held too long? Slow queries or lock waits (see [CommerceHighLatency](#commercehighlatency)).
   A transaction should never wait for a remote call.
3. Database side: `select state, wait_event_type, count(*) from pg_stat_activity group by 1, 2;`

---

## Common operations

### Failed migration

Flyway refuses to start the application after a failed migration (it is recorded as failed in
`flyway_schema_history`). Postgres runs each migration in a transaction, so a failed migration was rolled back.

1. Read the error in the logs, fix the migration in a new commit (it never ran successfully anywhere, so editing it is
   allowed in this case only).
2. Remove the failed entry: `delete from flyway_schema_history where success = false;`
3. Redeploy.

### Rotating the payment webhook secret

1. Generate a new secret (at least 32 random characters) and store it in the secret manager
   (`commerce-service/payment-provider`).
2. Wait for the External Secrets refresh (up to 1 h) or force it, then restart the deployment
   (`kubectl rollout restart deployment/commerce-service`).
3. Update the secret in the payment provider's dashboard. Webhooks rejected in between are retried by the provider.

### A customer says their order is stuck "pending payment"

1. Find the order: `select id, status, placed_at, payment_reference from orders where order_number = 'KO-...';`
2. Did we receive the webhook? Search the logs for the order ID, and
   `select * from payment_webhook_events order by processed_at desc limit 20;`
3. Rejected webhooks are logged with their error code (`INVALID_WEBHOOK_SIGNATURE`, `PAYMENT_AMOUNT_MISMATCH`...).
   An amount mismatch needs a human decision: involve customer support and finance.
