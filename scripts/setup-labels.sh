#!/usr/bin/env bash
# Crea o actualiza las etiquetas del proyecto (idempotente). No toca las etiquetas por defecto de GitHub.
# Uso: bash scripts/setup-labels.sh        (DRY_RUN=1 para simular)
set -euo pipefail
cd "$(dirname "$0")/.."

run() { if [ "${DRY_RUN:-0}" = "1" ]; then printf '[dry-run] '; printf '%q ' "$@"; echo; else "$@"; fi; }
label() { run gh label create "$1" --color "$2" --description "$3" --force; }

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

echo "Etiquetas listas."
