# API guidelines

How we design the HTTP API. Consistency matters more than any individual choice: a client developer who learned one
endpoint should be able to guess how the others work.

## URLs and versions

- Base path `/api/v1`. The version only changes for breaking changes, which we avoid (see below).
- Plural nouns for collections: `/products`, `/orders/{orderId}`.
- Back-office endpoints live under `/api/v1/admin/...` and require the `admin` role.
- Actions that are not CRUD are sub-resources named with a verb or a noun: `POST /orders/{id}/cancel`,
  `POST /orders/{id}/shipment`.
- IDs are UUIDs. Never expose database sequences as identifiers (they leak volumes and are guessable).

## Methods and status codes

| Situation                                        | Response                                   |
|--------------------------------------------------|--------------------------------------------|
| Read                                             | `GET` → `200`                              |
| Create                                           | `POST` → `201` + `Location` header + body  |
| Full update                                      | `PUT` → `200` + body                       |
| Action (cancel, ship...)                         | `POST` → `200` + updated resource          |
| Invalid input (format, validation)               | `400 VALIDATION_FAILED` / `MALFORMED_REQUEST` |
| No or invalid token                              | `401 UNAUTHENTICATED`                      |
| Valid token, missing role                        | `403 ACCESS_DENIED`                        |
| Not found, **or not yours**                      | `404` (never reveal that someone else's resource exists) |
| Conflict with the current state                  | `409` (`INSUFFICIENT_STOCK`, `INVALID_ORDER_STATE`, ...) |
| Valid input that breaks a business rule          | `422` (`PRODUCT_NOT_PURCHASABLE`, ...)     |
| Bug or outage on our side                        | `500 INTERNAL_ERROR` (details only in logs) |

## Errors

Every error uses [RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`):

```json
{
  "type": "https://api.kestrel-outfitters.example/problems/insufficient-stock",
  "title": "Insufficient stock",
  "status": 409,
  "detail": "Not enough stock for 1 product(s), see 'shortages'.",
  "instance": "/api/v1/orders",
  "code": "INSUFFICIENT_STOCK",
  "requestId": "7404c542-be6a-4ff6-a9f2-106198d38ef9",
  "shortages": [{ "productId": "...", "requested": 5, "available": 3 }]
}
```

- `code` is the stable, machine-readable identifier clients branch on (`ErrorCode` enum). `title` and `detail` are
  for humans and may change.
- `requestId` is what a customer or a client team gives support to find the logs of the failed request.
- Validation errors list each invalid field in `errors: [{field, message}]`, with the JSON path of the field
  (`shippingAddress.countryCode`, `items[0].quantity`).
- 5xx responses never contain exception messages, SQL or stack traces.

## Pagination

Lists are always paginated: `?page=0&size=20` (max size 100), response:

```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 134, "totalPages": 7 }
```

The sort order is fixed per endpoint and documented. Accepting arbitrary sort fields from clients exposes unindexed
queries and internal field names.

## Idempotency

`POST` requests that create something with side effects (placing an order, later: refunds) require an
`Idempotency-Key` header. A retry with the same key and the same body returns the original response with
`Idempotent-Replayed: true`. The same key with a different body is rejected (`422 IDEMPOTENCY_KEY_REUSED`). Keys are
scoped to the caller. See [ADR 0004](adr/0004-idempotency-keys.md).

## JSON conventions

- `camelCase` field names; enums as `UPPER_SNAKE_CASE` strings.
- Timestamps in ISO-8601 UTC: `2026-10-01T09:30:00Z`.
- Money as an object: `{"amount": 129.90, "currency": "EUR"}`. Amounts have two decimals; never use floating point
  in clients either.
- Absent values are `null`, fields are not omitted (the shape of a response does not depend on the data).

## Evolving the API

Allowed at any time (backward compatible): adding an endpoint, adding an optional request field, adding a response
field, adding an error code, adding an enum value **if clients were told to tolerate unknown values**.

Breaking (requires a new version and a migration plan with the client teams): removing or renaming a field, changing a
type or format, making an optional field required, changing the meaning of a field or a status code.

## Documentation

The OpenAPI document is generated from the code (`/v3/api-docs`, Swagger UI on `/swagger-ui.html` in local and
staging). Annotate new endpoints with `@Operation(summary = ...)` and request fields with `@Schema(example = ...)`.
CI publishes `openapi.json` as a build artifact on every build, so API changes are visible in pull requests.
