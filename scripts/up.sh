#!/usr/bin/env bash
# ============================================================================
#  HU-607 — Levanta FoodFlow completo con un solo comando
# ============================================================================
#  Frontend, API Gateway, los tres servicios, Kafka (con sus tópicos), las tres
#  PostgreSQL y el proveedor de notificaciones simulado, todo en Docker Compose.
#  Funciona desde un clon limpio: si no existe `.env`, lo crea desde `.env.example`.
#
#  Uso (desde cualquier directorio):
#    bash scripts/up.sh              construye las imágenes que cambiaron y levanta todo
#    bash scripts/up.sh --sin-build  levanta con las imágenes ya construidas
#
#  Termina cuando todos los contenedores están `healthy` (o `exited (0)` en el caso
#  de kafka-init). Si alguno no llega a estarlo en ${UP_TIMEOUT:-300} s, falla.
#  Detener: bash scripts/down.sh · Probar: bash scripts/smoke-test.sh
# ============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

COMPOSE_FILE=infrastructure/compose/docker-compose.yml
UP_TIMEOUT="${UP_TIMEOUT:-300}"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: no se encontró 'docker' en el PATH. Instala Docker con Compose v2." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker no responde. Arranca Docker Desktop (o el daemon) y reintenta." >&2
  exit 1
fi

if [ ! -f .env ]; then
  cp .env.example .env
  echo "AVISO: no había .env; se creó desde .env.example con las contraseñas de ejemplo."
  echo "       Sirve para la demo local; cámbialas en .env si el entorno no es desechable."
fi

build=(--build)
if [ "${1:-}" = "--sin-build" ]; then
  build=()
fi

echo "Levantando FoodFlow (espera hasta ${UP_TIMEOUT} s a que todo esté healthy)..."
if ! docker compose --env-file .env -f "$COMPOSE_FILE" up -d "${build[@]}" \
    --wait --wait-timeout "$UP_TIMEOUT"; then
  echo >&2
  echo "ERROR: algún contenedor no quedó healthy. Estado actual:" >&2
  docker compose --env-file .env -f "$COMPOSE_FILE" ps -a >&2
  echo "Revisa sus registros con: docker compose --env-file .env -f $COMPOSE_FILE logs <servicio>" >&2
  exit 1
fi

gateway_port=$(sed -n 's/^GATEWAY_PORT=//p' .env)
cat <<EOF

FoodFlow está arriba.
  Frontend             http://localhost:4200
  API Gateway          http://localhost:${gateway_port:-8080}  (health: /actuator/health)
  Proveedor simulado   http://localhost:$(sed -n 's/^NOTIFICATION_PROVIDER_HOST_PORT=//p' .env)
  Kafka desde el host  localhost:$(sed -n 's/^KAFKA_HOST_PORT=//p' .env)

Siguiente paso: bash scripts/smoke-test.sh
EOF
