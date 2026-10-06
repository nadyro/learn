# Onboarding: your first week on commerce-service

Welcome to the Commerce team! This guide takes you from a fresh laptop to your first merged pull request. Work through
it in order. When something is unclear or broken, fixing this document is a perfect first contribution.

## Day 1: run everything locally

### 1. Install the tools

| Tool             | Version | Check              | Notes                                                       |
|------------------|---------|--------------------|-------------------------------------------------------------|
| JDK              | 21      | `java -version`    | Temurin recommended, e.g. via [SDKMAN!](https://sdkman.io)  |
| Docker + Compose | recent  | `docker compose version` | Docker Desktop, OrbStack, Colima or Docker Engine      |
| make             | any     | `make --version`   | Preinstalled on macOS and Linux                             |
| IDE              |         |                    | IntelliJ IDEA is what most of the team uses                 |

You do **not** need to install Maven, PostgreSQL, Kafka or Keycloak: the Maven wrapper (`./mvnw`) downloads the right
Maven version, and everything else runs in containers.

IntelliJ setup: open `commerce-service/pom.xml` as a project, set the project SDK to 21, and install the
*palantir-java-format* plugin so the IDE formats code like the build does (or just run `make format` before
committing).

### 2. Start the environment and the service

```bash
cd commerce-service
make up       # Postgres, Keycloak (identity provider), Kafka, Kafka UI
make run      # the service, on http://localhost:8080
```

`make run` applies the database migrations, then loads demo products (`src/main/resources/db/seed/`). Watch the logs:
you should see `Started CommerceApplication`.

### 3. Make your first requests

```bash
make smoke-test     # a whole order lifecycle, the fastest way to check everything works
```

Then explore by hand. Open http://localhost:8080/swagger-ui.html, click **Authorize** and paste a token from
`make token user=alice`. Try to:

1. list the products and get one;
2. place an order (any UUID works as `Idempotency-Key`) and send the same request again: what changes in the response
   headers? Is a second order created?
3. try to order more units than available: read the error response carefully;
4. as `bob`, try to read alice's order;
5. pay it with `scripts/send-payment-webhook.sh <order-id> <total>`;
6. get a token for `olivia` (admin) and ship the order;
7. open Kafka UI (http://localhost:8085), topic `commerce.order-events.v1`: find the events of your order.

### 4. Run the tests

```bash
make test     # unit tests, seconds
make verify   # everything CI runs, a couple of minutes
```

## Day 2: understand the domain

Kestrel sells outdoor gear online. The vocabulary below is used everywhere, in code and in conversations.

| Term              | Meaning                                                                                       |
|-------------------|-----------------------------------------------------------------------------------------------|
| Product, SKU      | An item we sell. The SKU (stock keeping unit, e.g. `TENT-ALPINE-2P`) is its business identifier |
| Archived product  | No longer sold, but kept because past orders reference it                                     |
| On hand           | Units physically in the warehouse                                                             |
| Reserved          | Units promised to orders that have not shipped yet                                            |
| Available         | On hand − reserved: what can still be sold                                                    |
| Stock adjustment  | A manual stock change by the warehouse (restock, damaged goods, inventory count), audited     |
| Order             | A customer's purchase. Lines are a snapshot of product name and price at order time           |
| Order number      | Human-friendly identifier (`KO-00001042`) used by support and on emails                       |
| Payment provider  | External company that charges cards and notifies us through a webhook                         |
| Back-office       | Internal tools used by support and the warehouse (the `admin` role)                           |

Then read [architecture.md](architecture.md): the context diagram, the modules and the order lifecycle.

## Day 3: follow a request through the code

The best way to learn a codebase is to follow one request end to end. Take `POST /api/v1/orders` and open these files
in order:

1. **`shared/web/RequestIdFilter`**: the first code that sees the request. Assigns the request ID that appears in
   every log line and error response.
2. **`shared/security/SecurityConfig`**: validates the JWT and checks the caller has the `customer` role.
   `KeycloakJwtAuthenticationConverter` turns Keycloak roles into Spring authorities.
3. **`order/api/OrderController#placeOrder`**: HTTP concerns only. Bean Validation on `PlaceOrderRequest`, then the
   call to the use case, wrapped by `IdempotencyService`.
4. **`shared/idempotency/IdempotencyService`**: why it exists is in [ADR 0004](adr/0004-idempotency-keys.md).
5. **`order/application/OrderService#placeOrder`**: the use case. Notice that it talks to the catalog, inventory and
   customer modules through their *services*, never their repositories.
6. **`inventory/application/InventoryService#reserve`** and **`InventoryItemRepository#findAllForUpdate`**: how we make
   sure the last tent is never sold twice ([ADR 0005](adr/0005-pessimistic-locking-for-stock.md)).
7. **`order/domain/Order#place`**: the business rules live in the entity, not in the service.
8. **`shared/outbox/OutboxWriter`**, then **`OutboxRelay`**: how the `order.placed` event reaches Kafka without ever
   being lost or sent for a rolled-back order ([ADR 0003](adr/0003-transactional-outbox.md)).
9. **`shared/web/GlobalExceptionHandler`**: what the client receives when something goes wrong.

Then read the matching tests: `OrderTest`, `OrderServiceTest`, `OrderControllerTest`, `OrderLifecycleIT` and
`ConcurrentOrdersIT`. Notice what each level tests and what it does not.

## Day 4: how to add an endpoint

The checklist the team follows for a new endpoint, e.g. `GET /api/v1/admin/inventory/{id}/adjustments`:

1. **Domain**: entity/repository method if needed. Query methods return pages for anything that can grow.
2. **Migration**: if the schema changes, add `V<next>__<description>.sql`. Never edit an existing migration.
3. **Application**: a method on the module's service, with the right `@Transactional` (read-only for queries).
4. **API**: request/response records in `api/`, validation annotations, OpenAPI annotations (`@Operation`).
5. **Security**: add the route to `SecurityConfig` if the existing rules don't cover it, and to the matrix in
   `SecurityIT`.
6. **Errors**: reuse an `ErrorCode` or add one (it becomes part of the API contract).
7. **Tests**: domain rules in unit tests, HTTP behaviour in a `@WebMvcTest` or an `*IT`.
8. **Docs**: README API table, `docs/events.md` if you publish an event, the runbook if on-call needs to know.

## Day 5: your first ticket

Pick **KST-101** in the [backlog](backlog.md): it touches every layer without being risky. Create a branch, open a
pull request early (draft), and ask for feedback. See [CONTRIBUTING.md](../../CONTRIBUTING.md) for the conventions.

## Troubleshooting the local environment

**`make up` fails with `port is already allocated`, or `make run` fails with `FATAL: role "commerce" does not exist`.**
Another program already uses one of our ports, typically another project's Postgres container on 5432. In the second
case the service actually connected to *that* database, which has no `commerce` user. Find who holds the port with
`lsof -nP -iTCP:5432 -sTCP:LISTEN` (or `docker ps`), then either stop it, or move ours:

```bash
cp .env.example .env          # personal, git-ignored
# edit .env, e.g. COMMERCE_POSTGRES_PORT=15432
make down && make up && make run
```

`.env` is read by Docker Compose, by the `local` Spring profile and by the scripts, so they all agree on the ports.
Run the service from the `commerce-service` directory (as `make run` does): that is where it looks for `.env`.

**`make run` fails with `Connection refused`.** The containers are not running: `make up`, then check `make ps`.

**Requests return `401` with a token from `make token`.** The token expired (15 minutes): get a new one. If you changed
`COMMERCE_KEYCLOAK_PORT`, restart both Keycloak (`make down && make up`) and the service so they agree on the issuer.

## Debugging tips

- **Every error response has a `requestId`.** Search for it in the logs to find everything that happened during that
  request. Each log line also carries the `traceId`.
- **Traces**: `make up-observability`, start the service with `TRACING_ENABLED=true make run`, then open Jaeger
  (http://localhost:16686) to see every SQL query and Kafka call of a request with its duration.
- **Database**: `make db` opens `psql`. Useful queries:
  `select order_number, status from orders order by placed_at desc limit 5;`,
  `select event_type, published_at, attempts, last_error from outbox_events order by occurred_at desc limit 10;`
- **SQL logs**: start with `LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG make run` to see every query Hibernate sends.
  Great for spotting N+1 query problems.
- **Debugger**: run `CommerceApplication` from IntelliJ with the `local` profile (Run configuration → Active
  profiles), with `make up` running.
- **Reset everything**: `make reset && make up` gives you a fresh database.
