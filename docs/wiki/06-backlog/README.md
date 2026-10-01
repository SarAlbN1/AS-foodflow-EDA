# Backlog

[← Índice de la wiki](../Home.md)

El backlog vive en esta carpeta: **una página por épica** con sus historias de usuario (HU), más la priorización, el plan de sprints y el estado.

| Página | Contenido |
|---|---|
| [Épicas](epicas/) | Las HU con sus criterios de aceptación |
| [Priorización](priorizacion.md) | HU por prioridad P0, P1 y P2 |
| [Plan de sprints](plan-de-sprints.md) | Sprints, puntos, responsables y reparto 60/40 |
| [Estado](estado.md) | Estado vivo de cada HU |
| [Plan de sustentación](sustentacion.md) | Minutaje, reparto por integrante y asistencia |
| [`backlog.tsv`](backlog.tsv) | Datos del backlog en tabla (lo usa `scripts/create-issues.sh`) |

## Épicas

| Épica | Tema | Prioridad |
|---|---|---|
| [EP-00](epicas/EP-00-base-tecnica-y-estandares.md) | Base técnica, contratos, decisiones y estándares | P0 |
| [EP-01](epicas/EP-01-pedidos.md) | Gestión de pedidos | P0 |
| [EP-02](epicas/EP-02-pagos.md) | Procesamiento de pagos | P0 |
| [EP-03](epicas/EP-03-notificaciones.md) | Gestión de notificaciones | P0 |
| [EP-04](epicas/EP-04-gateway-y-contratos.md) | API Gateway y contratos HTTP | P0 |
| [EP-05](epicas/EP-05-frontend.md) | Experiencia web del cliente | P0 / P1 |
| [EP-06](epicas/EP-06-resiliencia-y-calidad.md) | Resiliencia, observabilidad y calidad | P0 / P1 |
| [EP-07](epicas/EP-07-entregables-y-sustentacion.md) | Entregables académicos y sustentación | P0 |

## Convenciones

**Prioridad.**

- **P0 — Must:** imprescindible para demostrar el flujo, la arquitectura y los entregables.
- **P1 — Should:** necesario para robustez, trazabilidad o experiencia de demostración.
- **P2 — Could:** opcional; no entra en los seis sprints y no condiciona la finalización del prototipo.

Dentro de cada épica, **Orden** indica la secuencia recomendada. **Sprint**, **Puntos** y **Responsable** provienen del plan de la página [Plan de sprints](plan-de-sprints.md) (única fuente de verdad).

**Formato de historia.**

```text
Como <actor>
quiero <capacidad>
para <valor/resultado>.
```

**Verificación INVEST.** Todas las historias están delimitadas para ser Independientes, Negociables, Valiosas, Estimables, Pequeñas y Testeables (`INVEST: I✅ N✅ V✅ E✅ S✅ T✅`).

**Cambios respecto a la versión anterior del backlog.** `OrderUpdated` pasa a `OrderStatusChanged`; el pago se determina con `PAY-OK` y `PAY-FAIL`; la API mínima se reduce a tres operaciones (HU-205 y HU-503 pasan a P2); Flyway, Testcontainers y CI pasan a P2; se añaden las historias de ADR, estándares del repositorio, OpenAPI, calidad y entregables académicos.
