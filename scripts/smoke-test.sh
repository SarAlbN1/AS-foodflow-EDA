#!/usr/bin/env bash
# ============================================================================
#  HU-607 — Prueba de humo de FoodFlow
# ============================================================================
#  Recorre el flujo por el API Gateway, como lo haría el frontend, con PAY-OK y
#  con PAY-FAIL, y termina con código distinto de 0 si algo no es lo esperado.
#
#  Requiere el entorno levantado con `bash scripts/up.sh`, `curl` y `docker`.
#
#  Qué comprueba por cada token:
#    1. El gateway está sano (/actuator/health = UP).
#    2. POST /orders con Idempotency-Key -> 201, Location y status CREADO.
#    3. El mismo POST repetido (misma clave y cuerpo) devuelve el mismo pedido.
#    4. GET /orders/{id} -> 200 con el mismo id.
#    5. El resultado del pago llega a payments.events con aggregateId = orderId:
#       PAY-OK -> PaymentApproved, PAY-FAIL -> PaymentRejected (ADR-10). Se lee
#       el tópico desde el contenedor de Kafka, sin grupo de consumidores, así
#       que no altera los offsets de ningún servicio.
#    6. El estado final del pedido: PAY-OK -> PAGADO, PAY-FAIL -> PAGO_RECHAZADO
#       (HU-104 y HU-105). Quedarse en CREADO o llegar a otro estado es un fallo.
#
#  Variables: GATEWAY_URL (por omisión http://localhost:$GATEWAY_PORT o :8080),
#  SMOKE_TIMEOUT (segundos de espera por evento y estado, por omisión 30).
# ============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

gateway_port=8080
if [ -f .env ]; then
  gateway_port=$(sed -n 's/^GATEWAY_PORT=//p' .env)
  gateway_port=${gateway_port:-8080}
fi
GATEWAY_URL="${GATEWAY_URL:-http://localhost:${gateway_port}}"
SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-30}"
KAFKA_CONTAINER=foodflow-kafka
PAYMENTS_TOPIC=$( (sed -n 's/^PAYMENTS_TOPIC=//p' .env 2>/dev/null || true) | head -1)
PAYMENTS_TOPIC=${PAYMENTS_TOPIC:-payments.events}

fallos=0
ok()        { echo "  ok        $*"; }
falla()     { echo "  FALLA     $*"; fallos=$((fallos + 1)); }

# Valor de un campo de texto plano de un JSON de una línea ("campo":"valor").
campo() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" | head -1; }

nuevo_uuid() {
  if [ -r /proc/sys/kernel/random/uuid ]; then
    cat /proc/sys/kernel/random/uuid
  else
    printf '%04x%04x-%04x-4%03x-a%03x-%04x%04x%04x\n' \
      $RANDOM $RANDOM $RANDOM $((RANDOM % 4096)) $((RANDOM % 4096)) $RANDOM $RANDOM $RANDOM
  fi
}

# Lee payments.events completo y busca el evento del pedido. Sin grupo: no toca offsets.
evento_de_pago() {
  MSYS_NO_PATHCONV=1 docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic "$PAYMENTS_TOPIC" --from-beginning \
    --timeout-ms 5000 2>/dev/null \
    | grep "\"aggregateId\":\"$1\"" | campo eventType || true
}

probar() {
  local token=$1 evento_esperado=$2 estado_esperado=$3
  echo
  echo "== Flujo con $token (espera $evento_esperado y $estado_esperado)"

  local clave cuerpo respuesta codigo cuerpo_resp id tmp
  clave=$(nuevo_uuid)
  tmp=$(mktemp)
  cuerpo="{\"customerReference\":\"SMOKE-$token\",\"customerContact\":\"smoke@foodflow.test\",\"notificationChannel\":\"EMAIL\",\"total\":45000.00,\"paymentToken\":\"$token\"}"

  respuesta=$(curl -sS -D - -o "$tmp" -w '%{http_code}' \
    -H 'Content-Type: application/json' -H "Idempotency-Key: $clave" \
    -d "$cuerpo" "$GATEWAY_URL/orders") || { falla "POST /orders no respondió"; return; }
  codigo=${respuesta: -3}
  cuerpo_resp=$(cat "$tmp")
  rm -f "$tmp"
  if [ "$codigo" != "201" ]; then
    falla "POST /orders respondió $codigo: $cuerpo_resp"
    return
  fi
  id=$(echo "$cuerpo_resp" | campo id)
  if echo "$respuesta" | grep -qi "^location: .*/orders/$id"; then
    ok "POST /orders -> 201 con Location /orders/$id"
  else
    falla "POST /orders -> 201 sin Location /orders/$id"
  fi
  [ "$(echo "$cuerpo_resp" | campo status)" = "CREADO" ] \
    && ok "el pedido nace en CREADO" \
    || falla "el pedido no nace en CREADO: $cuerpo_resp"

  local repetido
  repetido=$(curl -sS -H 'Content-Type: application/json' -H "Idempotency-Key: $clave" \
    -d "$cuerpo" "$GATEWAY_URL/orders" | campo id)
  [ "$repetido" = "$id" ] \
    && ok "el reintento con la misma Idempotency-Key devuelve el mismo pedido" \
    || falla "el reintento devolvió otro pedido ($repetido)"

  codigo=$(curl -sS -o /dev/null -w '%{http_code}' "$GATEWAY_URL/orders/$id")
  [ "$codigo" = "200" ] && ok "GET /orders/{id} -> 200" || falla "GET /orders/{id} respondió $codigo"

  local evento="" limite=$((SECONDS + SMOKE_TIMEOUT))
  while [ -z "$evento" ] && [ $SECONDS -lt $limite ]; do
    evento=$(evento_de_pago "$id")
    [ -n "$evento" ] || sleep 2
  done
  if [ "$evento" = "$evento_esperado" ]; then
    ok "$evento_esperado en $PAYMENTS_TOPIC con aggregateId = orderId"
  else
    falla "se esperaba $evento_esperado en $PAYMENTS_TOPIC y llegó '${evento:-nada}'"
  fi

  local estado="CREADO"
  limite=$((SECONDS + SMOKE_TIMEOUT))
  while [ "$estado" = "CREADO" ] && [ $SECONDS -lt $limite ]; do
    sleep 2
    estado=$(curl -sS "$GATEWAY_URL/orders/$id" | campo status)
  done
  if [ "$estado" = "$estado_esperado" ]; then
    ok "el pedido termina en $estado_esperado"
  else
    falla "el pedido terminó en '$estado' y se esperaba $estado_esperado"
  fi
}

echo "Prueba de humo contra $GATEWAY_URL"
salud=$(curl -sS "$GATEWAY_URL/actuator/health" 2>/dev/null || true)
if [ "$(echo "$salud" | campo status)" = "UP" ]; then
  ok "gateway healthy"
else
  echo "  FALLA     el gateway no responde UP en $GATEWAY_URL/actuator/health. ¿Corriste scripts/up.sh?"
  exit 1
fi

probar PAY-OK   PaymentApproved PAGADO
probar PAY-FAIL PaymentRejected PAGO_RECHAZADO

echo
if [ "$fallos" -gt 0 ]; then
  echo "RESULTADO: FALLA ($fallos comprobaciones fallidas)"
  exit 1
fi
echo "RESULTADO: OK"
