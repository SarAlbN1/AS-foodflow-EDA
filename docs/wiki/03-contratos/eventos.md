# Eventos Kafka

[← Índice de la wiki](../Home.md)

## Tópicos

| Tópico | Productores | Consumidores | DLQ |
|---|---|---|---|
| `orders.events` | Order Service | Payment Service; consumidores futuros | `orders.events.dlq` |
| `payments.events` | Payment Service | Order Service, Notification Service | `payments.events.dlq` |
| `notifications.events` | Notification Service | Observabilidad o consumidores futuros | `notifications.events.dlq` |

Configuración propuesta: 3 particiones por tópico, factor de replicación 1, retención de 7 días (DLQ: 14). Los nombres de tópicos se leen de configuración, nunca como literales dispersos. Grupos de consumidores: `<servicio>.<tópico>` (por ejemplo `payment-service.orders`).

## Envelope común

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "eventVersion": 1,
  "occurredAt": "2026-09-27T20:00:00Z",
  "correlationId": "uuid",
  "aggregateId": "uuid",
  "payload": {}
}
```

`eventId` sustenta la idempotencia. `aggregateId` es siempre el `orderId` y se usa como clave de partición (ADR-04). Los contratos se versionan en `contracts/events/v1/`; ningún servicio depende de clases Java compartidas.

## Catálogo de payloads v1

| Evento | Productor | Campos de `payload` |
|---|---|---|
| `OrderCreated` | Order | `orderId`, `customerReference`, `total`, `currency`, `paymentToken`, `notificationContact` |
| `OrderStatusChanged` | Order | `orderId`, `previousStatus`, `newStatus`, `notificationContact` |
| `PaymentApproved` | Payment | `paymentId`, `orderId`, `amount`, `currency`, `transactionReference`, `notificationContact` |
| `PaymentRejected` | Payment | `paymentId`, `orderId`, `amount`, `currency`, `reasonCode`, `notificationContact` |
| `NotificationSent` | Notification | `notificationId`, `orderId`, `paymentId`, `channel`, `providerReference` |
| `NotificationFailed` | Notification | `notificationId`, `orderId`, `paymentId`, `channel`, `failureCode`, `attempts` |

`notificationContact` es el snapshot `{ "channel": "EMAIL", "destination": "..." }` (ADR-11). Convenciones: identificadores UUID, importes `NUMERIC(12,2)` / `BigDecimal` con `currency = "COP"`, fechas UTC (ISO 8601), JSON en `camelCase`. Los eventos se nombran en inglés y los estados de negocio en español.

**Cómo llega el contacto a Notification (supuesto A-2).** Notification Service consume `payments.events`; el snapshot llega en el propio evento de pago, copiado por Payment Service desde `OrderCreated`. `OrderStatusChanged` también lo transporta para consumidores futuros, pero Notification **no depende** de él, lo que preserva la regla 10.
