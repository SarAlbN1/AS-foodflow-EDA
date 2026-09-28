# services/payment-service — Payment Service

> **Estado:** vacío. Solo existe la estructura inicial; la funcionalidad la construyen las historias indicadas.

**Responsabilidad:** Consume `OrderCreated`, decide el pago de forma determinista (`PAY-OK` / `PAY-FAIL`) y publica `PaymentApproved` o `PaymentRejected`. Único propietario de Payment DB.

**Historias que lo construyen:** HU-001, HU-201 a HU-204

**Reglas que aplican:** ADR-09, ADR-10. Ignora eventos de `orders.events` distintos de `OrderCreated`.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
