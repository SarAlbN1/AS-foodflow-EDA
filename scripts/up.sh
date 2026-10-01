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
#    bash scripts/up.sh --completar-env  añade a .env las variables nuevas de .env.example
#
#  Antes de levantar comprueba que .env tiene todas las variables de .env.example y se
#  detiene listando las que falten: Compose las dejaría en blanco sin avisar.
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

build=(--build)
completar=false
for arg in "$@"; do
  case "$arg" in
    --sin-build) build=() ;;
    --completar-env) completar=true ;;
    *) echo "Uso: bash scripts/up.sh [--sin-build] [--completar-env]" >&2; exit 2 ;;
  esac
done

if [ ! -f .env ]; then
  cp .env.example .env
  echo "AVISO: no había .env; se creó desde .env.example con las contraseñas de ejemplo."
  echo "       Sirve para la demo local; cámbialas en .env si el entorno no es desechable."
fi

# Un .env creado antes de que .env.example creciera no trae las variables nuevas. Compose no
# falla por eso: las sustituye por cadena vacía, y una variable vacía anula el valor por omisión
# de Spring (ORDERS_TOPIC="" deja el tópico en blanco y el OrderCreated se pierde con 201 al
# cliente). Por eso se comparan los NOMBRES contra .env.example antes de levantar nada; los
# valores de .env nunca se imprimen.
ausentes=()
vacias=()
while IFS= read -r nombre; do
  if ! grep -qE "^${nombre}=" .env; then
    ausentes+=("$nombre")
  elif ! grep -qE "^${nombre}=.+" .env; then
    vacias+=("$nombre")
  fi
done < <(grep -oE '^[A-Z][A-Z0-9_]*=' .env.example | tr -d '=' | sort -u)

if [ ${#ausentes[@]} -gt 0 ] && [ "$completar" = true ]; then
  for nombre in "${ausentes[@]}"; do
    grep -E "^${nombre}=" .env.example >> .env
  done
  echo "AVISO: se añadieron a .env, con el valor de .env.example: ${ausentes[*]}"
  echo "       Revisa en especial las contraseñas que se hayan añadido."
  ausentes=()
fi

if [ ${#ausentes[@]} -gt 0 ] || [ ${#vacias[@]} -gt 0 ]; then
  if [ ${#ausentes[@]} -gt 0 ]; then
    echo "ERROR: a .env le faltan variables de .env.example:" >&2
    for nombre in "${ausentes[@]}"; do
      echo "  $(grep -E "^${nombre}=" .env.example)" >&2
    done
    echo "Añádelas a .env (las líneas de arriba son los valores de ejemplo) o ejecuta" >&2
    echo "  bash scripts/up.sh --completar-env" >&2
  fi
  if [ ${#vacias[@]} -gt 0 ]; then
    # Una vacía no se rellena sola: puede ser deliberada y el script no lee valores.
    echo "ERROR: estas variables están en .env pero vacías; dales valor a mano:" >&2
    for nombre in "${vacias[@]}"; do
      echo "  ${nombre} (ejemplo: $(grep -E "^${nombre}=" .env.example | cut -d= -f2-))" >&2
    done
  fi
  exit 1
fi

echo "Levantando FoodFlow (espera hasta ${UP_TIMEOUT} s a que todo esté healthy)..."
# ${build[@]+...}: con set -u, bash < 4.4 (el 3.2 de macOS) trata un array vacio como no
# definido; esta forma expande a nada en lugar de abortar con --sin-build.
if ! docker compose --env-file .env -f "$COMPOSE_FILE" up -d ${build[@]+"${build[@]}"} \
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
