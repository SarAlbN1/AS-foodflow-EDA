# Persistencia

[← Índice de la wiki](../Home.md)

Nombres de columna en `snake_case`; los campos siguientes se muestran en `camelCase` por legibilidad.

| Servicio | Tablas y campos mínimos |
|---|---|
| Order | `orders`: `id`, `customerReference`, `notificationChannel`, `customerContact`, `paymentToken`, `total`, `status`, `createdAt`, `updatedAt`. `idempotency_keys`: `key` (PK), `requestHash`, `orderId`, `createdAt`. `processed_events`. |
| Payment | `payments`: `id`, `orderId` (**único**), `amount`, `status`, `reasonCode`, `transactionReference`, `createdAt`, `updatedAt`. `processed_events`. |
| Notification | `notifications`: `id`, `orderId`, `paymentId`, `channel`, `destination`, `content`, `status`, `failureCode`, `attempts`, `createdAt`, `updatedAt`. `processed_events`. |

`processed_events` (ADR-09): `eventId` (PK, restricción única), `consumer`, `processedAt`. Su registro y el efecto de negocio se escriben en la **misma transacción local**; el offset se confirma solo después del commit.

**Creación del esquema:** scripts SQL versionados en `infrastructure/postgres/<db>/` y `spring.jpa.hibernate.ddl-auto=validate`. Flyway es opcional (página [Visión y alcance](../01-producto/vision-y-alcance.md)).
