#!/usr/bin/env bash
# ============================================================================
#  HU-608 — Verificación de los atributos de calidad
# ============================================================================
#  Una comprobación reproducible por cada criterio de la página
#  docs/wiki/02-arquitectura/atributos-de-calidad.md. Imprime el valor medido
#  de cada una para que quede registrado en
#  docs/wiki/04-implementacion/pruebas/atributos-de-calidad.md.
#
#  Requiere el entorno levantado (`bash scripts/up.sh`), `curl` y `docker`.
#  No usa herramientas opcionales (ni Testcontainers, ni jq, ni generadores de
#  carga externos): solo el propio Compose, que es lo que se demuestra.
#
#  Uso:
#    bash scripts/verify-quality-attributes.sh
#    bash scripts/verify-quality-attributes.sh --sin-disruptivos
#    bash scripts/verify-quality-attributes.sh --solo rendimiento,replay
#    bash scripts/verify-quality-attributes.sh --listar
#
#  Las pruebas disruptivas (disponibilidad, replay, escalabilidad) detienen
#  contenedores o levantan una réplica extra y dejan el entorno como estaba.
#
#  Variables: GATEWAY_URL, CARGA_PEDIDOS (20), ESPERA_ESTADO (30 s),
#  ESPERA_ARRANQUE (180 s), ESPERA_REPLAY (600 s).
# ============================================================================
set -uo pipefail

cd "$(dirname "$0")/.."

# ---------------------------------------------------------------------------
#  Configuración leída del .env, con los mismos valores por omisión que Compose
# ---------------------------------------------------------------------------
var_env() { sed -n "s/^$1=//p" .env 2>/dev/null | head -1; }
con_omision() { local v; v=$(var_env "$1"); echo "${v:-$2}"; }

GATEWAY_URL="${GATEWAY_URL:-http://localhost:$(con_omision GATEWAY_PORT 8080)}"
ORDERS_TOPIC=$(con_omision ORDERS_TOPIC orders.events)
PAYMENTS_TOPIC=$(con_omision PAYMENTS_TOPIC payments.events)
NOTIFICATIONS_TOPIC=$(con_omision NOTIFICATIONS_TOPIC notifications.events)
GRUPO_ORDER=$(con_omision ORDER_PAYMENTS_CONSUMER_GROUP order-service.payments)
GRUPO_PAYMENT=$(con_omision PAYMENT_ORDERS_CONSUMER_GROUP payment-service.orders)
GRUPO_NOTIFICATION=$(con_omision NOTIFICATION_PAYMENTS_CONSUMER_GROUP notification-service.payments)
ORDER_DB_USER=$(con_omision ORDER_DB_USER orderuser)
ORDER_DB_NAME=$(con_omision ORDER_DB_NAME orderdb)
PAYMENT_DB_USER=$(con_omision PAYMENT_DB_USER paymentuser)
PAYMENT_DB_NAME=$(con_omision PAYMENT_DB_NAME paymentdb)
NOTIFICATION_DB_USER=$(con_omision NOTIFICATION_DB_USER notificationuser)
NOTIFICATION_DB_NAME=$(con_omision NOTIFICATION_DB_NAME notificationdb)

KAFKA=foodflow-kafka
CARGA_PEDIDOS="${CARGA_PEDIDOS:-20}"
ESPERA_ESTADO="${ESPERA_ESTADO:-30}"
ESPERA_ARRANQUE="${ESPERA_ARRANQUE:-180}"

UMBRAL_P95_HTTP_MS=500
UMBRAL_P95_CONVERGENCIA_MS=5000

TRABAJO=$(mktemp -d)
trap 'rm -rf "$TRABAJO"' EXIT

# ---------------------------------------------------------------------------
#  Salida
# ---------------------------------------------------------------------------
fallos=0
resumen="$TRABAJO/resumen"
: > "$resumen"

titulo()  { echo; echo "== $*"; }
ok()      { echo "  ok        $*"; }
falla()   { echo "  FALLA     $*"; fallos=$((fallos + 1)); }
aviso()   { echo "  aviso     $*"; }
medida()  { echo "  medida    $*"; }
anota()   { printf '%s\t%s\t%s\n' "$1" "$2" "$3" >> "$resumen"; }

# ---------------------------------------------------------------------------
#  Utilidades
# ---------------------------------------------------------------------------
ahora_ms() {
  if date +%s%3N 2>/dev/null | grep -qv N; then
    date +%s%3N
  else
    perl -MTime::HiRes=time -e 'printf "%d\n", time*1000'
  fi
}

nuevo_uuid() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen | tr 'A-Z' 'a-z'
  elif [ -r /proc/sys/kernel/random/uuid ]; then
    cat /proc/sys/kernel/random/uuid
  else
    printf '%04x%04x-%04x-4%03x-a%03x-%04x%04x%04x\n' \
      $RANDOM $RANDOM $RANDOM $((RANDOM % 4096)) $((RANDOM % 4096)) $RANDOM $RANDOM $RANDOM
  fi
}

campo() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" | head -1; }

# Percentil 95 por rango más cercano sobre una lista de enteros por línea.
p95() { sort -n | awk '{v[NR]=$1} END {if (NR==0) {print "NA"; exit} i=int(NR*0.95); if (i*100 < NR*95) i=i+1; if (i<1) i=1; print v[i]}'; }

kafka_consumir() {  # tópico [ms]
  MSYS_NO_PATHCONV=1 docker exec "$KAFKA" /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic "$1" --from-beginning \
    --timeout-ms "${2:-8000}" 2>/dev/null || true
}

kafka_grupos() {
  MSYS_NO_PATHCONV=1 docker exec "$KAFKA" /opt/kafka/bin/kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 "$@" 2>/dev/null
}

sql_order()        { docker exec "foodflow-order-db" psql -U "$ORDER_DB_USER" -d "$ORDER_DB_NAME" -tAc "$1"; }
sql_payment()      { docker exec "foodflow-payment-db" psql -U "$PAYMENT_DB_USER" -d "$PAYMENT_DB_NAME" -tAc "$1"; }
sql_notification() { docker exec "foodflow-notification-db" psql -U "$NOTIFICATION_DB_USER" -d "$NOTIFICATION_DB_NAME" -tAc "$1"; }

crear_pedido() {  # token referencia -> imprime el id, o vacío si falla
  local token=$1 referencia=$2 clave cuerpo codigo salida
  clave=$(nuevo_uuid)
  salida="$TRABAJO/pedido-$(nuevo_uuid).json"
  cuerpo="{\"customerReference\":\"$referencia\",\"customerContact\":\"$CONTACTO_PRUEBA\",\"notificationChannel\":\"EMAIL\",\"total\":45000.00,\"paymentToken\":\"$token\"}"
  codigo=$(curl -sS -o "$salida" -w '%{http_code}' \
    -H 'Content-Type: application/json' -H "Idempotency-Key: $clave" \
    -d "$cuerpo" "$GATEWAY_URL/orders" 2>/dev/null)
  [ "$codigo" = "201" ] || { echo ""; return 1; }
  campo id < "$salida"
}

estado_pedido() { curl -sS "$GATEWAY_URL/orders/$1" 2>/dev/null | campo status; }

esperar_estado() {  # id estado [segundos] -> imprime los ms que tardó
  local id=$1 esperado=$2 limite=${3:-$ESPERA_ESTADO} inicio fin
  inicio=$(ahora_ms)
  fin=$((inicio + limite * 1000))
  while [ "$(ahora_ms)" -lt "$fin" ]; do
    [ "$(estado_pedido "$id")" = "$esperado" ] && { echo $(( $(ahora_ms) - inicio )); return 0; }
    sleep 0.3
  done
  echo -1
  return 1
}

esperar_salud() {  # contenedor [segundos]
  local contenedor=$1 limite=${2:-$ESPERA_ARRANQUE} fin estado
  fin=$(( $(date +%s) + limite ))
  while [ "$(date +%s)" -lt "$fin" ]; do
    estado=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$contenedor" 2>/dev/null || echo ausente)
    case "$estado" in healthy|running) return 0 ;; esac
    sleep 2
  done
  return 1
}

esperar_notificacion() {  # id [segundos] -> imprime el estado, o vacío
  local id=$1 limite=${2:-90} fin estado=""
  fin=$(( $(date +%s) + limite ))
  while [ "$(date +%s)" -lt "$fin" ]; do
    estado=$(curl -sS "$GATEWAY_URL/orders/$id/notifications" 2>/dev/null | campo status)
    [ -n "$estado" ] && break
    sleep 2
  done
  echo "$estado"
}

esperar_sin_retraso() {  # grupo [segundos] — espera a que el grupo consuma todo
  local grupo=$1 limite=${2:-120} fin suma
  fin=$(( $(date +%s) + limite ))
  while [ "$(date +%s)" -lt "$fin" ]; do
    suma=$(kafka_grupos --describe --group "$grupo" \
      | awk '$6 ~ /^[0-9]+$/ {s += $6} END {print s + 0}')
    [ "${suma:-1}" = "0" ] && return 0
    sleep 2
  done
  return 1
}

CONTACTO_PRUEBA="${CONTACTO_PRUEBA:-hu608@foodflow.test}"

# ===========================================================================
#  1. Rendimiento y 2. Consistencia eventual — una sola carga sirve a ambos
# ===========================================================================
#  Se lanzan CARGA_PEDIDOS POST /orders concurrentes y se mide, por pedido,
#  cuánto tarda la respuesta HTTP (rendimiento: el pago no se espera) y cuánto
#  tarda el pedido en converger a su estado final (consistencia eventual).
carga_concurrente() {
  local n=$CARGA_PEDIDOS i dir inicio
  dir="$TRABAJO/carga"
  mkdir -p "$dir"

  for i in $(seq 1 "$n"); do
    (
      local clave cuerpo
      clave=$(nuevo_uuid)
      cuerpo="{\"customerReference\":\"HU608-CARGA-$i\",\"customerContact\":\"$CONTACTO_PRUEBA\",\"notificationChannel\":\"EMAIL\",\"total\":45000.00,\"paymentToken\":\"PAY-OK\"}"
      ahora_ms > "$dir/inicio-$i"
      curl -sS -o "$dir/resp-$i.json" -w '%{http_code} %{time_total}\n' \
        -H 'Content-Type: application/json' -H "Idempotency-Key: $clave" \
        -d "$cuerpo" "$GATEWAY_URL/orders" > "$dir/http-$i" 2>/dev/null
    ) &
  done
  wait
  echo "$dir"
}

prueba_rendimiento_y_consistencia() {
  titulo "Rendimiento y consistencia eventual — $CARGA_PEDIDOS pedidos concurrentes"
  local dir i codigo creados=0 limite fin id estado

  dir=$(carga_concurrente)

  : > "$dir/latencias"
  for i in $(seq 1 "$CARGA_PEDIDOS"); do
    codigo=$(awk '{print $1}' "$dir/http-$i" 2>/dev/null)
    if [ "$codigo" = "201" ]; then
      creados=$((creados + 1))
      awk '{printf "%d\n", $2 * 1000}' "$dir/http-$i" >> "$dir/latencias"
      campo id < "$dir/resp-$i.json" > "$dir/id-$i"
    else
      falla "POST /orders devolvió ${codigo:-sin respuesta} en el pedido $i"
    fi
  done

  local p95_http
  p95_http=$(p95 < "$dir/latencias")
  medida "p95 de POST /orders: ${p95_http} ms (umbral $UMBRAL_P95_HTTP_MS ms, $creados/$CARGA_PEDIDOS creados)"
  if [ "$creados" = "$CARGA_PEDIDOS" ] && [ "$p95_http" -lt "$UMBRAL_P95_HTTP_MS" ]; then
    ok "Rendimiento: p95 por debajo del umbral sin esperar el pago"
  else
    falla "Rendimiento: p95 ${p95_http} ms con $creados/$CARGA_PEDIDOS creados"
  fi
  anota Rendimiento "p95 ${p95_http} ms (n=$CARGA_PEDIDOS)" "umbral ${UMBRAL_P95_HTTP_MS} ms"

  # Convergencia: se sondea cada pedido hasta su estado final o hasta el límite.
  fin=$(( $(ahora_ms) + ESPERA_ESTADO * 1000 ))
  local pendientes=1
  while [ "$pendientes" -gt 0 ] && [ "$(ahora_ms)" -lt "$fin" ]; do
    pendientes=0
    for i in $(seq 1 "$CARGA_PEDIDOS"); do
      [ -f "$dir/id-$i" ] || continue
      [ -f "$dir/conv-$i" ] && continue
      id=$(cat "$dir/id-$i")
      estado=$(estado_pedido "$id")
      case "$estado" in
        PAGADO|PAGO_RECHAZADO) echo $(( $(ahora_ms) - $(cat "$dir/inicio-$i") )) > "$dir/conv-$i" ;;
        *) pendientes=$((pendientes + 1)) ;;
      esac
    done
    [ "$pendientes" -gt 0 ] && sleep 0.3
  done

  cat "$dir"/conv-* 2>/dev/null > "$dir/convergencias"
  local convergidos p95_conv
  convergidos=$(wc -l < "$dir/convergencias" | tr -d ' ')
  p95_conv=$(p95 < "$dir/convergencias")
  medida "p95 de convergencia a estado final: ${p95_conv} ms ($convergidos/$CARGA_PEDIDOS convergidos, umbral $UMBRAL_P95_CONVERGENCIA_MS ms)"
  if [ "$convergidos" = "$CARGA_PEDIDOS" ] && [ "$p95_conv" -lt "$UMBRAL_P95_CONVERGENCIA_MS" ]; then
    ok "Consistencia eventual: todos los pedidos convergen dentro del umbral"
  else
    falla "Consistencia eventual: $convergidos/$CARGA_PEDIDOS convergidos, p95 ${p95_conv} ms"
  fi
  anota "Consistencia (eventual)" "p95 ${p95_conv} ms (n=$CARGA_PEDIDOS)" "umbral ${UMBRAL_P95_CONVERGENCIA_MS} ms"

  # Un pedido de esta carga alimenta las pruebas de trazabilidad y seguridad.
  [ -f "$dir/id-1" ] && cp "$dir/id-1" "$TRABAJO/pedido-referencia"
}

# ===========================================================================
#  3. Disponibilidad — el flujo del pago no depende de Notification Service
# ===========================================================================
prueba_disponibilidad() {
  titulo "Disponibilidad — Notification Service detenido"
  local id ms notificaciones fin

  docker stop foodflow-notification-service >/dev/null 2>&1 \
    || { falla "No se pudo detener Notification Service"; return; }
  ok "Notification Service detenido"

  id=$(crear_pedido PAY-OK "HU608-DISPONIBILIDAD")
  if [ -z "$id" ]; then
    falla "POST /orders falló con Notification Service caído"
    docker start foodflow-notification-service >/dev/null 2>&1
    return
  fi

  ms=$(esperar_estado "$id" PAGADO)
  if [ "$ms" -ge 0 ] 2>/dev/null; then
    ok "El pedido alcanzó PAGADO en ${ms} ms sin Notification Service (regla 12)"
    medida "convergencia sin Notification Service: ${ms} ms"
  else
    falla "El pedido no alcanzó PAGADO con Notification Service caído"
  fi

  # Con el servicio caído la consulta puede devolver la lista vacía o un 503 en
  # formato Problem Details: las dos son respuestas correctas y ninguna bloquea
  # el pago. Lo que no vale es una notificación creada sin su servicio.
  notificaciones=$(curl -sS "$GATEWAY_URL/orders/$id/notifications" 2>/dev/null)
  if [ "$notificaciones" = "[]" ]; then
    ok "Sin notificación mientras el servicio está caído, como se espera"
  elif echo "$notificaciones" | grep -q 'DEPENDENCY_UNAVAILABLE'; then
    ok "La consulta de notificaciones degrada con 503 DEPENDENCY_UNAVAILABLE, sin afectar al pago"
  else
    falla "El endpoint de notificaciones devolvió algo inesperado: $notificaciones"
  fi

  docker start foodflow-notification-service >/dev/null 2>&1
  esperar_salud foodflow-notification-service || aviso "Notification Service tardó más de ${ESPERA_ARRANQUE}s en quedar sano"

  local estado_notificacion
  estado_notificacion=$(esperar_notificacion "$id")
  if [ "$estado_notificacion" = "ENVIADA" ]; then
    ok "Al reiniciarlo procesó la notificación pendiente (estado ENVIADA)"
    anota Disponibilidad "pedido a PAGADO en ${ms} ms sin Notification Service; notificación recuperada al reiniciar" "cumple"
  else
    falla "La notificación pendiente no se procesó al reiniciar (estado: ${estado_notificacion:-ninguna})"
    anota Disponibilidad "notificación pendiente no recuperada" "no cumple"
  fi
}

# ===========================================================================
#  4. Idempotencia — el mismo evento entregado dos veces de verdad (ADR-09)
# ===========================================================================
prueba_idempotencia() {
  titulo "Idempotencia — reentrega real de PaymentApproved"
  local id evento clave_particion
  local pagos_antes pagos_despues notif_antes notif_despues actualizado_antes actualizado_despues transiciones_antes transiciones_despues

  id=$(crear_pedido PAY-OK "HU608-IDEMPOTENCIA")
  [ -z "$id" ] && { falla "No se pudo crear el pedido de la prueba"; return; }
  esperar_estado "$id" PAGADO >/dev/null || { falla "El pedido no llegó a PAGADO"; return; }
  esperar_notificacion "$id" >/dev/null   # la foto se toma con la notificación ya creada

  evento=$(kafka_consumir "$PAYMENTS_TOPIC" 8000 | grep "\"aggregateId\":\"$id\"" | head -1)
  [ -z "$evento" ] && { falla "No se encontró el PaymentApproved del pedido en $PAYMENTS_TOPIC"; return; }

  pagos_antes=$(sql_payment "select count(*) from payments where order_id = '$id'")
  notif_antes=$(sql_notification "select count(*) from notifications where order_id = '$id'")
  actualizado_antes=$(sql_order "select updated_at from orders where id = '$id'")
  transiciones_antes=$(kafka_consumir "$ORDERS_TOPIC" 8000 | grep -c "\"aggregateId\":\"$id\".*OrderStatusChanged\|OrderStatusChanged.*\"aggregateId\":\"$id\"")

  clave_particion=$id
  printf '%s|%s\n' "$clave_particion" "$evento" \
    | MSYS_NO_PATHCONV=1 docker exec -i "$KAFKA" /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server localhost:9092 --topic "$PAYMENTS_TOPIC" \
        --property parse.key=true --property key.separator='|' >/dev/null 2>&1 \
    || { falla "No se pudo reentregar el evento"; return; }
  ok "Evento reentregado con el mismo eventId y la misma clave de partición"

  esperar_sin_retraso "$GRUPO_ORDER" 60 || aviso "El grupo $GRUPO_ORDER seguía con retraso"
  esperar_sin_retraso "$GRUPO_NOTIFICATION" 60 >/dev/null 2>&1
  sleep 5

  pagos_despues=$(sql_payment "select count(*) from payments where order_id = '$id'")
  notif_despues=$(sql_notification "select count(*) from notifications where order_id = '$id'")
  actualizado_despues=$(sql_order "select updated_at from orders where id = '$id'")
  transiciones_despues=$(kafka_consumir "$ORDERS_TOPIC" 8000 | grep -c "\"aggregateId\":\"$id\".*OrderStatusChanged\|OrderStatusChanged.*\"aggregateId\":\"$id\"")

  medida "pagos $pagos_antes -> $pagos_despues · notificaciones $notif_antes -> $notif_despues · OrderStatusChanged $transiciones_antes -> $transiciones_despues"
  [ "$pagos_despues" = "1" ] && ok "Un solo pago" || falla "Pagos del pedido: $pagos_despues"
  [ "$notif_despues" = "1" ] && ok "Una sola notificación" || falla "Notificaciones del pedido: $notif_despues"
  [ "$transiciones_despues" = "$transiciones_antes" ] && ok "Ninguna transición nueva" || falla "OrderStatusChanged pasó de $transiciones_antes a $transiciones_despues"
  if [ "$actualizado_antes" = "$actualizado_despues" ]; then
    ok "updated_at del pedido no se movió: $actualizado_despues"
  else
    falla "updated_at cambió: $actualizado_antes -> $actualizado_despues"
  fi
  anota Idempotencia "1 pago, 1 notificación, ${transiciones_despues} transición; updated_at intacto" "cumple"
}

# ===========================================================================
#  5. Recuperabilidad — Parcial (ADR-08)
# ===========================================================================
#  Dos mitades. La primera reprocesa payments.events desde el principio con el
#  grupo de Order Service reiniciado a earliest: el estado se reconstruye sin
#  duplicar efectos. La segunda demuestra el límite: un pedido cuyo OrderCreated
#  nunca llegó al broker no lo recupera ningún replay.
prueba_recuperabilidad() {
  titulo "Recuperabilidad — replay de $PAYMENTS_TOPIC con offsets a earliest"
  local estados_antes estados_despues transiciones_antes transiciones_despues max_antes max_despues reposicionadas

  estados_antes=$(sql_order "select status || '=' || count(*) from orders group by status order by 1" | tr '\n' ' ')
  max_antes=$(sql_order "select coalesce(max(updated_at)::text, 'ninguno') from orders")
  transiciones_antes=$(kafka_consumir "$ORDERS_TOPIC" 8000 | grep -c OrderStatusChanged)

  docker stop foodflow-order-service >/dev/null 2>&1 || { falla "No se pudo detener Order Service"; return; }
  reposicionadas=$(kafka_grupos --group "$GRUPO_ORDER" --reset-offsets --to-earliest --all-topics --execute | grep -c "^$GRUPO_ORDER")
  docker start foodflow-order-service >/dev/null 2>&1
  ok "Offsets del grupo $GRUPO_ORDER reiniciados a earliest en $reposicionadas particiones"

  esperar_salud foodflow-order-service || aviso "Order Service tardó en quedar sano"
  # El replay desde earliest arrastra los eventos huérfanos de ejecuciones
  # anteriores: cada uno agota sus 3 reintentos (1 s, 2 s, 4 s) antes de ir a la
  # DLQ, así que drenar el tópico entero lleva minutos, no segundos (HU-602).
  esperar_sin_retraso "$GRUPO_ORDER" "${ESPERA_REPLAY:-600}" || aviso "El grupo $GRUPO_ORDER no llegó a retraso 0 en ${ESPERA_REPLAY:-600}s"
  sleep 5

  estados_despues=$(sql_order "select status || '=' || count(*) from orders group by status order by 1" | tr '\n' ' ')
  max_despues=$(sql_order "select coalesce(max(updated_at)::text, 'ninguno') from orders")
  transiciones_despues=$(kafka_consumir "$ORDERS_TOPIC" 8000 | grep -c OrderStatusChanged)

  medida "estados: [$estados_antes] -> [$estados_despues]"
  medida "OrderStatusChanged en $ORDERS_TOPIC: $transiciones_antes -> $transiciones_despues"
  [ "$estados_antes" = "$estados_despues" ] && ok "El replay no cambió ningún estado" || falla "El replay cambió los estados"
  [ "$transiciones_despues" = "$transiciones_antes" ] && ok "El replay no publicó transiciones nuevas" || falla "El replay publicó $((transiciones_despues - transiciones_antes)) transiciones nuevas"
  [ "$max_antes" = "$max_despues" ] && ok "Ningún updated_at se movió ($max_despues)" || falla "updated_at máximo cambió: $max_antes -> $max_despues"

  titulo "Recuperabilidad — el evento que nunca llegó al broker no se recupera"
  local id estado_final eventos_del_pedido
  docker stop "$KAFKA" >/dev/null 2>&1 || { falla "No se pudo detener Kafka"; return; }
  id=$(crear_pedido PAY-OK "HU608-SIN-BROKER")
  if [ -z "$id" ]; then
    aviso "POST /orders no respondió 201 con Kafka caído; se omite la demostración"
    docker start "$KAFKA" >/dev/null 2>&1
    esperar_salud "$KAFKA"
    return
  fi
  ok "El pedido $id quedó persistido con el broker caído (201)"
  # El proceso cae antes de que el productor consiga publicar: el evento se pierde.
  docker stop foodflow-order-service >/dev/null 2>&1
  docker start "$KAFKA" >/dev/null 2>&1
  esperar_salud "$KAFKA" || aviso "Kafka tardó en quedar sano"
  docker start foodflow-order-service >/dev/null 2>&1
  esperar_salud foodflow-order-service || aviso "Order Service tardó en quedar sano"
  sleep 10

  eventos_del_pedido=$(kafka_consumir "$ORDERS_TOPIC" 8000 | grep -c "\"aggregateId\":\"$id\"")
  estado_final=$(estado_pedido "$id")
  medida "eventos de $id en $ORDERS_TOPIC: $eventos_del_pedido · estado del pedido: $estado_final"
  if [ "$eventos_del_pedido" = "0" ] && [ "$estado_final" = "CREADO" ]; then
    ok "Recuperabilidad Parcial demostrada: el pedido existe, su OrderCreated no, y ningún replay lo reconstruye (ADR-08)"
    anota Recuperabilidad "replay sin duplicados; pedido $id persistido sin evento y no recuperable" "Parcial, como declara ADR-08"
  else
    falla "Se esperaba 0 eventos y estado CREADO; hubo $eventos_del_pedido eventos y estado $estado_final"
    anota Recuperabilidad "la demostración del límite no se reprodujo" "revisar"
  fi
}

# ===========================================================================
#  6. Desacoplamiento — un consumidor nuevo recibe OrderCreated
# ===========================================================================
prueba_desacoplamiento() {
  titulo "Desacoplamiento — consumidor adicional sobre $ORDERS_TOPIC"
  local grupo salida id fin recibido=0
  grupo="hu608-observador-$(nuevo_uuid | cut -c1-8)"
  salida="$TRABAJO/observador.log"

  MSYS_NO_PATHCONV=1 docker exec "$KAFKA" /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic "$ORDERS_TOPIC" --group "$grupo" \
    --timeout-ms 60000 > "$salida" 2>/dev/null &
  local consumidor=$!
  sleep 8   # espera a que el grupo nuevo tenga particiones asignadas

  id=$(crear_pedido PAY-OK "HU608-DESACOPLAMIENTO")
  [ -z "$id" ] && { falla "No se pudo crear el pedido"; kill "$consumidor" 2>/dev/null; return; }

  fin=$(( $(date +%s) + 30 ))
  while [ "$(date +%s)" -lt "$fin" ]; do
    if grep -q "\"aggregateId\":\"$id\"" "$salida" 2>/dev/null; then recibido=1; break; fi
    sleep 1
  done
  kill "$consumidor" 2>/dev/null
  wait "$consumidor" 2>/dev/null

  if [ "$recibido" = "1" ]; then
    ok "El grupo $grupo recibió OrderCreated del pedido $id sin tocar Order Service"
    anota Desacoplamiento "consumidor nuevo ($grupo) recibe OrderCreated sin cambios en Order Service" "cumple"
  else
    falla "El consumidor adicional no recibió OrderCreated del pedido $id"
    anota Desacoplamiento "el consumidor adicional no recibió el evento" "no cumple"
  fi
}

# ===========================================================================
#  7. Trazabilidad — un correlationId localiza logs y eventos
# ===========================================================================
prueba_trazabilidad() {
  titulo "Trazabilidad — un correlationId a través de tres servicios y tres tópicos"
  local id correlacion servicio n total_logs=0 eventos_orders eventos_payments eventos_notifications

  if [ -f "$TRABAJO/pedido-referencia" ]; then
    id=$(cat "$TRABAJO/pedido-referencia")
  else
    id=$(crear_pedido PAY-OK "HU608-TRAZABILIDAD")
    [ -z "$id" ] && { falla "No se pudo crear el pedido"; return; }
    esperar_estado "$id" PAGADO >/dev/null
    esperar_notificacion "$id" >/dev/null
  fi

  correlacion=$(kafka_consumir "$ORDERS_TOPIC" 8000 | grep "\"aggregateId\":\"$id\"" | head -1 | campo correlationId)
  [ -z "$correlacion" ] && { falla "No se encontró el correlationId del pedido $id"; return; }
  medida "correlationId del pedido $id: $correlacion"

  # El correlationId se busca en los dos formatos de log: como propiedad JSON
  # (logs estructurados de HU-603) y como par clave=valor del patrón de consola.
  for servicio in foodflow-order-service foodflow-payment-service foodflow-notification-service; do
    n=$(docker logs "$servicio" 2>&1 | grep -cE "\"correlationId\":\"$correlacion\"|correlationId=$correlacion")
    total_logs=$((total_logs + n))
    if [ "$n" -gt 0 ]; then ok "$servicio: $n líneas con el correlationId"; else falla "$servicio: ninguna línea con el correlationId"; fi
  done

  eventos_orders=$(kafka_consumir "$ORDERS_TOPIC" 8000 | grep -c "\"correlationId\":\"$correlacion\"")
  eventos_payments=$(kafka_consumir "$PAYMENTS_TOPIC" 8000 | grep -c "\"correlationId\":\"$correlacion\"")
  eventos_notifications=$(kafka_consumir "$NOTIFICATIONS_TOPIC" 8000 | grep -c "\"correlationId\":\"$correlacion\"")
  medida "eventos con ese correlationId: $ORDERS_TOPIC=$eventos_orders · $PAYMENTS_TOPIC=$eventos_payments · $NOTIFICATIONS_TOPIC=$eventos_notifications"
  if [ "$eventos_orders" -ge 1 ] && [ "$eventos_payments" -ge 1 ] && [ "$eventos_notifications" -ge 1 ]; then
    ok "El correlationId aparece en los tres tópicos"
    anota Trazabilidad "$total_logs líneas de log y $((eventos_orders + eventos_payments + eventos_notifications)) eventos con el mismo correlationId" "cumple"
  else
    falla "El correlationId no aparece en los tres tópicos"
    anota Trazabilidad "el correlationId no recorre los tres tópicos" "no cumple"
  fi
}

# ===========================================================================
#  8. Testabilidad — los dos flujos con un solo comando
# ===========================================================================
prueba_testabilidad() {
  titulo "Testabilidad — scripts/smoke-test.sh"
  local inicio segundos
  inicio=$(date +%s)
  if bash scripts/smoke-test.sh > "$TRABAJO/smoke.log" 2>&1; then
    segundos=$(( $(date +%s) - inicio ))
    ok "smoke-test.sh recorrió PAY-OK y PAY-FAIL en ${segundos}s"
    anota Testabilidad "smoke-test.sh: RESULTADO OK en ${segundos}s" "cumple"
  else
    segundos=$(( $(date +%s) - inicio ))
    falla "smoke-test.sh terminó con error tras ${segundos}s (salida en la traza)"
    tail -20 "$TRABAJO/smoke.log" | sed 's/^/            /'
    anota Testabilidad "smoke-test.sh falló" "no cumple"
  fi
}

# ===========================================================================
#  9. Escalabilidad — dos réplicas de Payment Service en el mismo grupo
# ===========================================================================
prueba_escalabilidad() {
  titulo "Escalabilidad — reparto de particiones con 2 réplicas de Payment Service"
  local miembros particiones_por_miembro fin id ms

  set -a; . ./.env; set +a
  if ! docker compose -f infrastructure/compose/docker-compose.yml \
                      -f infrastructure/compose/docker-compose.escalado.yml \
                      up -d payment-service-2 >/dev/null 2>&1; then
    falla "No se pudo levantar la segunda réplica de Payment Service"
    return
  fi
  ok "Segunda réplica levantada (foodflow-payment-service-2)"
  esperar_salud foodflow-payment-service-2 || aviso "La réplica tardó en quedar sana"

  # Espera al reequilibrio: dos miembros con particiones asignadas.
  fin=$(( $(date +%s) + 120 ))
  miembros=0
  while [ "$(date +%s)" -lt "$fin" ]; do
    miembros=$(kafka_grupos --describe --group "$GRUPO_PAYMENT" \
      | awk '$1 != "GROUP" && $7 != "CONSUMER-ID" && $7 != "-" && $7 != "" {print $7}' | sort -u | wc -l | tr -d ' ')
    [ "$miembros" -ge 2 ] && break
    sleep 3
  done

  particiones_por_miembro=$(kafka_grupos --describe --group "$GRUPO_PAYMENT" \
    | awk '$1 != "GROUP" && $7 != "CONSUMER-ID" && $7 != "-" && $7 != "" {n[$7]++} END {for (c in n) printf "...%s=%d particiones ", substr(c, length(c) - 7), n[c]}')
  medida "miembros del grupo $GRUPO_PAYMENT: $miembros · particiones: $particiones_por_miembro"

  if [ "$miembros" -ge 2 ]; then
    ok "Las particiones de $ORDERS_TOPIC se reparten entre las dos réplicas"
  else
    falla "El grupo $GRUPO_PAYMENT quedó con $miembros miembro(s) con particiones"
  fi

  # Con las dos réplicas corriendo, el flujo sigue completo.
  id=$(crear_pedido PAY-OK "HU608-ESCALABILIDAD")
  if [ -n "$id" ] && ms=$(esperar_estado "$id" PAGADO) && [ "$ms" -ge 0 ]; then
    ok "Con dos réplicas el pedido llegó a PAGADO en ${ms} ms"
  else
    falla "Con dos réplicas el pedido no alcanzó PAGADO"
  fi
  anota Escalabilidad "$miembros miembros en $GRUPO_PAYMENT; particiones $particiones_por_miembro" "cumple"

  docker rm -f foodflow-payment-service-2 >/dev/null 2>&1
  ok "Réplica retirada; el entorno queda como estaba"
  esperar_sin_retraso "$GRUPO_PAYMENT" 60 >/dev/null 2>&1
}

# ===========================================================================
#  10. Seguridad — ni secretos ni contacto completo en logs; destino enmascarado
# ===========================================================================
prueba_seguridad() {
  titulo "Seguridad — secretos y datos de contacto"
  local id destino servicio n contacto_en_logs=0 secretos_en_logs=0 secreto nombre

  id=$(crear_pedido PAY-OK "HU608-SEGURIDAD")
  [ -z "$id" ] && { falla "No se pudo crear el pedido"; return; }
  esperar_estado "$id" PAGADO >/dev/null
  # La notificación llega después del pago: se espera a que exista antes de leer
  # su destino, o la comprobación mediría una lista vacía y no el enmascarado.
  [ -n "$(esperar_notificacion "$id")" ] || aviso "El pedido no tenía notificación tras la espera"

  for servicio in foodflow-order-service foodflow-payment-service foodflow-notification-service foodflow-api-gateway; do
    n=$(docker logs "$servicio" 2>&1 | grep -c "$CONTACTO_PRUEBA")
    contacto_en_logs=$((contacto_en_logs + n))
    [ "$n" -gt 0 ] && falla "$servicio registra el contacto completo en $n líneas"
  done
  [ "$contacto_en_logs" = "0" ] && ok "Ningún servicio registra el contacto completo (ContactMasker, HU-603)"

  for nombre in ORDER_DB_PASSWORD PAYMENT_DB_PASSWORD NOTIFICATION_DB_PASSWORD NOTIFICATION_PROVIDER_API_KEY; do
    secreto=$(var_env "$nombre")
    [ -z "$secreto" ] && continue
    for servicio in foodflow-order-service foodflow-payment-service foodflow-notification-service foodflow-api-gateway; do
      n=$(docker logs "$servicio" 2>&1 | grep -c -- "$secreto")
      if [ "$n" -gt 0 ]; then
        secretos_en_logs=$((secretos_en_logs + n))
        falla "$servicio registra el valor de $nombre en $n líneas"
      fi
    done
  done
  [ "$secretos_en_logs" = "0" ] && ok "Ningún log contiene el valor de un secreto del .env"

  destino=$(curl -sS "$GATEWAY_URL/orders/$id/notifications" 2>/dev/null | campo destination)
  medida "destino publicado por GET /orders/{id}/notifications: ${destino:-ninguno}"
  if [ -n "$destino" ] && [ "$destino" != "$CONTACTO_PRUEBA" ] && echo "$destino" | grep -q '\*'; then
    ok "El destino de la notificación sale enmascarado"
  else
    falla "El destino de la notificación no está enmascarado: ${destino:-ninguno}"
  fi

  # GET /orders/{id} sí devuelve customerContact: es el snapshot de ADR-11 y el
  # contrato lo exige (contracts/api/openapi.yaml, OrderResponse). Se registra
  # como dato medido, no como fallo; ver la nota del informe de HU-608.
  if curl -sS "$GATEWAY_URL/orders/$id" 2>/dev/null | grep -q "\"customerContact\":\"$CONTACTO_PRUEBA\""; then
    aviso "GET /orders/{id} devuelve customerContact completo, como exige OrderResponse en el contrato"
    anota Seguridad "sin secretos ni contacto en logs; destino enmascarado; customerContact completo en OrderResponse por contrato" "cumple con salvedad"
  else
    ok "GET /orders/{id} no devuelve el contacto completo"
    anota Seguridad "sin secretos ni contacto en logs; destino enmascarado" "cumple"
  fi
}

# ===========================================================================
#  Orquestación
# ===========================================================================
# El replay va al final a propósito: deja al consumidor de Order Service
# drenando el tópico entero durante varios minutos, y cualquier prueba que
# corriera después mediría esa cola, no el flujo normal.
TODAS="rendimiento disponibilidad idempotencia desacoplamiento trazabilidad testabilidad seguridad escalabilidad replay"
DISRUPTIVAS="disponibilidad replay escalabilidad"

ejecutar() {
  case $1 in
    rendimiento)     prueba_rendimiento_y_consistencia ;;
    disponibilidad)  prueba_disponibilidad ;;
    idempotencia)    prueba_idempotencia ;;
    replay)          prueba_recuperabilidad ;;
    desacoplamiento) prueba_desacoplamiento ;;
    trazabilidad)    prueba_trazabilidad ;;
    testabilidad)    prueba_testabilidad ;;
    escalabilidad)   prueba_escalabilidad ;;
    seguridad)       prueba_seguridad ;;
    *) echo "Prueba desconocida: $1"; exit 2 ;;
  esac
}

seleccion=$TODAS
while [ $# -gt 0 ]; do
  case $1 in
    --listar) echo "$TODAS" | tr ' ' '\n'; exit 0 ;;
    --solo) seleccion=$(echo "${2:-}" | tr ',' ' '); shift 2 ;;
    --sin-disruptivos)
      nueva=""
      for prueba in $TODAS; do
        case " $DISRUPTIVAS " in *" $prueba "*) continue ;; esac
        nueva="$nueva $prueba"
      done
      seleccion=$nueva; shift ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "Opción desconocida: $1"; exit 2 ;;
  esac
done

echo "FoodFlow · HU-608 · verificación de atributos de calidad"
echo "Fecha:    $(date '+%Y-%m-%d %H:%M:%S %Z')"
echo "Gateway:  $GATEWAY_URL"
echo "Pruebas:  $(echo "$seleccion" | tr -s ' ')"

if ! docker inspect -f '{{.State.Status}}' "$KAFKA" >/dev/null 2>&1; then
  echo
  echo "El entorno no está levantado. Ejecuta antes: bash scripts/up.sh"
  exit 2
fi

for prueba in $seleccion; do ejecutar "$prueba"; done

echo
echo "== Resumen"
if [ -s "$resumen" ]; then
  while IFS="$(printf '\t')" read -r atributo valor veredicto; do
    printf '  %-26s %-64s %s\n' "$atributo" "$valor" "$veredicto"
  done < "$resumen"
fi

echo
if [ "$fallos" -eq 0 ]; then
  echo "RESULTADO: OK"
  exit 0
fi
echo "RESULTADO: $fallos comprobación(es) fallida(s)"
exit 1
