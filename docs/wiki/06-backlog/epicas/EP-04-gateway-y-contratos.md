# ÉPICA EP-04 — API Gateway y contratos HTTP

[← Backlog](../README.md) · [Plan de sprints](../plan-de-sprints.md) · [Índice de la wiki](../../Home.md)

**Objetivo:** exponer un único punto de entrada para el frontend con un contrato REST documentado.  
**Prioridad de la épica:** P0.

## HU-401 — Enrutar operaciones de pedidos

**Orden:** 1  
**Prioridad:** P0  
**Sprint:** 1 · **Puntos:** 3 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como aplicación web, quiero consumir las operaciones de pedidos desde un único punto de entrada, para no conocer la ubicación interna de Order Service.

**Criterios de aceptación**

1. `POST /orders` se enruta a Order Service.
2. `GET /orders/{id}` se enruta a Order Service.
3. El gateway no implementa reglas de negocio de pedidos.
4. Los códigos HTTP relevantes del servicio se preservan o transforman de manera documentada.
5. El encabezado `Idempotency-Key` se reenvía sin modificar a Order Service.

## HU-402 — Enrutar la consulta de notificaciones

**Orden:** 2  
**Prioridad:** P1  
**Sprint:** 4 · **Puntos:** 3 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como aplicación web, quiero consultar las notificaciones de un pedido mediante el API Gateway, para mantener un único punto de integración con FoodFlow.

**Criterios de aceptación**

1. `GET /orders/{id}/notifications` se enruta únicamente a Notification Service.
2. El gateway no implementa reglas de negocio ni agrega datos de otros servicios.
3. El frontend no contiene URLs internas de los servicios.
4. La ruta queda documentada en `contracts/api/openapi.yaml`.

## HU-403 — Propagar correlationId y manejar CORS

**Orden:** 3  
**Prioridad:** P1  
**Sprint:** 2 · **Puntos:** 3 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo de soporte, quiero que las solicitudes mantengan un `correlationId` y que el navegador pueda consumir el gateway desde el origen configurado, para facilitar trazabilidad e integración frontend-backend.

**Criterios de aceptación**

1. Si el cliente envía un `correlationId` válido se conserva; si no, se genera uno.
2. El identificador se propaga hacia el servicio destino.
3. Las respuestas permiten correlacionar la solicitud con los logs del backend.
4. CORS permite únicamente los orígenes configurados para el entorno.
5. El encabezado se llama `X-Correlation-Id`.

## HU-404 — Contrato OpenAPI y errores Problem Details

**Orden:** 4  
**Prioridad:** P0  
**Sprint:** 2 · **Puntos:** 5 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como desarrollador del frontend o de un servicio, quiero un contrato REST documentado y errores con estructura uniforme, para integrar sin adivinar formatos.

**Criterios de aceptación**

1. `contracts/api/openapi.yaml` documenta `POST /orders`, `GET /orders/{id}` y `GET /orders/{id}/notifications` con ejemplos de solicitud y respuesta.
2. Documenta `Idempotency-Key`, `X-Correlation-Id` y los códigos `400`, `404` y `409`.
3. Order Service, Notification Service y el gateway devuelven errores en formato RFC 9457 Problem Details con `code` y `correlationId`.
4. El contrato se valida con una herramienta de linting de OpenAPI a elección del equipo.
5. El frontend solo usa operaciones documentadas.
6. El contrato se actualiza en el mismo PR que cambie el API.
