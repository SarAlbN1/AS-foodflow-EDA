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

**La clave de idempotencia.** El informe técnico fija que se envía el `notificationId` al proveedor «como clave de idempotencia para que un reintento no genere un segundo envío con la misma clave». Se manda en la cabecera `Idempotency-Key` de `POST /v1/messages`. El proveedor simulado de HU-306 todavía no la lee y la ignora sin error; se envía igual porque un proveedor real la usaría y porque es lo que describe el informe.

## Catálogo de `failureCode` (HU-302)

Es el catálogo de `notifications.failure_code` y del `failureCode` de `NotificationFailed`, cuyo esquema remitía a esta historia para fijarlo. Lo implementa el enum `DeliveryFailure` de notification-service.

| `failureCode` | Cuándo | ¿Se reintenta? |
|---|---|---|
| `PROVEEDOR_NO_DISPONIBLE` | El proveedor respondió `5xx` en todos los intentos (`*@fail.test`, o `*@flaky.test` si se agotaran) | Sí |
| `TIEMPO_DE_ESPERA_AGOTADO` | Se agotó el tiempo de conexión o de lectura en todos los intentos (`*@slow.test`) | Sí |
| `ERROR_DE_CONEXION` | No se pudo establecer la conexión: el proveedor no responde en esa dirección | Sí |
| `PROVEEDOR_RECHAZO_EL_MENSAJE` | El proveedor respondió `4xx` | **No** |
| `RESPUESTA_INESPERADA` | Respondió fuera de contrato (un estado que no es `2xx`, `4xx` ni `5xx`) o su respuesta no se pudo tratar | **No** |

**Cuando no hay referencia legible se registra `SIN-REFERENCIA`.** El evento `NotificationSent` exige `providerReference` con al menos un carácter, y la aceptación es real: no se puede omitir. `SIN-REFERENCIA` no es un valor inventado, dice exactamente lo que pasó. Solo ocurre si el proveedor responde `2xx` sin un cuerpo del que extraerla.

**Un `2xx` es una aceptación aunque su cuerpo no se entienda.** Si el proveedor responde `202` con otro tipo de contenido o con un JSON roto, el mensaje **ya fue aceptado**: el envío se da por hecho y se registra sin `providerReference`, con un aviso. Darlo por fallido —o dejar escapar el error de lectura— perdería un envío que sí ocurrió. Lo encontró Juan al revisar HU-302.

**Por qué el `4xx` no se reintenta.** Dice que el mensaje no es aceptable, no que el proveedor esté indispuesto: repetirlo produce exactamente el mismo rechazo y solo retrasa el resultado. Los otros tres son transitorios por definición y agotan la política antes de darse por perdidos.

**Qué significa un envío fallido.** Es un resultado de **negocio**, no una avería: la notificación queda en `FALLIDA` y el evento de pago **no** va a DLQ (regla 10), su offset se confirma, y el resultado del pago sigue registrado en Order Service (regla 12). Quien persiste el resultado son HU-303 (`ENVIADA`) y HU-304 (`FALLIDA`); HU-302 solo hace el envío y lo registra.

## Qué dice el `content`

**El mensaje informa del resultado del pago, no del estado del pedido.** «Tu pago fue aprobado» o «tu pago fue rechazado»; nunca «tu pedido está pagado».

No es una preferencia de redacción: es lo que sostiene la decisión [D-6](../02-arquitectura/divergencias-informe-wiki.md). Notification Service reacciona a `PaymentApproved` y `PaymentRejected` en paralelo con Order Service, así que la notificación puede salir **antes** de que el pedido muestre su estado final, e incluso aunque Order Service no llegue a procesar su copia del evento. Un mensaje que hablara del pedido podría afirmar algo que todavía no es cierto, o que no llegará a serlo; uno que habla del pago es cierto siempre, porque el pago ya ocurrió cuando el evento se publicó.

Lo implementa HU-301 al construir la notificación.
