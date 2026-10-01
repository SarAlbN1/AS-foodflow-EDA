#!/usr/bin/env bash
# ============================================================================
#  HU-607 — Detiene FoodFlow
# ============================================================================
#  Uso (desde cualquier directorio):
#    bash scripts/down.sh            detiene y elimina los contenedores y la red;
#                                    los datos de las tres bases se conservan
#    bash scripts/down.sh --limpiar  además borra los volúmenes de las bases, para
#                                    recrear el entorno desde cero
#
#  Después de cualquiera de los dos, `bash scripts/up.sh` vuelve a levantarlo todo:
#  las bases recrean su esquema y kafka-init vuelve a declarar los tópicos.
# ============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

COMPOSE_FILE=infrastructure/compose/docker-compose.yml

# Compose necesita las variables para interpretar el archivo, aunque solo vaya a detener.
env_file=.env
if [ ! -f "$env_file" ]; then
  env_file=.env.example
fi

extra=()
case "${1:-}" in
  "") ;;
  --limpiar) extra=(--volumes) ;;
  *) echo "Uso: bash scripts/down.sh [--limpiar]" >&2; exit 2 ;;
esac

# ${extra[@]+...}: con set -u, bash < 4.4 (el 3.2 de macOS) trata un array vacio como no
# definido; esta forma expande a nada en lugar de abortar sin --limpiar.
docker compose --env-file "$env_file" -f "$COMPOSE_FILE" down --remove-orphans ${extra[@]+"${extra[@]}"}

if [ ${#extra[@]} -gt 0 ]; then
  echo "FoodFlow detenido y volúmenes de las bases eliminados."
else
  echo "FoodFlow detenido. Los datos de las bases se conservan (usa --limpiar para borrarlos)."
fi
