# Decisiones (ADR)

[← Índice de la wiki](../Home.md)

## Registro de decisiones (ADR)

| ADR | Decisión | Estado |
|---|---|---|
| ADR-01 | Kafka como broker; los servicios no se invocan entre sí | Aprobado |
| ADR-02 | Tres servicios de negocio | Aprobado |
| ADR-03 | Una base PostgreSQL por servicio | Aprobado |
| ADR-04 | Clave de partición por `orderId` | Aprobado |
| ADR-05 | Reintentos y DLQ | Aprobado |
| ADR-06 | Validación y eventos explícitos de resultado | Aprobado |
| ADR-07 | API Gateway como entrada síncrona | Aprobado |
| **ADR-08** | **Sin Transactional Outbox: se acepta el riesgo de persistir en PostgreSQL sin llegar a Kafka.** Recuperabilidad clasificada como **Parcial**. | Aprobado |
| **ADR-09** | **Idempotencia:** `eventId` único por evento y tabla `processed_events` en cada servicio, escrita en la misma transacción local que el efecto de negocio. | Aprobado |
| **ADR-10** | **Pago determinista:** `PAY-OK` y `PAY-FAIL`. | Aprobado |
| **ADR-11** | **Snapshot de contacto y canal** capturado en el pedido y propagado por eventos. | Aprobado |
| ADR-12 | Contrato REST: OpenAPI, Problem Details e `Idempotency-Key` | Propuesto (numeración por confirmar) |

Consecuencia de ADR-08: el replay de Kafka recupera eventos **publicados**, no los que nunca llegaron al broker. Este riesgo debe estar documentado en el ADR, en el README y en las lecciones aprendidas.
