# FoodFlow EDA — Wiki del proyecto

> El **diseño** lo fija el informe técnico ([`docs/informe/main.tex`](../informe/README.md)). Esta wiki **lo describe, lo explica y deriva de él** los procesos de Git, los contratos operativos y el backlog. Todo cambio a estas páginas se propone mediante pull request, igual que el código.
> Los asistentes de IA no leen esta wiki completa: parten de [`CLAUDE.md`](../../CLAUDE.md), que la referencia.

**FoodFlow en una frase:** recibe comandos HTTP en el borde, mantiene el estado local de cada dominio en una PostgreSQL exclusiva y coordina Pedido → Pago → Notificación mediante eventos Kafka coreografiados, idempotentes y trazables.

## Cómo está organizada

| Carpeta | Qué contiene | Empieza por |
|---|---|---|
| [`01-producto/`](01-producto/vision-y-alcance.md) | Qué se construye, qué no, stack y criterios de éxito | [Visión y alcance](01-producto/vision-y-alcance.md) |
| [`02-arquitectura/`](02-arquitectura/reglas-arquitectonicas.md) | Estilo, reglas, decisiones (ADR), calidad, trazabilidad, diagramas | [Reglas arquitectónicas](02-arquitectura/reglas-arquitectonicas.md) |
| [`03-contratos/`](03-contratos/api-rest.md) | API REST, eventos Kafka, proveedor de notificaciones y persistencia | [API REST](03-contratos/api-rest.md) |
| [`04-implementacion/`](04-implementacion/estructura-del-repositorio.md) | Estructura del repo, convenciones, versiones, runbook de la demo | [Estructura del repositorio](04-implementacion/estructura-del-repositorio.md) |
| [`05-proceso/`](05-proceso/README.md) | Políticas de Git, etiquetas, PR, releases, DoR/DoD y trabajo con IA | [Proceso](05-proceso/README.md) |
| [`06-backlog/`](06-backlog/README.md) | Historias de usuario por épica, priorización, plan de sprints y estado | [Backlog](06-backlog/README.md) |

## Jerarquía de fuentes

**El informe técnico es la fuente de verdad del diseño.** Esta wiki es su descripción operativa: traduce lo que el informe decide a reglas, contratos, procesos e historias ejecutables. Si dos fuentes se contradicen, prevalece la de menor número:

1. El informe técnico ([`docs/informe/main.tex`](../informe/README.md)) y sus [diagramas](02-arquitectura/diagramas/README.md).
2. Las [reglas arquitectónicas](02-arquitectura/reglas-arquitectonicas.md) y las [decisiones ADR](02-arquitectura/decisiones-adr.md) aprobadas, que desarrollan lo que fija el informe.
3. Los contratos versionados en [`contracts/`](../../contracts/api/README.md) (OpenAPI y JSON Schema).
4. El resto de esta wiki y el [backlog](06-backlog/README.md).

`main.tex` lo edita únicamente Sara: una corrección al informe **se propone, no se aplica**.

Una contradicción no se resuelve en silencio. Si la wiki se desvía del informe, se corrige la wiki; si parece que el equivocado es el informe, se documenta en el PR o en un comentario de la HU para revisión cruzada antes de que Sara decida.

## Qué leer según la tarea

| Tarea | Lee |
|---|---|
| Implementar una HU | Su [épica](06-backlog/README.md) y las [reglas arquitectónicas](02-arquitectura/reglas-arquitectonicas.md) |
| Endpoint REST | [API REST](03-contratos/api-rest.md) |
| Productor o consumidor Kafka | [Eventos](03-contratos/eventos.md) y [comportamiento del flujo](02-arquitectura/comportamiento-del-flujo.md) |
| Base de datos | [Persistencia](03-contratos/persistencia.md) |
| Rama, commit, issue, etiqueta, PR, release | [Proceso](05-proceso/README.md) |
| Pruebas y calidad | [Atributos de calidad](02-arquitectura/atributos-de-calidad.md) y [DoR y DoD](05-proceso/dor-y-dod.md) |
| Saber qué está hecho | [Estado](06-backlog/estado.md) |
| Duda de alcance | [Visión y alcance](01-producto/vision-y-alcance.md) y [puntos abiertos](02-arquitectura/puntos-abiertos.md) |
| El informe y la wiki no coinciden | El PR o la HU correspondiente; revisión cruzada antes del cambio |
| Ver un diagrama | [Diagramas](02-arquitectura/diagramas/README.md) |

## Convenciones de esta wiki

- Una página, un tema. Se enlaza en lugar de duplicar.
- Las decisiones de diseño se registran como ADR; una decisión que cambia genera un ADR nuevo que reemplaza al anterior.
- Los elementos marcados **Aprobado** son vinculantes; los **Propuesto** son la recomendación por defecto hasta que el equipo los apruebe.
- El estado vivo del proyecto está en [Estado](06-backlog/estado.md).
