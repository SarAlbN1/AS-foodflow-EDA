# ÉPICA EP-02 — Procesamiento de pagos

[← Backlog](../README.md) · [Plan de sprints](../plan-de-sprints.md) · [Índice de la wiki](../../Home.md)

**Objetivo:** procesar de forma asíncrona y determinista el pago asociado a un pedido y publicar su resultado.  
**Prioridad de la épica:** P0.

## HU-201 — Consumir OrderCreated para iniciar un pago

**Orden:** 1  
**Prioridad:** P0  
**Sprint:** 3 · **Puntos:** 3 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como sistema de pagos, quiero reaccionar a `OrderCreated`, para iniciar el pago sin acoplarme mediante una llamada REST a Order Service.

**Criterios de aceptación**

1. Payment Service está suscrito a `orders.events` dentro de su propio consumer group.
2. Solo procesa eventos `OrderCreated` compatibles con la versión soportada; ignora sin error (log `DEBUG` y confirmación de offset) cualquier otro tipo de evento de `orders.events`, como `OrderStatusChanged`.
3. Extrae `orderId`, monto y `correlationId` requeridos.
4. Un evento inválido no crea un pago exitoso.
5. Payment Service no consulta Order DB.

## HU-202 — Procesar y persistir el resultado del pago

**Orden:** 2  
**Prioridad:** P0  
**Sprint:** 3 · **Puntos:** 5 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como sistema de pagos, quiero determinar y registrar el resultado del pago de un pedido, para conservar un resultado transaccional independiente del estado del pedido.

**Criterios de aceptación**

1. Para un `OrderCreated` válido se crea un registro de Payment asociado a `orderId`.
2. El monto registrado coincide con el monto recibido en el evento.
3. El resultado es determinista según `paymentToken` (ADR-10): `PAY-OK` produce `APROBADO` y `PAY-FAIL` produce `RECHAZADO`. Cualquier otro valor se trata como evento no procesable (DLQ).
4. La referencia de transacción es identificable y no nula cuando el procesamiento termina.
5. No se crean múltiples pagos para el mismo `orderId` al reprocesar el mismo evento.

## HU-203 — Publicar PaymentApproved

**Orden:** 3  
**Prioridad:** P0  
**Sprint:** 3 · **Puntos:** 2 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como Order Service y Notification Service, queremos recibir `PaymentApproved` cuando un pago sea aprobado, para actualizar el pedido y notificar al cliente de forma independiente.

**Criterios de aceptación**

1. Un pago persistido como `APROBADO` genera `PaymentApproved` en `payments.events`.
2. El evento sigue el catálogo de la página [Eventos Kafka](../../03-contratos/eventos.md): contiene `paymentId`, `orderId`, `amount`, `currency`, `transactionReference` y el `notificationContact` recibido en `OrderCreated`, además del `correlationId` en el envelope.
3. No se publica `PaymentApproved` si la persistencia del pago falla.
4. Order Service y Notification Service pueden consumir el mismo evento mediante consumer groups distintos.

## HU-204 — Publicar PaymentRejected

**Orden:** 4  
**Prioridad:** P0  
**Sprint:** 3 · **Puntos:** 2 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como Order Service y Notification Service, queremos recibir `PaymentRejected` cuando un pago sea rechazado, para reflejar el rechazo y comunicarlo al cliente de forma desacoplada.

**Criterios de aceptación**

1. Un pago persistido como `RECHAZADO` genera `PaymentRejected` en `payments.events`.
2. El evento sigue el catálogo de la página [Eventos Kafka](../../03-contratos/eventos.md): contiene `paymentId`, `orderId`, `amount`, `currency`, `reasonCode` y el `notificationContact` recibido en `OrderCreated`, además del `correlationId` en el envelope.
3. No se emite un evento de aprobación para el mismo resultado lógico.
4. La publicación no requiere una llamada REST hacia Order Service o Notification Service.

## HU-205 — Consultar el pago de un pedido

**Orden:** 5  
**Prioridad:** P2  
**Sprint:** — (opcional, fuera del plan) · **Puntos:** — · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero consultar el resultado del pago asociado a mi pedido, para visualizar si fue aprobado o rechazado.

**Criterios de aceptación**

1. El API permite consultar el pago mediante un identificador explícito o por `orderId` según el contrato OpenAPI.
2. La respuesta contiene estado, monto y referencia cuando corresponda.
3. Si todavía no existe pago por consistencia eventual, la API responde de forma definida y documentada sin inventar un resultado.
4. La consulta solo utiliza Payment DB.

> **Nota (opcional, fuera del plan de sprints):** la API mínima obligatoria (página [API REST](../../03-contratos/api-rest.md)) no incluye un endpoint de consulta de pagos; el resultado del pago es visible mediante el estado del pedido. Solo se implementa si sobra capacidad y con una decisión explícita (punto abierto A-3).
