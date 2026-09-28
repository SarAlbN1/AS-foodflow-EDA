# services/order-service — Order Service

> **Estado:** vacío. Solo existe la estructura inicial; la funcionalidad la construyen las historias indicadas.

**Responsabilidad:** Crea y consulta pedidos; publica `OrderCreated` y `OrderStatusChanged`; consume `PaymentApproved` y `PaymentRejected`. Único propietario de Order DB.

**Historias que lo construyen:** HU-001, HU-101 a HU-107

**Reglas que aplican:** ADR-08, ADR-09, ADR-11. Sin llamadas REST a otros servicios.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
