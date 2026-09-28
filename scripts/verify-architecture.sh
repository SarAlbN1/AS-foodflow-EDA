#!/usr/bin/env bash
# Verifica las reglas arquitectónicas que pueden comprobarse de forma automática (HU-006).
#
#   1. Ningún pom.xml depende de otro módulo del proyecto (regla 8: sin código compartido).
#   2. En Compose, cada servicio recibe solo la URL y las credenciales de su propia base (regla 2).
#   3. No hay literales de tópicos Kafka fuera de la configuración (regla 11).
#   4. Las pruebas ArchUnit de cada servicio (ArchitectureTest): @KafkaListener y KafkaTemplate
#      solo en infrastructure.messaging, el dominio no depende de api ni de infrastructure, y
#      solo infrastructure.provider de Notification usa un cliente HTTP saliente.
#
# Uso:
#   bash scripts/verify-architecture.sh             # las cuatro comprobaciones
#   bash scripts/verify-architecture.sh --sin-tests # solo 1-3, sin compilar (rápido)
#
# Requiere JDK 25 para la comprobación 4 (JAVA_HOME apuntando a él). No depende de CI.
set -uo pipefail
cd "$(dirname "$0")/.."

SERVICIOS=(order-service payment-service notification-service)
fallos=0

ok()    { echo "  ok     $1"; }
falla() { echo "  FALLA  $1"; fallos=$((fallos + 1)); }
info()  { echo "  --     $1"; }

# ---------------------------------------------------------------------------
echo "1. Dependencias entre módulos (pom.xml)"
for pom in services/*/pom.xml gateway/*/pom.xml; do
  # Solo el bloque <dependencies> del proyecto: ahí no puede aparecer ningún com.foodflow.
  if sed -n '/<dependencies>/,/<\/dependencies>/p' "$pom" | grep -q '<groupId>com\.foodflow</groupId>'; then
    falla "$pom depende de otro módulo com.foodflow"
  else
    ok "$pom no depende de otros módulos del proyecto"
  fi
done

# ---------------------------------------------------------------------------
echo "2. Cada servicio recibe solo su base (Compose)"
COMPOSE=infrastructure/compose/docker-compose.yml
for svc in "${SERVICIOS[@]}"; do
  bloque=$(awk -v s="  $svc:" '$0 == s {p=1; next} p && /^  [a-z0-9-]+:$/ {exit} p && /^[a-z]/ {exit} p' "$COMPOSE")
  if [ -z "$bloque" ]; then
    info "$svc todavía no está en Compose (lo añade HU-607): nada que comprobar"
    continue
  fi
  propia=$(echo "${svc%-service}" | tr '[:lower:]' '[:upper:]')_DB
  ajenas=$(echo "$bloque" | grep -oE '(ORDER|PAYMENT|NOTIFICATION)_DB_[A-Z_]+|(order|payment|notification)-db' \
    | grep -v -E "^${propia}_|^${svc%-service}-db$" | sort -u | tr '\n' ' ')
  if [ -n "$ajenas" ]; then
    falla "$svc recibe datos de otra base: $ajenas"
  else
    ok "$svc solo recibe su propia base"
  fi
done

# ---------------------------------------------------------------------------
echo "3. Sin literales de tópicos fuera de la configuración"
literales=$(grep -rnE '"(orders|payments|notifications)\.events(\.dlq)?"' \
  services/*/src/main/java gateway/*/src/main/java 2>/dev/null || true)
if [ -n "$literales" ]; then
  while IFS= read -r l; do falla "literal de tópico en código: $l"; done <<< "$literales"
else
  ok "los tópicos solo aparecen en application.properties"
fi

# ---------------------------------------------------------------------------
if [ "${1:-}" = "--sin-tests" ]; then
  echo "4. Pruebas ArchUnit: omitidas (--sin-tests)"
else
  echo "4. Pruebas ArchUnit (ArchitectureTest de cada servicio)"
  for svc in "${SERVICIOS[@]}"; do
    dir=services/$svc
    if ! ls "$dir"/src/test/java/com/foodflow/*/ArchitectureTest.java >/dev/null 2>&1; then
      falla "$svc no tiene ArchitectureTest"
      continue
    fi
    if (cd "$dir" && ./mvnw -q test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false \
          > "/tmp/verify-architecture-$svc.log" 2>&1); then
      ok "$svc cumple las reglas ArchUnit"
    else
      falla "$svc incumple una regla ArchUnit (detalle: /tmp/verify-architecture-$svc.log)"
      grep -E "Architecture Violation|was violated" "/tmp/verify-architecture-$svc.log" | head -3 | sed 's/^/           /'
    fi
  done
fi

echo
if [ "$fallos" -eq 0 ]; then
  echo "RESULTADO: OK"
else
  echo "RESULTADO: $fallos incumplimiento(s)"
  exit 1
fi
