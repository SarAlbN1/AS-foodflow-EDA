# ÉPICA EP-01 — Gestión de pedidos

[← Backlog](../README.md) · [Plan de sprints](../plan-de-sprints.md) · [Índice de la wiki](../../Home.md)

**Objetivo:** crear y consultar pedidos, persistir su estado y representar su evolución en respuesta a eventos.  
**Prioridad de la épica:** P0.

## HU-101 — Crear un pedido válido

**Orden:** 1  
**Prioridad:** P0  
**Sprint:** 1 · **Puntos:** 5 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero crear un pedido indicando mis datos de contacto, el canal de notificación, el valor total y el resultado de pago simulado, para iniciar el flujo de compra en FoodFlow.

**Criterios de aceptación**

1. **Dado** un request válido (página [API REST](../../03-contratos/api-rest.md)), **cuando** se crea el pedido, **entonces** Order Service genera un identificador único y lo persiste en Order DB.
2. El nuevo pedido se crea con estado `CREADO`.
3. Se validan: `total` mayor que cero, `customerContact` con formato de email, `notificationChannel` igual a `EMAIL` y `paymentToken` igual a `PAY-OK` o `PAY-FAIL`.
4. Los campos obligatorios vacíos o inválidos producen `400` en formato Problem Details y no crean registros.
5. El pedido conserva el *snapshot* de contacto y canal de notificación (ADR-11).
6. La respuesta es `201` con encabezado `Location` y contiene al menos `id`, `total`, `status` y fecha de creación.

## HU-102 — Consultar un pedido por identificador

**Orden:** 2  
**Prioridad:** P0  
**Sprint:** 1 · **Puntos:** 2 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero consultar un pedido por su identificador, para conocer su información y estado actual.

**Criterios de aceptación**

1. Un pedido existente puede consultarse mediante el endpoint definido para Order Service.
2. La respuesta refleja el estado persistido más reciente.
3. Un identificador inexistente devuelve `404`.
4. La consulta no invoca Payment Service, Notification Service ni Kafka para reconstruir el estado.

## HU-103 — Publicar OrderCreated al crear el pedido

**Orden:** 3  
**Prioridad:** P0  
**Sprint:** 2 · **Puntos:** 5 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como Payment Service, quiero recibir un evento `OrderCreated` después de que un pedido haya sido persistido, para iniciar el procesamiento del pago de forma desacoplada.

**Criterios de aceptación**

1. Al crear con éxito un pedido se publica `OrderCreated` en `orders.events` **después del commit** de la transacción que lo persiste.
2. El evento sigue el catálogo de la página [Eventos Kafka](../../03-contratos/eventos.md): incluye `orderId`, `total`, `currency`, `paymentToken` y `notificationContact`, además de `eventId` y `correlationId` en el envelope. La clave del mensaje es el `orderId`.
3. No se publica `OrderCreated` si la persistencia del pedido falla.
4. El productor usa `acks=all` e idempotencia habilitada.
5. Si la publicación falla tras los reintentos del productor, se registra un `ERROR` con `correlationId` y `orderId` y el pedido permanece en `CREADO`. Este es el riesgo aceptado por ADR-08: **no** se implementa Outbox ni tarea de reconciliación.
6. Order Service no llama directamente a Payment Service.

## HU-104 — Actualizar el pedido ante un pago aprobado

**Orden:** 4  
**Prioridad:** P0  
**Sprint:** 3 · **Puntos:** 3 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero que mi pedido cambie a `PAGADO` cuando el pago sea aprobado, para conocer el resultado correcto del proceso.

**Criterios de aceptación**

1. Order Service consume `PaymentApproved` desde `payments.events`.
2. El pedido relacionado cambia de `CREADO` a `PAGADO`.
3. Reprocesar el mismo `eventId` no produce efectos duplicados ni errores de estado.
4. El cambio queda persistido en Order DB.
5. El procesamiento no modifica Payment DB.

## HU-105 — Actualizar el pedido ante un pago rechazado

**Orden:** 5  
**Prioridad:** P0  
**Sprint:** 3 · **Puntos:** 2 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero que mi pedido cambie a `PAGO_RECHAZADO` cuando el pago sea rechazado, para conocer que la operación no fue aprobada.

**Criterios de aceptación**

1. Order Service consume `PaymentRejected` desde `payments.events`.
2. El pedido relacionado cambia de `CREADO` a `PAGO_RECHAZADO`.
3. Reprocesar el mismo evento es seguro e idempotente.
4. El estado se persiste únicamente en Order DB.
5. El pedido puede consultarse posteriormente con el nuevo estado.

## HU-106 — Publicar OrderStatusChanged al cambiar el estado del pedido

**Orden:** 6  
**Prioridad:** P1  
**Sprint:** 3 · **Puntos:** 3 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como consumidor interesado en los cambios de un pedido, quiero recibir `OrderStatusChanged` cuando cambie su estado, para reaccionar sin consultar directamente a Order Service.

**Criterios de aceptación**

1. Cada transición válida posterior a la creación (`CREADO` a `PAGADO` y `CREADO` a `PAGO_RECHAZADO`) publica `OrderStatusChanged` en `orders.events`, después del commit.
2. El payload contiene `orderId`, `previousStatus`, `newStatus` y `notificationContact`.
3. El evento conserva el `correlationId` del flujo.
4. Una transición inválida no produce evento.
5. En el prototipo ningún servicio depende de este evento: Payment Service lo ignora (página [Comportamiento del flujo](../../02-arquitectura/comportamiento-del-flujo.md)).
6. `OrderStatusChanged` reemplaza al antiguo `OrderUpdated`; no debe aparecer este último en código, contratos ni documentación.

## HU-107 — Evitar pedidos duplicados con Idempotency-Key

**Orden:** 7  
**Prioridad:** P0  
**Sprint:** 2 · **Puntos:** 3 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero que un reintento de mi solicitud de creación no genere un segundo pedido, para no duplicar pedidos ante fallos de red o dobles clics.

**Criterios de aceptación**

1. `POST /orders` acepta el encabezado `Idempotency-Key`.
2. Misma clave y mismo cuerpo devuelven la respuesta original sin crear otro pedido ni publicar otro `OrderCreated`.
3. Misma clave y cuerpo distinto devuelven `409` en formato Problem Details.
4. Sin encabezado, la solicitud se procesa normalmente.
5. Las claves se guardan en `idempotency_keys` de Order DB.
6. Existe una prueba con una clave repetida.
