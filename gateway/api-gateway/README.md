# gateway/api-gateway — API Gateway

> **Estado:** vacío. Solo existe la estructura inicial; la funcionalidad la construyen las historias indicadas.

**Responsabilidad:** Punto de entrada REST. Enruta hacia el servicio propietario sin aplicar reglas de negocio.

**Historias que lo construyen:** HU-001 (esqueleto), HU-401, HU-402, HU-403

**Reglas que aplican:** Reenvía `Idempotency-Key`; genera o propaga `X-Correlation-Id`; CORS solo para orígenes configurados.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
