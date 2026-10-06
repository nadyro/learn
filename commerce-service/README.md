# commerce-service

The core backend of the Kestrel Outfitters shop. It owns the **catalog**, the **inventory**, the **customers** and the
**orders**, receives **payment** notifications from our payment provider, and publishes **order events** to Kafka for
the rest of the company.

**Owner:** Commerce team · **On-call:** see [runbook](docs/runbook.md) · **API docs:** `/swagger-ui.html` (local and
staging only)

## Tech stack

| Concern           | Choice                                                                                     |
|-------------------|--------------------------------------------------------------------------------------------|
| Language, runtime | Java 21 (LTS)                                                                              |
| Framework         | Spring Boot 4.1 (Spring MVC, Spring Data JPA / Hibernate 7, Spring Security 7)             |
| Database          | PostgreSQL 17, schema managed by Flyway                                                    |
| Messaging         | Apache Kafka, fed by a transactional outbox                                                |
| Security          | OAuth2 resource server validating JWTs issued by Keycloak; HMAC-signed payment webhooks    |
| Observability     | Actuator health probes, Prometheus metrics, OpenTelemetry traces, structured JSON logs     |
| Tests             | JUnit 6, AssertJ, Mockito, Spring MockMvc, Testcontainers, ArchUnit, JaCoCo (80% gate)     |
| Build, delivery   | Maven (wrapper), Spotless, Docker (layered image, non-root), GitHub Actions, Kubernetes    |

## Quick start

Prerequisites: **JDK 21**, **Docker** (with Compose v2) and **make**. Maven is not needed: use `./mvnw`.

```bash
make up         # start Postgres, Keycloak, Kafka and Kafka UI (first run downloads the images)
make run        # start the service on http://localhost:8080 with the "local" profile
```

In another terminal:

```bash
make smoke-test                                   # places, pays, ships and delivers an order end to end

curl localhost:8080/api/v1/products               # public catalog
TOKEN=$(make token user=alice)                    # alice and bob are customers, olivia is an admin
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/customers/me
```

Run `make` to list every command. When you are done: `make down` (keeps data) or `make reset` (deletes it).

### Local URLs

| What                     | URL                                         | Credentials                         |
|--------------------------|---------------------------------------------|-------------------------------------|
| API                      | http://localhost:8080/api/v1                |  `make token user=<name>`           |
| Swagger UI               | http://localhost:8080/swagger-ui.html       | paste a token in "Authorize"        |
| Actuator (health, metrics) | http://localhost:8081/actuator            |                                     |
| Keycloak admin console   | http://localhost:8180                       | admin / admin                       |
| Kafka UI                 | http://localhost:8085                       |                                     |
| Grafana¹                 | http://localhost:3001                       |                                     |
| Prometheus¹              | http://localhost:9090                       |                                     |
| Jaeger (traces)¹         | http://localhost:16686                      |                                     |
| PostgreSQL               | localhost:5432, database `commerce`         | commerce / commerce (`make db`)     |

¹ Started by `make up-observability`. To send traces to Jaeger, start the service with `TRACING_ENABLED=true make run`.

Users of the local `kestrel` realm (password = username): `alice` and `bob` (customers), `olivia` (admin).

## API overview

| Endpoint                                              | Who        | What                                         |
|-------------------------------------------------------|------------|----------------------------------------------|
| `GET /api/v1/products[/{id}]`                         | anyone     | Browse and search the catalog                |
| `GET /api/v1/customers/me`                            | customer   | My profile (created on first call)           |
| `POST /api/v1/orders`                                 | customer   | Place an order (`Idempotency-Key` required)  |
| `GET /api/v1/orders[/{id}]`, `POST .../{id}/cancel`   | customer   | Follow and cancel my orders                  |
| `/api/v1/admin/products/**`                           | admin      | Create, update and archive products          |
| `/api/v1/admin/inventory/**`                          | admin      | Stock levels and adjustments                 |
| `/api/v1/admin/orders/**`                             | admin      | Search orders, ship, deliver, cancel         |
| `POST /api/v1/webhooks/payments`                      | provider   | Payment notifications (HMAC signature)       |

Errors use [RFC 9457 problem details](docs/api-guidelines.md#errors) with a stable `code` field. The full contract is
in the OpenAPI document (`/v3/api-docs`), also published by CI as a build artifact.

Ready-made requests for the IntelliJ HTTP client: [`http/commerce-service.http`](http/commerce-service.http).

## Project structure

```
src/main/java/com/kestrel/commerce/
├── catalog/        products                     ┐
├── inventory/      stock levels, adjustments    │  business modules, each split into:
├── customer/       customer profiles            │    api/          REST controllers + DTOs
├── order/          orders and their lifecycle   │    application/  use cases (the module's public API)
├── payment/        payment provider webhooks    ┘    domain/       entities, repositories, business rules
└── shared/         cross-cutting code: errors, security, web, idempotency, outbox, config
src/main/resources/
├── application.yml           configuration shared by all environments
├── application-local.yml     values for running on your machine
└── db/migration/             Flyway migrations (db/seed/ = local demo data)
```

The boundaries between modules are enforced by [`ArchitectureTest`](src/test/java/com/kestrel/commerce/ArchitectureTest.java).
Read [docs/architecture.md](docs/architecture.md) for the big picture.

## Testing

| Command       | Runs                                                       | Needs Docker | Time   |
|---------------|------------------------------------------------------------|--------------|--------|
| `make test`   | Unit tests and web slice tests (`*Test`)                   | no           | ~20 s  |
| `make verify` | + integration tests (`*IT`), formatting, coverage gate     | yes          | ~2 min |

Integration tests start real PostgreSQL and Kafka containers with Testcontainers: no mocks for the database, so
locking, constraints and SQL are actually tested. Run `make format` before committing.

## Documentation

| Document                                          | Read it when                                      |
|---------------------------------------------------|---------------------------------------------------|
| [Onboarding](docs/onboarding.md)                  | You just joined                                   |
| [Architecture](docs/architecture.md)              | You want the big picture                          |
| [API guidelines](docs/api-guidelines.md)          | You add or change an endpoint                     |
| [Events](docs/events.md)                          | You add or change a Kafka event                   |
| [Configuration](docs/configuration.md)            | You add a setting or deploy to a new environment  |
| [Deployment](docs/deployment.md)                  | You want to know how code reaches production      |
| [Runbook](docs/runbook.md)                        | You are on call, or an alert fired                |
| [Architecture decisions (ADRs)](docs/adr/)        | You wonder "why is it done this way?"             |
| [Backlog](docs/backlog.md)                        | You look for something to work on                 |
