# services/notification-service — Notification Service

> **Estado:** vacío. Solo existe la estructura inicial; la funcionalidad la construyen las historias indicadas.

**Responsabilidad:** Consume el resultado del pago, registra la notificación, la envía al proveedor y publica `NotificationSent` o `NotificationFailed`. Único propietario de Notification DB y único que llama al proveedor.

**Historias que lo construyen:** HU-001, HU-301 a HU-305

**Reglas que aplican:** ADR-09, ADR-11. Un fallo del proveedor deja la notificación en `FALLIDA`, no en DLQ.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
