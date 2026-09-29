# Registro de decisiones arquitectónicas (ADR)

[← Índice de la wiki](../../Home.md) · [Decisiones (resumen)](../decisiones-adr.md)

Cada decisión de arquitectura vive en un archivo `ADR-NN-<slug>.md` en esta carpeta. Un cambio de decisión **no edita** el ADR anterior: crea uno nuevo que lo reemplaza (estado `Superseded`).

> **Fechas.** La wiki no registra la fecha original de cada decisión, así que los ADR llevan la fecha en que se registraron en el repositorio (HU-005, 2026-09-27) y lo indican expresamente. No se inventan fechas retroactivas.

## Índice

| ADR | Decisión | Estado | Archivo |
|---|---|---|---|
| ADR-01 | Kafka como broker; los servicios no se invocan entre sí | Aprobado | [ADR-01](ADR-01-kafka-como-broker.md) |
| ADR-02 | Tres servicios de negocio | Aprobado | [ADR-02](ADR-02-tres-servicios-de-negocio.md) |
| ADR-03 | Una base PostgreSQL por servicio | Aprobado | [ADR-03](ADR-03-una-base-postgresql-por-servicio.md) |
| ADR-04 | Clave de partición por `orderId` | Aprobado | [ADR-04](ADR-04-clave-de-particion-por-orderid.md) |
| ADR-05 | Reintentos controlados y DLQ | Aprobado | [ADR-05](ADR-05-reintentos-y-dlq.md) |
| ADR-06 | Validación en la entrada y eventos explícitos de resultado | Aprobado | [ADR-06](ADR-06-validacion-y-eventos-explicitos-de-resultado.md) |
| ADR-07 | API Gateway como entrada síncrona controlada | Aprobado | [ADR-07](ADR-07-api-gateway-como-entrada-sincrona.md) |
| ADR-08 | Sin Transactional Outbox; riesgo de escritura dual aceptado | Aprobado | [ADR-08](ADR-08-sin-transactional-outbox.md) |
| ADR-09 | Idempotencia con `eventId` y `processed_events` | Aprobado | [ADR-09](ADR-09-idempotencia-con-eventid-y-processed-events.md) |
| ADR-10 | Pago determinista `PAY-OK` / `PAY-FAIL` | Aprobado | [ADR-10](ADR-10-pago-determinista.md) |
| ADR-11 | Snapshot de contacto y canal de notificación | Aprobado | [ADR-11](ADR-11-snapshot-de-contacto-y-canal.md) |
| ADR-12 | Contrato REST: OpenAPI, Problem Details, `Idempotency-Key` | **Propuesto** | [ADR-12](ADR-12-contrato-rest-openapi-problem-details-idempotency-key.md) |

**ADR-12 sigue en `Propuesto`** porque su numeración está pendiente de confirmar por el equipo; el contenido de la decisión sí está acordado y ya se refleja en la página [API REST](../../03-contratos/api-rest.md).

Ningún ADR está hoy en estado `Deprecated` ni `Superseded`.

## Trazabilidad con la matriz de tácticas

Cada ADR cierra con la fila que le corresponde en la **matriz de tácticas** del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*):

| ADR | Táctica | Estilo / tecnología | Estado en la matriz |
|---|---|---|---|
| ADR-01 | Desacoplar en el tiempo | EDA + Apache Kafka | Implementar |
| ADR-02 | Separación de responsabilidades | Spring Boot + tres servicios | Implementar |
| ADR-03 | Persistencia local por servicio | PostgreSQL | Implementar |
| ADR-04 | Orden del flujo | Kafka + `orderId` | Implementar |
| ADR-05 | Tolerancia a errores de consumo | Kafka + retry + DLQ | Implementar |
| ADR-06 | Validación y resultados explícitos | Spring Boot + Bean Validation | Implementar |
| ADR-07 | Entrada síncrona controlada | REST/JSON + API Gateway | Implementar |
| ADR-08 | Riesgo de escritura dual | PostgreSQL + Kafka sin Outbox | **Diseñado / riesgo aceptado** |
| ADR-09 | Idempotencia de consumidores | `eventId` + PostgreSQL | Implementar |
| ADR-10 | Pago reproducible | Payment Service + simulador | Implementar |
| ADR-11 | Datos de contacto | Evento versionado + snapshot | Implementar |
| ADR-12 | Contrato HTTP e idempotencia de entrada | RFC 9457 + OpenAPI + `Idempotency-Key` | Implementar |

## Cómo cambiar una decisión

1. **No se edita** el ADR vigente para cambiar la decisión. Corregir una errata o añadir un enlace sí; cambiar la decisión, no.
2. Se crea un ADR nuevo con el número siguiente, que explica el contexto que cambió y por qué la decisión anterior ya no sirve.
3. El ADR nuevo indica en su cabecera qué ADR reemplaza.
4. El ADR anterior pasa a **`Superseded` por ADR-NN**, con la referencia al nuevo. Se conserva: el registro histórico de por qué se decidió algo es parte del valor del ADR.
5. Se actualiza este índice, la tabla de [Decisiones (resumen)](../decisiones-adr.md) y, si la decisión afecta a una táctica, la matriz del informe técnico.
6. Todo eso va en un PR con la etiqueta `adr`, revisado por la otra persona del equipo.

Ejemplo de cabecera de un ADR reemplazado:

```markdown
**Estado:** Superseded por [ADR-15](ADR-15-adoptar-transactional-outbox.md)
```

## Relación entre los ADR

- [ADR-01](ADR-01-kafka-como-broker.md) fija el estilo; [ADR-02](ADR-02-tres-servicios-de-negocio.md) y [ADR-03](ADR-03-una-base-postgresql-por-servicio.md) fijan los límites de servicio y de datos.
- [ADR-04](ADR-04-clave-de-particion-por-orderid.md), [ADR-05](ADR-05-reintentos-y-dlq.md) y [ADR-09](ADR-09-idempotencia-con-eventid-y-processed-events.md) son las tres tácticas que hacen fiable el consumo de eventos: orden, tratamiento de errores e idempotencia.
- [ADR-08](ADR-08-sin-transactional-outbox.md) es la limitación aceptada del prototipo, y es la razón por la que la recuperabilidad es **Parcial**. [ADR-09](ADR-09-idempotencia-con-eventid-y-processed-events.md) es lo que hace que el *replay* —la recuperabilidad que sí existe— sea seguro.
- [ADR-07](ADR-07-api-gateway-como-entrada-sincrona.md) y [ADR-12](ADR-12-contrato-rest-openapi-problem-details-idempotency-key.md) definen el borde síncrono; [ADR-06](ADR-06-validacion-y-eventos-explicitos-de-resultado.md) conecta la validación del borde con la forma de los eventos internos.
- [ADR-11](ADR-11-snapshot-de-contacto-y-canal.md) resuelve el supuesto A-2 y es lo que permite que Notification Service sea autónomo sin romper [ADR-03](ADR-03-una-base-postgresql-por-servicio.md).
