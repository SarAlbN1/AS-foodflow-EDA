# Issues, etiquetas y milestones

[← Índice de la wiki](../Home.md)

## Issues y trazabilidad con el backlog

- Un issue de GitHub por HU, con título `HU-101 — Crear un pedido válido`.
- El cuerpo usa la plantilla `story.yml`: historia, criterios de aceptación, contratos afectados y servicio propietario.
- Todas las HU del backlog tienen su issue desde el inicio del proyecto; no se crean sprint a sprint.
- Cada issue lleva **obligatoriamente**: 1 etiqueta de tipo, al menos 1 de área, 1 de prioridad, 1 de épica, un *milestone* (`Sprint N`) y un responsable. Excepción: las HU opcionales (P2) sin sprint asignado no llevan milestone ni responsable hasta que se planifiquen.
- Los puntos de historia se registran como campo del GitHub Project (`Points`), no como etiquetas.
- El PR referencia el issue con `Closes #<n>`.

## Etiquetas (labels)

GitHub ya trae 10 etiquetas por defecto en este repositorio. **No se duplican**: se reutilizan como etiquetas de tipo y se añaden solo las que aportan información nueva.

**Etiquetas existentes (se conservan sin modificar):**

| Etiqueta | Uso en FoodFlow |
|---|---|
| `enhancement` | Toda HU funcional o enabler (tipo por defecto de una historia) |
| `bug` | Defecto sobre algo ya terminado |
| `documentation` | Documento técnico, README, ADR, diagramas |
| `question` | Duda pendiente de respuesta del equipo |
| `accessibility` | Requisito o defecto de accesibilidad de la interfaz |
| `duplicate`, `invalid`, `wontfix` | Cierre de issues sin trabajo asociado |
| `good first issue`, `help wanted` | No aplican a un equipo de dos personas; se recomienda eliminarlas (decisión opcional) |

**Etiquetas nuevas propuestas:**

| Familia | Etiquetas | Color | Uso |
|---|---|---|---|
| Tipo adicional | `chore`, `adr` | `#bfd4f2`, `#0e8a16` | Mantenimiento sin funcionalidad; decisión arquitectónica |
| Área | `area:frontend`, `area:gateway`, `area:order-service`, `area:payment-service`, `area:notification-service`, `area:contracts`, `area:infra`, `area:mocks`, `area:docs`, `area:repo` | `#1d76db` | Componente afectado (al menos una por issue) |
| Prioridad | `priority:P0`, `priority:P1`, `priority:P2` | `#b60205`, `#d93f0b`, `#fbca04` | Coincide con la prioridad de la HU |
| Épica | `epic:EP-00` a `epic:EP-07` | `#5319e7` | Épica a la que pertenece la HU |
| Estado excepcional | `status:blocked`, `status:needs-decision` | `#24292f`, `#e99695` | El flujo normal (Todo, En curso, En revisión, Hecho) se maneja con el tablero del Project |
| Transparencia | `ai-assisted` | `#c2e0c6` | PR o issue con contribución sustancial de un asistente de IA |

Convención de color (sistema de diseño de etiquetas): un color por familia, para que la familia se reconozca de un vistazo; la prioridad usa la escala rojo, naranja, amarillo.

`scripts/setup-labels.sh` crea o actualiza las etiquetas de forma idempotente con GitHub CLI:

```bash
#!/usr/bin/env bash
set -euo pipefail
label() { gh label create "$1" --color "$2" --description "$3" --force; }

label "chore"                       "bfd4f2" "Mantenimiento sin cambio funcional"
label "adr"                         "0e8a16" "Decisión arquitectónica (ADR)"
for a in frontend gateway order-service payment-service notification-service contracts infra mocks docs repo; do
  label "area:$a" "1d76db" "Componente: $a"
done
label "priority:P0" "b60205" "Imprescindible"
label "priority:P1" "d93f0b" "Importante"
label "priority:P2" "fbca04" "Opcional"
for e in 00 01 02 03 04 05 06 07; do
  label "epic:EP-$e" "5319e7" "Épica EP-$e"
done
label "status:blocked"        "24292f" "Bloqueado por una dependencia"
label "status:needs-decision" "e99695" "Requiere decisión del equipo"
label "ai-assisted"           "c2e0c6" "Contribución sustancial de un asistente de IA"
```

## Milestones y tablero

- Un *milestone* por sprint: `Sprint 1` a `Sprint 6`.
- Un GitHub Project ([FoodFlowEDA](https://github.com/users/SarAlbN1/projects/7)) con columnas `Todo`, `En curso`, `En revisión`, `Hecho` y campos `Points`, `Sprint` y `Responsable`. Todas las HU están en él; una HU nueva entra en `Todo`.
- `docs/wiki/06-backlog/estado.md` refleja el estado en el repositorio para que un asistente sin acceso al tablero lo consulte.
