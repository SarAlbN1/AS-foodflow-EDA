#!/usr/bin/env bash
# Valida los ejemplos de eventos contra los esquemas JSON de contracts/events/v1 (HU-003).
#
# Prepara un entorno virtual aislado en .venv-contracts (ignorado por Git) e instala
# 'jsonschema'. No añade dependencias al repositorio ni código Java compartido.
#
# Uso: bash scripts/validate-events.sh
set -euo pipefail
cd "$(dirname "$0")/.."

VENV=".venv-contracts"
PY="$VENV/bin/python"

if [ ! -x "$PY" ]; then
  echo "Preparando el entorno de validación en $VENV ..."
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install --quiet --upgrade pip
  "$VENV/bin/pip" install --quiet "jsonschema[format]>=4.23"
fi

if ! "$PY" -c "import jsonschema, referencing" 2>/dev/null; then
  "$VENV/bin/pip" install --quiet "jsonschema[format]>=4.23"
fi

exec "$PY" scripts/validate_events.py
