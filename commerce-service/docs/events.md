# Events

commerce-service publishes **order events** so that other teams (notifications, analytics, warehouse, finance) can
react to what happens in the shop without coupling to our API or database.

| Property         | Value                                                                                 |
|------------------|---------------------------------------------------------------------------------------|
| Topic            | `commerce.order-events.v1`                                                            |
| Key              | Order ID: all events of an order are on the same partition, in order                  |
| Value            | JSON envelope (below), UTF-8                                                          |
| Headers          | `event-id`, `event-type`                                                              |
| Delivery         | **At least once.** Consumers must deduplicate on `eventId`                            |
| Producer         | `OutboxRelay`, from the `outbox_events` table ([ADR 0003](adr/0003-transactional-outbox.md)) |

## Envelope

```json
{
  "eventId": "01a11105-c729-7c04-8bb9-b4aff7bca7e7",
  "eventType": "order.placed",
  "eventVersion": 1,
  "occurredAt": "2026-10-06T11:42:33.769031797Z",
  "aggregateType": "order",
  "aggregateId": "01a11105-c715-71ff-b43f-1c6e6323808a",
  "producer": "commerce-service",
  "data": { }
}
```

## Event types

| `eventType`        | When                                    | `data` fields                                                                                           |
|--------------------|-----------------------------------------|---------------------------------------------------------------------------------------------------------|
| `order.placed`     | A customer placed an order              | `orderId`, `orderNumber`, `customerId`, `lines[]` (`productId`, `sku`, `quantity`, `unitPrice`), `totalAmount`, `currency`, `shippingCountryCode`, `placedAt` |
| `order.paid`       | The payment provider confirmed payment  | `orderId`, `orderNumber`, `customerId`, `paymentReference`, `totalAmount`, `currency`, `paidAt`          |
| `order.cancelled`  | The order was cancelled                 | `orderId`, `orderNumber`, `customerId`, `reason` (`CUSTOMER_REQUEST`, `BACK_OFFICE`, `PAYMENT_TIMEOUT`), `cancelledAt` |
| `order.shipped`    | The warehouse handed it to a carrier    | `orderId`, `orderNumber`, `customerId`, `carrier`, `trackingNumber`, `shippedAt`                         |
| `order.delivered`  | The carrier delivered it                | `orderId`, `orderNumber`, `customerId`, `deliveredAt`                                                    |

Events carry IDs, not personal data (no names, emails or addresses): consumers that need them call the API with their
own credentials. This keeps personal data out of Kafka, where it is hard to delete.

The Java definitions are the records in `order/domain/event/`.

## Compatibility rules

Consumers are deployed independently from us, so the contract must evolve without breaking them:

- **Allowed**: adding a field to `data`, adding a new event type, adding an enum value (consumers must ignore unknown
  event types and tolerate unknown values).
- **Breaking**: removing or renaming a field, changing its type or meaning. Publish a new `eventVersion` (or a new
  topic `...v2`) alongside the old one, migrate the consumers, then stop the old one.
- Talk to the consumer teams before any change, and update this document in the same pull request.

## Consuming locally

Kafka UI (http://localhost:8085) shows the topic and its messages. From the command line:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic commerce.order-events.v1 --from-beginning \
  --formatter-property print.key=true
```
