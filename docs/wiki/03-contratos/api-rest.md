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

## Health checks (HU-604)

Cada servicio Spring Boot publica su estado con Spring Boot Actuator. `health` es el único grupo de endpoints expuesto: `env`, `configprops`, `metrics` y el resto responden `404`.

| Endpoint | Responde | Éxito | Error |
|---|---|---|---|
| `GET /actuator/health` | Estado agregado del servicio y de sus dependencias esenciales | `200` `UP` | `503` `DOWN` |
| `GET /actuator/health/liveness` | La aplicación arrancó y su contexto está vivo | `200` | `503` |
| `GET /actuator/health/readiness` | El servicio puede atender tráfico: sus dependencias responden | `200` | `503` |

La separación entre `liveness` y `readiness` es la que distingue una aplicación iniciada de una dependencia esencial no disponible. Cuando un servicio adquiera su base PostgreSQL, Actuator registra por sí solo el contribuyente `db`, que pasa a formar parte del estado agregado. Para Kafka no hay indicador automático: Spring Boot solo aporta uno para Kafka Streams, así que si se quiere reflejar el broker en el health habrá que añadir un `HealthIndicator` propio en la HU que lo justifique.

**El cuerpo no revela credenciales.** Se publica el estado por componente (`show-components=always`) pero nunca su detalle (`show-details=never`), que es donde Actuator incluiría la URL JDBC, el usuario o la versión del motor:

```json
{
  "status": "UP",
  "components": {
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

**El gateway todavía no los expone.** `gateway/api-gateway` sigue siendo un esqueleto sin servidor web: su health check se añade en HU-401, junto con la decisión de su stack (servlet o reactivo).
