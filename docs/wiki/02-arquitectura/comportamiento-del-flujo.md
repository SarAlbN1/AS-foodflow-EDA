# Comportamiento del flujo

[← Índice de la wiki](../Home.md)

El orden de los pasos y quién reacciona a cada evento están en la [vista dinámica](estilo-y-flujo.md#estilo) y en el informe técnico; esta página define **qué ocurre cuando algo falla**.

## Reglas de comportamiento del flujo

**Publicación (ADR-08).** Order Service publica `OrderCreated` **después** del commit de la transacción que persiste el pedido. El productor usa `acks=all` e idempotencia habilitada. Si la publicación falla tras los reintentos del productor, se registra un `ERROR` con `correlationId` y `orderId`, y el pedido permanece en `CREADO`. No se implementa Outbox ni tarea de reconciliación.

**Fallo de negocio frente a fallo técnico.**

| Tipo | Ejemplo | Resultado |
|---|---|---|
| Técnico recuperable | Base momentáneamente caída | Reintentar (3 intentos, espera creciente) y luego DLQ |
| Técnico no recuperable | Mensaje corrupto, esquema o versión no soportada | DLQ directo, sin reintentos |
| De negocio | El proveedor de notificaciones falla tras sus reintentos | `Notification` pasa a `FALLIDA`, se publica `NotificationFailed` y el offset se confirma. **No** va a DLQ |

**Máquinas de estado.**

| Agregado | Transición permitida | Otra transición |
|---|---|---|
| Order | `CREADO` a `PAGADO`; `CREADO` a `PAGO_RECHAZADO` | Se ignora con `WARN`, sin error ni evento |
| Notification | `PENDIENTE` a `ENVIADA`; `PENDIENTE` a `FALLIDA` | Se ignora con `WARN` |

Un evento de pago para un pedido inexistente es un error recuperable: se reintenta y, si persiste, va a DLQ.

**Ruido en tópicos compartidos.** Payment Service ignora en `orders.events` todo evento distinto de `OrderCreated` (log `DEBUG` y confirmación de offset).
