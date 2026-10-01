# Notas de release — v1.0.0

[← Proceso](README.md) · [← Índice de la wiki](../Home.md)

Notas del tag `v1.0.0` (HU-701), con el contenido que exige la página [Pull requests, protección y releases](pull-requests-y-releases.md): HU incluidas, cómo ejecutar, escenarios de demostración, limitaciones conocidas y verificación de los [criterios de éxito](../01-producto/criterios-de-exito.md).

> **El tag se crea cuando `main` queda cerrado.** Es el último paso del proyecto: etiquetar antes dejaría fuera lo que todavía está en revisión. El estado vivo de cada historia está en [`estado.md`](../06-backlog/estado.md), y lo que esté abierto al momento de etiquetar se anota aquí entonces.

## Qué es FoodFlow

Prototipo académico de una plataforma de pedidos que demuestra una **arquitectura orientada a eventos con coreografía**: el cliente solo crea el pedido, y a partir de ahí cada servicio reacciona a hechos publicados en Kafka. No hay orquestador y los servicios no se llaman entre sí para coordinarse.

## Cómo ejecutarlo

```bash
bash scripts/up.sh          # frontend, gateway, 3 servicios, Kafka, 3 bases y el proveedor simulado
bash scripts/smoke-test.sh  # recorre PAY-OK y PAY-FAIL por el gateway
bash scripts/down.sh        # detiene; --limpiar borra también los volúmenes
```

El frontend queda en `http://localhost:4200` y el gateway en `http://localhost:8080`.

## Escenarios de demostración

| Token | Pedido | Pago | Notificación |
|---|---|---|---|
| `PAY-OK` | `PAGADO` | `APROBADO` | `ENVIADA` |
| `PAY-FAIL` | `PAGO_RECHAZADO` | `RECHAZADO` / `PAGO_RECHAZADO_POR_TOKEN` | `ENVIADA` (informa del rechazo) |

Los dos son reproducibles y no requieren tocar la base a mano. Para ver la DLQ y los modos de fallo del proveedor (`*@fail.test`, `*@flaky.test`, `*@slow.test`), ver [atributos de calidad](../02-arquitectura/atributos-de-calidad.md).

**Lo que más conviene mostrar:** un pedido genera ocho hechos repartidos en tres tópicos, y **un solo `correlationId` los atraviesa todos**. Es la coreografía entera en una traza.

## Qué incluye esta versión

Las seis épicas del backlog, completas en lo imprescindible:

| Épica | Qué quedó funcionando |
|---|---|
| EP-00 base técnica | Estructura, contratos de eventos v1, tópicos con DLQ, ADR-01 a ADR-13 y verificación de arquitectura con ArchUnit |
| EP-01 pedidos | `POST /orders` con `Idempotency-Key`, consulta del pedido, publicación de `OrderCreated` y `OrderStatusChanged` |
| EP-02 pagos | Pago determinista por token, `PaymentApproved` / `PaymentRejected` y consulta del pago (`GET /orders/{id}/payment`) |
| EP-03 notificaciones | Notificación por cada resultado de pago, envío al proveedor simulado y `NotificationSent` / `NotificationFailed` |
| EP-04 borde | API Gateway, OpenAPI 3.1 y errores en RFC 9457 |
| EP-05 frontend | Crear el pedido, seguir su estado y ver sus notificaciones |
| EP-06 resiliencia y calidad | Consumidores idempotentes, reintentos y DLQ, logs correlacionados, *health checks*, pruebas de integración sobre Kafka real y medición de los atributos de calidad |

Añadidos opcionales que también entraron: esquemas versionados con **Flyway** (ADR-13), pruebas de integración con **Testcontainers** (`-Ptestcontainers`) y un flujo de **CI** que repite en cada PR lo que se ejecuta en local.

## Verificación de los criterios de éxito

| # | Criterio | Dónde se verifica |
|---|---|---|
| 1–4 | El cliente crea el pedido, entra por el gateway con `Idempotency-Key`, se persiste y se publica `OrderCreated` | HU-101, HU-103, HU-107, HU-401, HU-501 |
| 5–7 | Payment consume el evento, resuelve el pago de forma reproducible y publica su resultado | HU-201 a HU-204 · `OrderCreatedFlowIntegrationTests` |
| 8 | Order consume el resultado, actualiza y publica `OrderStatusChanged` | HU-104 a HU-106 · `PaymentResultFlowIntegrationTests` |
| 9–10 | Notification consume el mismo resultado de forma independiente, persiste, invoca al proveedor y publica | HU-301 a HU-304 · `PaymentResultFlowIntegrationTests` |
| 11 | El frontend muestra el resultado eventual y las notificaciones | HU-502, HU-504, HU-505 |
| 12 | Los duplicados no duplican efectos | HU-601 · tres pruebas de doble entrega contra Kafka real |
| 13 | Reintentos y DLQ | HU-602 · `DeadLetterQueueIntegrationTests` |
| 14 | Un `correlationId` rastrea el flujo en los tres servicios | HU-603 |
| 15 | OpenAPI y Problem Details | HU-404 · `validate-openapi.sh` |
| 16 | Atributos de calidad con pruebas reproducibles | HU-608 · `verify-quality-attributes.sh` |
| 17 | Todo contenerizado y reproducible | HU-606, HU-607 · `up.sh` + `smoke-test.sh` |
| 18 | El código conserva la separación de los diagramas | HU-006 · `verify-architecture.sh` (ArchUnit) |
| 19 | Tag y release `v1.0.0`, repositorio público | Esta release |

## Limitaciones conocidas

La lista completa y argumentada está en el [README](../../../README.md#limitaciones-conocidas). La que más importa para leer el prototipo con honestidad:

**Sin Transactional Outbox (ADR-08).** Un pedido puede quedar persistido sin que su evento llegue a Kafka. Se registra un `ERROR` y nadie lo reconcilia: la **recuperabilidad es Parcial**, y así está declarado en el informe técnico. Se aceptó a propósito para no introducir la complejidad de un Outbox en un prototipo académico.

## Cómo se crea el tag

```bash
git tag -a v1.0.0 -m "FoodFlow v1.0.0: prototipo EDA completo"
git push origin v1.0.0
```

Y la release en GitHub con estas notas. Al cierre de cada sprint se recomienda un pre-release `v0.<N>.0`.
