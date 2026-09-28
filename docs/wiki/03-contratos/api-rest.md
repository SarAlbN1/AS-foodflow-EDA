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
