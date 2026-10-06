# Configuration

The service follows the [twelve-factor](https://12factor.net/config) approach: the same image runs in every
environment, and everything that differs between environments comes from **environment variables**.

- `src/main/resources/application.yml`: defaults shared by every environment (safe for production).
- `src/main/resources/application-local.yml`: values for a developer machine (`make run`).
- Deployed environments: non-secret values in the Kubernetes ConfigMap (`deploy/k8s`), secrets from the secret manager
  through an `ExternalSecret`. **Never commit a secret.**

Spring Boot maps environment variables to properties with
[relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables):
`spring.datasource.url` ⇔ `SPRING_DATASOURCE_URL`.

## Variables to set in a deployed environment

| Variable                                                   | Secret | Example                                                     |
|------------------------------------------------------------|--------|-------------------------------------------------------------|
| `SPRING_DATASOURCE_URL`                                    |        | `jdbc:postgresql://commerce-db.internal:5432/commerce`      |
| `SPRING_DATASOURCE_USERNAME`                               |        | `commerce_app`                                              |
| `SPRING_DATASOURCE_PASSWORD`                               | yes    |                                                             |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS`                           |        | `kafka.internal:9092`                                       |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`     |        | `https://auth.kestrel-outfitters.example/realms/kestrel`    |
| `COMMERCE_PAYMENTS_WEBHOOK_SIGNING_SECRET`                 | yes    | at least 32 characters, shared with the payment provider    |
| `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`    |        | `http://otel-collector.observability.svc:4318/v1/traces`    |
| `COMMERCE_CORS_ALLOWED_ORIGINS`                            |        | `https://shop.kestrel-outfitters.example`                   |

The application **refuses to start** if the webhook secret is missing or too short (`@Validated` configuration
properties): failing at deployment time is much better than failing on the first payment.

## Optional settings

| Variable                                       | Default   | Purpose                                                    |
|------------------------------------------------|-----------|------------------------------------------------------------|
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE`   | `10`      | Database connections per replica                           |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY`      | `0.1`     | Share of requests traced (1.0 = all)                       |
| `COMMERCE_OUTBOX_RELAY_ENABLED`                | `true`    | Stop publishing events (they accumulate safely)            |
| `COMMERCE_OUTBOX_RELAY_INTERVAL`               | `PT1S`    | Delay between two publication runs                         |
| `COMMERCE_OUTBOX_RELAY_BATCH_SIZE`             | `100`     | Events published per transaction                           |
| `COMMERCE_PAYMENTS_WEBHOOK_TOLERANCE`          | `PT5M`    | Maximum age of a webhook signature                         |
| `SPRINGDOC_API_DOCS_ENABLED`, `SPRINGDOC_SWAGGER_UI_ENABLED` | `false` | OpenAPI document and Swagger UI                  |
| `LOG_FORMAT`                                   | empty     | `ecs` for JSON logs (set by the Docker image)              |
| `LOGGING_LEVEL_COM_KESTREL`                    | `INFO`    | Log level of our code (`DEBUG` to investigate)             |

## Adding a setting

1. Group related settings in a `@ConfigurationProperties` record under the `commerce.*` prefix, with validation
   annotations and `@DefaultValue`s (see `OutboxProperties`, `PaymentWebhookProperties`).
2. Put the default in `application.yml` if it is safe for production, the local value in `application-local.yml`.
3. Secrets have no default anywhere and override `toString()` so they never appear in logs.
4. Add the variable to this document and to `deploy/k8s` if it differs per environment.
