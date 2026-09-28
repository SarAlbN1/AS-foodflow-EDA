#!/usr/bin/env bash
# ============================================================================
#  FoodFlow EDA — creación declarativa de los tópicos Kafka (HU-004)
# ============================================================================
#  Única declaración de tópicos de la infraestructura. Contrato de referencia:
#  docs/wiki/03-contratos/eventos.md · Documentación: infrastructure/kafka/topics.md
#
#  Lo ejecuta el contenedor `kafka-init` de Compose cuando Kafka está `healthy`.
#  Es idempotente (`--if-not-exists`): se puede volver a ejecutar sin error.
#
#  Variables de entorno:
#    KAFKA_BOOTSTRAP_SERVERS  Broker al que conectarse (por defecto kafka:9092).
#    KAFKA_BIN                Carpeta de las herramientas de Kafka (por defecto /opt/kafka/bin).
# ============================================================================
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
KAFKA_BIN="${KAFKA_BIN:-/opt/kafka/bin}"

# Configuración de eventos.md: 3 particiones, factor de replicación 1,
# retención de 7 días en los tópicos principales y de 14 días en las DLQ.
PARTITIONS=3
REPLICATION_FACTOR=1
RETENTION_MS=604800000       # 7 días
DLQ_RETENTION_MS=1209600000  # 14 días

# Tópicos principales. Cada uno tiene su DLQ `<tópico>.dlq`.
TOPICS=(
  orders.events
  payments.events
  notifications.events
)

create_topic() {
  local topic=$1 retention=$2
  "$KAFKA_BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$PARTITIONS" \
    --replication-factor "$REPLICATION_FACTOR" \
    --config "retention.ms=$retention"
  echo "Tópico listo: $topic (particiones=$PARTITIONS, retention.ms=$retention)"
}

for topic in "${TOPICS[@]}"; do
  create_topic "$topic" "$RETENTION_MS"
  # La DLQ usa las mismas particiones que su tópico: el publicador de DLQ de
  # Spring Kafka conserva por defecto la partición del registro original.
  create_topic "$topic.dlq" "$DLQ_RETENTION_MS"
done

echo "Tópicos en el broker:"
"$KAFKA_BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --list
