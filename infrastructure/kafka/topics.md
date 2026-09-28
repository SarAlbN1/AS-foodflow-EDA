# Tópicos Kafka

> **Estado:** HU-004 completada. Los tópicos y sus DLQ se crean automáticamente al levantar el entorno.

Contrato de referencia: [`docs/wiki/03-contratos/eventos.md`](../../docs/wiki/03-contratos/eventos.md).

## Tópicos del prototipo

| Tópico | Productor | Consumidores | Eventos | Clave de partición | Particiones | Retención | DLQ |
|---|---|---|---|---|---:|---|---|
| `orders.events` | Order Service | Payment Service (solo `OrderCreated`); consumidores futuros | `OrderCreated`, `OrderStatusChanged` | `orderId` | 3 | 7 días | `orders.events.dlq` |
| `payments.events` | Payment Service | Order Service, Notification Service | `PaymentApproved`, `PaymentRejected` | `orderId` | 3 | 7 días | `payments.events.dlq` |
| `notifications.events` | Notification Service | Observabilidad o consumidores futuros | `NotificationSent`, `NotificationFailed` | `orderId` | 3 | 7 días | `notifications.events.dlq` |

| DLQ | La alimenta | Particiones | Retención |
|---|---|---:|---|
| `orders.events.dlq` | El consumidor de `orders.events` que no puede procesar un evento (Payment Service) | 3 | 14 días |
| `payments.events.dlq` | Los consumidores de `payments.events` (Order Service, Notification Service) | 3 | 14 días |
| `notifications.events.dlq` | Consumidores futuros de `notifications.events` | 3 | 14 días |

- **Factor de replicación:** 1 (un solo broker en local).
- **Clave de partición:** siempre `aggregateId = orderId` (regla 11, ADR-04): todos los eventos de un pedido van a la misma partición y conservan su orden.
- **Grupos de consumidores:** `<servicio>.<tópico>`, por ejemplo `payment-service.orders` (página [Eventos](../../docs/wiki/03-contratos/eventos.md)).
- **Las DLQ usan las mismas 3 particiones que su tópico**, porque el publicador de DLQ de Spring Kafka conserva por defecto la partición del registro original.
- Un fallo de negocio del proveedor (`FALLIDA`) **no** va a DLQ (regla 10). A una DLQ solo llegan eventos no procesables: mensaje corrupto, esquema o versión no soportada, o un error transitorio que agotó sus reintentos.

## Cómo se crean

La única declaración de tópicos de la infraestructura es [`scripts/create-topics.sh`](scripts/create-topics.sh). Compose la ejecuta en el contenedor de un solo uso `kafka-init`, que:

1. espera a que `kafka` esté `healthy` (`depends_on: condition: service_healthy`);
2. crea cada tópico y su `<tópico>.dlq` con `kafka-topics.sh --create --if-not-exists` (idempotente);
3. lista los tópicos y termina con código 0.

El broker tiene `auto.create.topics.enable=false`: ningún tópico se crea de forma implícita por error de escritura.

```bash
# Desde la raíz del repositorio: levanta Kafka, las bases y crea los tópicos.
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml up -d

# Resultado de la creación (debe terminar con "exited (0)").
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml logs kafka-init
```

Como Kafka no tiene volumen (ver [`infrastructure/compose/README.md`](../compose/README.md)), tras un `down` los tópicos desaparecen y el siguiente `up -d` los vuelve a crear.

## Cómo comprobar los tópicos

```bash
# Lista: deben aparecer los 3 tópicos y sus 3 DLQ.
docker exec foodflow-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Particiones y réplicas de un tópico.
docker exec foodflow-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic orders.events

# Retención efectiva (retention.ms=604800000 en principales, 1209600000 en DLQ).
docker exec foodflow-kafka /opt/kafka/bin/kafka-configs.sh --bootstrap-server localhost:9092 \
  --entity-type topics --entity-name orders.events --describe
```

## Cómo inspeccionar una DLQ durante la demostración

```bash
# Lee la DLQ desde el principio, mostrando clave, cabeceras y valor.
docker exec -it foodflow-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payments.events.dlq \
  --from-beginning \
  --property print.key=true \
  --property print.headers=true \
  --property print.timestamp=true
```

La clave es el `orderId`. Cuando la DLQ la alimente Spring Kafka (HU-602), las cabeceras `kafka_dlt-exception-message`, `kafka_dlt-original-topic` y `kafka_dlt-original-offset` indican el motivo del fallo y el origen del evento. `Ctrl+C` detiene el consumidor. Desde el host, sin `docker exec`, se usa el *bootstrap* `localhost:29092`.

## Nombres de tópicos en el código

Los servicios **no** escriben los nombres de los tópicos como literales repartidos por el código: los leen de su configuración (`application.properties` o variables de entorno), en un único punto por servicio, junto con el nombre de su DLQ. Lo implementan las HU que añaden productores y consumidores (HU-103 en adelante) y lo verifica `scripts/verify-architecture.sh` (HU-006).

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Eventos](../../docs/wiki/03-contratos/eventos.md) · [Comportamiento del flujo](../../docs/wiki/02-arquitectura/comportamiento-del-flujo.md)
