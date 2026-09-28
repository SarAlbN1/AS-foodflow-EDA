# Proveedor de notificaciones (mock)

[← Índice de la wiki](../Home.md)

## Proveedor de notificaciones (mock)

`POST /v1/messages` con `{channel, destination, content, correlationId}`. Responde `202` con `{providerReference}`. Para demostrar fallos sin tocar código, el comportamiento depende del destino:

| Destino | Comportamiento |
|---|---|
| Email normal | Éxito |
| `*@fail.test` | `503` siempre |
| `*@flaky.test` | Falla los 2 primeros intentos por destino y luego responde éxito |
| `*@slow.test` | Responde después del tiempo de lectura (prueba de timeout) |

Timeouts: conexión 2 s, lectura 3 s. Reintentos: 3 intentos con espera de 500 ms que se duplica. No hay Circuit Breaker.

## Qué dice el `content`

**El mensaje informa del resultado del pago, no del estado del pedido.** «Tu pago fue aprobado» o «tu pago fue rechazado»; nunca «tu pedido está pagado».

No es una preferencia de redacción: es lo que sostiene la decisión [D-6](../02-arquitectura/divergencias-informe-wiki.md). Notification Service reacciona a `PaymentApproved` y `PaymentRejected` en paralelo con Order Service, así que la notificación puede salir **antes** de que el pedido muestre su estado final, e incluso aunque Order Service no llegue a procesar su copia del evento. Un mensaje que hablara del pedido podría afirmar algo que todavía no es cierto, o que no llegará a serlo; uno que habla del pago es cierto siempre, porque el pago ya ocurrió cuando el evento se publicó.

Lo implementa HU-301 al construir la notificación.
