# Decisiones (ADR)

[← Índice de la wiki](../Home.md)

## Registro de decisiones (ADR)

Cada decisión tiene su ADR completo (contexto, opciones consideradas, trade-offs y consecuencias) en [`adr/`](adr/README.md). Esta tabla es el resumen; el ADR es el detalle.

| ADR | Decisión | Estado |
|---|---|---|
| [ADR-01](adr/ADR-01-kafka-como-broker.md) | Kafka como broker; los servicios no se invocan entre sí | Aprobado |
| [ADR-02](adr/ADR-02-tres-servicios-de-negocio.md) | Tres servicios de negocio | Aprobado |
| [ADR-03](adr/ADR-03-una-base-postgresql-por-servicio.md) | Una base PostgreSQL por servicio | Aprobado |
| [ADR-04](adr/ADR-04-clave-de-particion-por-orderid.md) | Clave de partición por `orderId` | Aprobado |
| [ADR-05](adr/ADR-05-reintentos-y-dlq.md) | Reintentos y DLQ | Aprobado |
| [ADR-06](adr/ADR-06-validacion-y-eventos-explicitos-de-resultado.md) | Validación y eventos explícitos de resultado | Aprobado |
| [ADR-07](adr/ADR-07-api-gateway-como-entrada-sincrona.md) | API Gateway como entrada síncrona | Aprobado |
| **[ADR-08](adr/ADR-08-sin-transactional-outbox.md)** | **Sin Transactional Outbox: se acepta el riesgo de persistir en PostgreSQL sin llegar a Kafka.** Recuperabilidad clasificada como **Parcial**. | Aprobado |
| **[ADR-09](adr/ADR-09-idempotencia-con-eventid-y-processed-events.md)** | **Idempotencia:** `eventId` único por evento y tabla `processed_events` en cada servicio, escrita en la misma transacción local que el efecto de negocio. | Aprobado |
| **[ADR-10](adr/ADR-10-pago-determinista.md)** | **Pago determinista:** `PAY-OK` y `PAY-FAIL`. | Aprobado |
| **[ADR-11](adr/ADR-11-snapshot-de-contacto-y-canal.md)** | **Snapshot de contacto y canal** capturado en el pedido y propagado por eventos. | Aprobado |
| [ADR-12](adr/ADR-12-contrato-rest-openapi-problem-details-idempotency-key.md) | Contrato REST: OpenAPI, Problem Details e `Idempotency-Key` | Propuesto (numeración por confirmar) |

Consecuencia de ADR-08: el replay de Kafka recupera eventos **publicados**, no los que nunca llegaron al broker. Este riesgo debe estar documentado en el ADR, en el README y en las lecciones aprendidas.
