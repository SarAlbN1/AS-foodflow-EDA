# Proceso de trabajo

[← Índice de la wiki](../Home.md)

Estas páginas son **normativas** para personas y asistentes de IA. Su objetivo es que el historial, las ramas, las etiquetas y las entregas sean predecibles y verificables.

| Página | Tema |
|---|---|
| [Git: ramas y commits](git-ramas-y-commits.md) | Modelo de ramas y formato de commits |
| [Issues, etiquetas y milestones](issues-etiquetas-y-milestones.md) | Una HU = un issue; taxonomía de etiquetas; sprints como milestones |
| [Pull requests y releases](pull-requests-y-releases.md) | Plantilla de PR, protección de `main`, tags y releases |
| [Estándares de documentación](documentacion.md) | README, ADR, OpenAPI, runbook |
| [DoR y DoD](dor-y-dod.md) | Cuándo una HU está lista y cuándo está terminada |
| [Trabajo con asistentes de IA](trabajo-con-ia.md) | Algoritmo, prohibiciones, reporte y plantilla de sesión |
| [Bootstrap del repositorio](bootstrap-repositorio.md) | Cómo se creó y publicó el repositorio (runbook para Claude Code) |

## Resumen en cinco líneas

1. Una HU = un issue = una rama = un PR.
2. Rama `<tipo>/<HU-###>-<slug>`; nunca commits directos a `main`.
3. Commit `<tipo>(<ámbito>): <descripción en español> [HU-###]`.
4. PR con plantilla, revisado por la otra persona, integrado con squash.
5. Una HU no está terminada hasta verificar todos sus criterios de aceptación.
