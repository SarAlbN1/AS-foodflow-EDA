# ÉPICA EP-03 — Gestión de notificaciones

[← Backlog](../README.md) · [Plan de sprints](../plan-de-sprints.md) · [Índice de la wiki](../../Home.md)

**Objetivo:** reaccionar al resultado del pago, registrar una notificación, solicitar su entrega y publicar el resultado.  
**Prioridad de la épica:** P0.

## HU-301 — Crear una notificación ante el resultado del pago

**Orden:** 1  
**Prioridad:** P0  
**Sprint:** 4 · **Puntos:** 5 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero que FoodFlow genere una notificación cuando se conozca el resultado de mi pago, para recibir información sobre el resultado de la compra.

**Criterios de aceptación**

1. Notification Service consume `PaymentApproved` y `PaymentRejected` desde `payments.events`.
2. Para cada resultado válido crea una Notification en estado `PENDIENTE`.
3. La notificación contiene `orderId`, `paymentId`, destino, contenido, canal y `correlationId` asociado al flujo.
4. El mismo `eventId` no genera notificaciones duplicadas.
5. Notification Service obtiene destino y canal del `notificationContact` incluido en el evento de pago (ADR-11) y no consulta Order DB ni Payment DB.

## HU-302 — Enviar una notificación mediante el proveedor externo

**Orden:** 2  
**Prioridad:** P0  
**Sprint:** 4 · **Puntos:** 5 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero que la notificación generada sea enviada mediante el proveedor configurado, para recibir el resultado de mi pago por el canal definido.

**Criterios de aceptación**

1. Notification Service realiza la solicitud HTTPS/REST al proveedor o mock configurado.
2. El proveedor recibe destino, canal y contenido requeridos.
3. La integración tiene timeout explícito.
4. Los errores HTTP o de conexión se traducen en un resultado controlado y no provocan pérdida silenciosa del mensaje.
5. Ningún otro servicio invoca directamente al proveedor.
6. Los reintentos hacia el proveedor son finitos y configurables (por defecto 3 intentos con espera de 500 ms que se duplica; timeouts de 2 s de conexión y 3 s de lectura). No se implementa Circuit Breaker.

## HU-303 — Registrar y publicar una notificación enviada

**Orden:** 3  
**Prioridad:** P0  
**Sprint:** 4 · **Puntos:** 3 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como operador del sistema, quiero que una entrega exitosa quede registrada y produzca `NotificationSent`, para conocer que el flujo de comunicación terminó correctamente.

**Criterios de aceptación**

1. Ante una respuesta exitosa del proveedor, la Notification pasa de `PENDIENTE` a `ENVIADA`.
2. El cambio se persiste en Notification DB.
3. Se publica `NotificationSent` en `notifications.events`.
4. El evento conserva `notificationId`, `orderId` y `correlationId`.
5. No se publica `NotificationFailed` para la misma ejecución exitosa.

## HU-304 — Registrar y publicar una notificación fallida

**Orden:** 4  
**Prioridad:** P0  
**Sprint:** 4 · **Puntos:** 5 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como operador del sistema, quiero que una entrega definitivamente fallida quede registrada y produzca `NotificationFailed`, para detectar fallos sin alterar el resultado del pedido o del pago.

**Criterios de aceptación**

1. Después de agotar los reintentos de envío al proveedor, la Notification pasa a `FALLIDA`. Es un resultado de negocio: el evento de pago **no** se envía a DLQ y el offset se confirma.
2. Se publica `NotificationFailed` en `notifications.events`.
3. El fallo no revierte el Payment ni el estado final del Order.
4. El evento incluye una causa o código técnico suficientemente útil para diagnóstico.

## HU-305 — Consultar las notificaciones de un pedido

**Orden:** 5  
**Prioridad:** P1  
**Sprint:** 4 · **Puntos:** 3 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero consultar las notificaciones asociadas a mi pedido, para verificar el mensaje generado y su estado de entrega.

**Criterios de aceptación**

1. `GET /orders/{orderId}/notifications` (vía API Gateway) devuelve las notificaciones del pedido; si aún no existen, responde `200` con una lista vacía.
2. La respuesta puede contener cero, una o varias notificaciones.
3. Cada elemento expone al menos canal, estado, contenido y fechas relevantes.
4. La consulta se resuelve únicamente desde Notification DB.

## HU-306 — Mock del proveedor de notificaciones

**Orden:** 6  
**Prioridad:** P0  
**Sprint:** 4 · **Puntos:** 3 · **Responsable:** Juan  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo de desarrollo, quiero un proveedor simulado con modos de fallo controlables, para demostrar el envío exitoso, los reintentos y el fallo definitivo sin depender de un tercero.

**Criterios de aceptación**

1. El mock expone `POST /v1/messages` y responde `202` con `providerReference`.
2. El comportamiento por destino cumple la página [Proveedor de notificaciones (mock)](../../03-contratos/proveedor-notificaciones.md) (`fail.test`, `flaky.test`, `slow.test`).
3. Corre como contenedor en Compose y su URL se configura por variable de entorno.
4. Hay pruebas o un guion que demuestran cada modo.
