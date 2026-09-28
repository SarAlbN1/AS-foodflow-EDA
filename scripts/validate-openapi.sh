#!/usr/bin/env bash
# Valida contracts/api/openapi.yaml: estructura OpenAPI 3.1 y ejemplos contra sus esquemas (HU-404).
#
# Reutiliza el entorno virtual aislado .venv-contracts (ignorado por Git) que ya usa
# scripts/validate-events.sh, e instala 'openapi-spec-validator'. No añade dependencias
# al repositorio.
#
# Uso: bash scripts/validate-openapi.sh
set -euo pipefail
cd "$(dirname "$0")/.."

VENV=".venv-contracts"
PY="$VENV/bin/python"

if [ ! -x "$PY" ]; then
  echo "Preparando el entorno de validación en $VENV ..."
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install --quiet --upgrade pip
fi

if ! "$PY" -c "import openapi_spec_validator, jsonschema, yaml" 2>/dev/null; then
  "$VENV/bin/pip" install --quiet "openapi-spec-validator>=0.7" "jsonschema[format]>=4.23" "PyYAML>=6"
fi

exec "$PY" scripts/validate_openapi.py
