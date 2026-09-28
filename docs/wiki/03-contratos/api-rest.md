# API REST

[← Índice de la wiki](../Home.md)

## API REST mínima (obligatoria)

| Operación | Servicio propietario | Éxito | Errores |
|---|---|---|---|
| `POST /orders` | Order Service | `201` con `Location` y cuerpo del pedido | `400` validación; `409` clave de idempotencia reutilizada con otro cuerpo |
| `GET /orders/{id}` | Order Service | `200` | `404` |
| `GET /orders/{id}/notifications` | Notification Service (vía gateway) | `200` con lista, posiblemente vacía | `400` identificador inválido |
| `GET /actuator/health` | Cada servicio | `200` | `503` |

No existe un endpoint de consulta de pagos: el resultado del pago es visible mediante el estado del pedido (`PAGADO` o `PAGO_RECHAZADO`).

**Cuerpo de `POST /orders`:**

```json
{
  "customerReference": "string, obligatorio, máx. 60",
  "customerContact": "email válido, obligatorio",
  "notificationChannel": "EMAIL",
  "total": "decimal mayor que 0, escala 2",
  "paymentToken": "PAY-OK | PAY-FAIL"
}
```

`paymentToken` es el punto de entrada del pago determinista (supuesto A-1). Cualquier otro valor produce `400`.

**Idempotency-Key.** Encabezado opcional para clientes, siempre enviado por Angular. Misma clave y mismo cuerpo: se devuelve la respuesta original sin crear otro pedido. Misma clave y cuerpo distinto: `409`.

**Representación del pedido.** La devuelven `POST /orders` (`201`) y `GET /orders/{id}` (`200`), idéntica en ambas, para que el frontend use un solo modelo:

```json
{
  "id": "3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10",
  "customerReference": "PED-0001",
  "customerContact": "ana@foodflow.test",
  "notificationChannel": "EMAIL",
  "total": 45000.00,
  "status": "CREADO",
  "createdAt": "2026-09-28T06:41:12.482913Z",
  "updatedAt": "2026-09-28T06:41:12.482913Z"
}
```

| Campo | Tipo | Regla |
|---|---|---|
| `id` | UUID | Identificador del pedido. En los eventos el mismo valor viaja como `aggregateId` y como `payload.orderId` |
| `customerReference` | string | Máx. 60, tal como se envió |
| `customerContact` | string | Correo del cliente; *snapshot* de ADR-11. En los registros se enmascara (`a***@dominio.com`) |
| `notificationChannel` | string | `EMAIL` (único canal del prototipo); *snapshot* de ADR-11 |
| `total` | number | Escala 2, mayor que 0 |
| `status` | string | `CREADO`, `PAGADO` o `PAGO_RECHAZADO` |
| `createdAt`, `updatedAt` | string | ISO 8601 en UTC con sufijo `Z` |

`Location` de la respuesta `201` es la ruta relativa `/orders/{id}`; el gateway le añade su propio prefijo si lo tiene.

La moneda **no** viaja en la representación: es `COP` para todo el prototipo (constante del envelope de eventos, `contracts/events/v1/envelope.schema.json`). El contacto y el canal van planos en REST (`customerContact`, `notificationChannel`) y anidados solo en los eventos, como `notificationContact: {channel, destination}`.

**Errores (RFC 9457 Problem Details):**

```json
{
  "type": "https://foodflow.local/problems/validation-error",
  "title": "Solicitud inválida",
  "status": 400,
  "detail": "total: debe ser mayor que cero",
  "code": "VALIDATION_ERROR",
  "correlationId": "uuid"
}
```

Nunca se devuelven trazas de pila. El contrato vive en `contracts/api/openapi.yaml` y se actualiza antes o junto con el código.
