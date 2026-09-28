# Registro de decisiones arquitectónicas (ADR)

[← Índice de la wiki](../../Home.md) · [Decisiones (resumen)](../decisiones-adr.md) · [Plantilla](plantilla.md)

Cada decisión de arquitectura vive en un archivo `ADR-NN-<slug>.md` en esta carpeta, con la [plantilla](plantilla.md). Un cambio de decisión **no edita** el ADR anterior: crea uno nuevo que lo reemplaza (estado `Superseded`).

> Los archivos individuales los crea la HU-005. Hasta entonces, el resumen vigente está en [Decisiones (ADR)](../decisiones-adr.md).

| ADR | Decisión | Estado | Archivo |
|---|---|---|---|
| ADR-01 | Kafka como broker | Aprobado | _HU-005_ |
| ADR-02 | Tres servicios de negocio | Aprobado | _HU-005_ |
| ADR-03 | Una base PostgreSQL por servicio | Aprobado | _HU-005_ |
| ADR-04 | Clave de partición por `orderId` | Aprobado | _HU-005_ |
| ADR-05 | Reintentos y DLQ | Aprobado | _HU-005_ |
| ADR-06 | Validación y eventos explícitos de resultado | Aprobado | _HU-005_ |
| ADR-07 | API Gateway como entrada síncrona | Aprobado | _HU-005_ |
| ADR-08 | Sin Transactional Outbox; riesgo aceptado | Aprobado | _HU-005_ |
| ADR-09 | Idempotencia con `eventId` y `processed_events` | Aprobado | _HU-005_ |
| ADR-10 | Pago determinista `PAY-OK` / `PAY-FAIL` | Aprobado | _HU-005_ |
| ADR-11 | Snapshot de contacto y canal | Aprobado | _HU-005_ |
| ADR-12 | Contrato REST: OpenAPI, Problem Details, `Idempotency-Key` | Propuesto | _HU-005_ |
