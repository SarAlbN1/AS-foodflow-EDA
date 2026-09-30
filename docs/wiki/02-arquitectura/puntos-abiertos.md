# Puntos abiertos y supuestos

[← Índice de la wiki](../Home.md)

| Ref. | Punto | Supuesto por defecto | Acción |
|---|---|---|---|
| A-1 | Las decisiones no indican **dónde** se especifica `PAY-OK` o `PAY-FAIL`. | Campo `paymentToken` en `POST /orders`, transportado en `OrderCreated`. | Confirmar el campo. |
| A-2 | ~~ADR-11 menciona `OrderStatusChanged` como vía de propagación del contacto, pero la regla 10 exige que Notification reaccione al pago de forma independiente.~~ | **CERRADO (2026-09-28).** Notification consume `payments.events`; Payment copia el snapshot desde `OrderCreated`. `OrderStatusChanged` lo transporta para consumidores futuros. | Ninguna. La decisión confirma el abanico desde `payments.events`; se corrigió el informe, no la wiki. |
| A-3 | La API mínima obligatoria no incluye consulta de pagos. | El contrato mínimo del informe no cambia: el pago se sigue observando mediante el estado del pedido. HU-205 y HU-503 añaden la consulta como **opcional**, asignadas a Juan. | Sara aprueba o rechaza el cambio de `CLAUDE.md` §5 en el PR que restaura estas historias. HU-205 no empieza antes. |
| A-4 | Calendario real de sprints y calibración de puntos. | Seis sprints; puntos relativos; el 60/40 se calcula sobre puntos. | Recalibrar tras el Sprint 1. |
| A-5 | ~~Umbrales numéricos de calidad (página [Atributos de calidad verificables](atributos-de-calidad.md)).~~ | **CERRADO (2026-09-30).** Calibrados en HU-608 con mediciones reales sobre el entorno Compose; se conservan los valores propuestos porque los medidos quedan muy por debajo. | Ninguna. Valores, entorno y fecha en [Atributos de calidad medidos](../04-implementacion/pruebas/atributos-de-calidad.md). |
| A-6 | Versiones de Java, Spring Boot, Spring Kafka, Angular, Node, PostgreSQL, Kafka y Nginx. | Sin fijar. | Fijar en `docs/wiki/04-implementacion/versiones.md` antes del Sprint 1. |
| A-7 | Licencia del repositorio. | Sin definir. | Decidir. |
| A-8 | Usuarios de GitHub para asignaciones. | Sin registrar. | Registrar en el Project. |
| A-9 | Criterio de participación en la sustentación (todos participan). | Segmento propio en HU-707. | Confirmar con el docente. |
| A-10 | `api-rest.md` exige `GET /actuator/health` a «cada servicio», pero HU-604 solo cubre los tres servicios Spring Boot. El API Gateway no lo expone. | Resuelto en HU-607: el gateway expone `/actuator/health` (liveness y readiness) con el mismo bloque de propiedades que los servicios, sin consultar a los servicios. | Sara lo confirma al revisar el PR de HU-607. |
| A-11 | El informe nombra `schemaVersion` al campo de versión del envelope (`main.tex`, líneas 435, 464, 656 y 711). | Los contratos versionados (`contracts/events/v1/`), el validador y el código usan `eventVersion`. | Decidir el nombre: ajustar el informe o renombrar la v1 completa. Lo decide Sara tras revisión cruzada. |
| A-12 | El informe pone `orderId` como campo común en la raíz del envelope (`main.tex`, línea 656). | Los contratos usan `aggregateId` (igual al `orderId`, clave de partición según ADR-04) en la raíz y `orderId` dentro del `payload`. | Decidir el nombre. Lo decide Sara tras revisión cruzada. |
| A-13 | El informe llama `paymentTestToken` al token de pago simulado (`main.tex`, línea 938). | La API REST, `OrderCreated`, la columna `payment_token` de Order DB y el código usan `paymentToken`. | Decidir el nombre. Lo decide Sara tras revisión cruzada. |
