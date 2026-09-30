# Puntos abiertos y supuestos

[← Índice de la wiki](../Home.md)

| Ref. | Punto | Supuesto por defecto | Acción |
|---|---|---|---|
| A-1 | Las decisiones no indican **dónde** se especifica `PAY-OK` o `PAY-FAIL`. | Campo `paymentToken` en `POST /orders`, transportado en `OrderCreated`. | Confirmar el campo. |
| A-2 | ~~ADR-11 menciona `OrderStatusChanged` como vía de propagación del contacto, pero la regla 10 exige que Notification reaccione al pago de forma independiente.~~ | **CERRADO (2026-09-28).** Notification consume `payments.events`; Payment copia el snapshot desde `OrderCreated`. `OrderStatusChanged` lo transporta para consumidores futuros. | Ninguna. La decisión confirma el abanico desde `payments.events`; se corrigió el informe, no la wiki. |
| A-3 | La API mínima obligatoria no incluye consulta de pagos. | HU-205 y HU-503 pasan a P2 (fuera del plan). El pago se observa mediante el estado del pedido. | Confirmar. |
| A-4 | Calendario real de sprints y calibración de puntos. | Seis sprints; puntos relativos; el 60/40 se calcula sobre puntos. | Recalibrar tras el Sprint 1. |
| A-5 | ~~Umbrales numéricos de calidad (página [Atributos de calidad verificables](atributos-de-calidad.md)).~~ | **CERRADO (2026-09-30).** Calibrados en HU-608 con mediciones reales sobre el entorno Compose; se conservan los valores propuestos porque los medidos quedan muy por debajo. | Ninguna. Valores, entorno y fecha en [Atributos de calidad medidos](../04-implementacion/pruebas/atributos-de-calidad.md). |
| A-6 | Versiones de Java, Spring Boot, Spring Kafka, Angular, Node, PostgreSQL, Kafka y Nginx. | Sin fijar. | Fijar en `docs/wiki/04-implementacion/versiones.md` antes del Sprint 1. |
| A-7 | Licencia del repositorio. | Sin definir. | Decidir. |
| A-8 | Usuarios de GitHub para asignaciones. | Sin registrar. | Registrar en el Project. |
| A-9 | Criterio de participación en la sustentación (todos participan). | Segmento propio en HU-707. | Confirmar con el docente. |
| A-10 | `api-rest.md` exige `GET /actuator/health` a «cada servicio», pero HU-604 solo cubre los tres servicios Spring Boot. El API Gateway no lo expone. | Resuelto en HU-607: el gateway expone `/actuator/health` (liveness y readiness) con el mismo bloque de propiedades que los servicios, sin consultar a los servicios. | Sara lo confirma al revisar el PR de HU-607. |
