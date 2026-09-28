# infrastructure/compose — Docker Compose

**Responsabilidad:** `docker-compose.yml` con Kafka, tres PostgreSQL, servicios, gateway, frontend y mock.

**Historias que lo construyen:** HU-002 (Kafka y las tres bases), HU-004 (tópicos), HU-607 (servicios, gateway, frontend, mock y `scripts/up.sh`)

**Reglas que aplican:** Cada servicio recibe únicamente la configuración de su propia base.

> **Estado:** HU-002 completada — Kafka (KRaft) y las tres PostgreSQL. Todavía **no** hay tópicos (HU-004) ni servicios, gateway, frontend o mock (HU-607).

## Qué levanta hoy

| Servicio | Imagen | Puerto en el host | Propietario exclusivo |
|---|---|---|---|
| `kafka` | `apache/kafka:4.3.1` | `29092` (escucha externa) | — |
| `order-db` | `postgres:18.6` | `5433` | `order-service` |
| `payment-db` | `postgres:18.6` | `5434` | `payment-service` |
| `notification-db` | `postgres:18.6` | `5435` | `notification-service` |

Las versiones se toman de [versiones.md](../../docs/wiki/04-implementacion/versiones.md) mediante las variables `KAFKA_IMAGE` y `POSTGRES_IMAGE`; nunca se usa `latest`.

Kafka corre en **modo KRaft**: un solo nodo con los roles `broker` y `controller`. Kafka 4.x no usa ZooKeeper. `auto.create.topics.enable` está en `false` a propósito: los tópicos se declaran de forma explícita en la HU-004.

## Aislamiento de las bases (reglas 2 y 3)

Cada base tiene **su propia base de datos, su propio usuario y su propia contraseña**, definidos en variables separadas de `.env`. Ningún contenedor recibe las credenciales de otra base. Las URL que cada servicio deberá recibir cuando exista (HU-607) son, y solo estas:

| Servicio | URL de SU base |
|---|---|
| `order-service` | `jdbc:postgresql://order-db:5432/${ORDER_DB_NAME}` |
| `payment-service` | `jdbc:postgresql://payment-db:5432/${PAYMENT_DB_NAME}` |
| `notification-service` | `jdbc:postgresql://notification-db:5432/${NOTIFICATION_DB_NAME}` |

Ninguna base publica ni consume eventos, y Kafka nunca escribe en PostgreSQL.

## Arranque

Desde la **raíz del repositorio**, con Docker o Podman con Compose disponible:

```bash
# 1. Crear el archivo de variables y cambiar las contraseñas de ejemplo.
cp .env.example .env

# 2. Levantar la infraestructura.
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml up -d

# 3. Esperar a que los cuatro contenedores estén "healthy".
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml ps
```

`podman compose` acepta los mismos argumentos.

> A partir de la HU-607 esto se envuelve en `scripts/up.sh` y `scripts/down.sh`.

## Comprobación de salud

Los cuatro contenedores declaran `healthcheck`, así que `docker compose ps` muestra `healthy` cuando están listos:

| Contenedor | Comprobación |
|---|---|
| `kafka` | `kafka-broker-api-versions.sh --bootstrap-server localhost:9092` responde |
| `*-db` | `pg_isready -U <usuario> -d <base>` responde |

Comprobación manual equivalente:

```bash
# Kafka responde y expone su cluster id.
docker exec foodflow-kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 | head -1

# Cada base responde y tiene su esquema creado.
docker exec foodflow-order-db        psql -U "$ORDER_DB_USER"        -d "$ORDER_DB_NAME"        -c '\dt'
docker exec foodflow-payment-db      psql -U "$PAYMENT_DB_USER"      -d "$PAYMENT_DB_NAME"      -c '\dt'
docker exec foodflow-notification-db psql -U "$NOTIFICATION_DB_USER" -d "$NOTIFICATION_DB_NAME" -c '\dt'
```

Tablas esperadas: `orders`, `idempotency_keys` y `processed_events` en Order DB; `payments` y `processed_events` en Payment DB; `notifications` y `processed_events` en Notification DB.

Comprobar que **no** hay acceso cruzado (el usuario de una base no entra en otra):

```bash
docker exec foodflow-payment-db psql -U "$ORDER_DB_USER" -d "$PAYMENT_DB_NAME" -c 'select 1'   # debe fallar
```

## Detener y recrear

```bash
# Detener conservando los datos de PostgreSQL.
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml down

# Volver a levantar: los datos de las bases siguen ahí.
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml up -d

# Recrear desde cero: borra los volúmenes y vuelve a ejecutar los scripts de esquema.
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml down -v
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml up -d
```

No hay ningún paso manual adicional: el esquema de cada base lo crean los scripts de
[`infrastructure/postgres/<db>/`](../postgres), que el *entrypoint* de PostgreSQL ejecuta
automáticamente la primera vez que se crea el volumen.

## Esquema de las bases

Los scripts SQL viven versionados en `infrastructure/postgres/<db>/` y se montan en
`/docker-entrypoint-initdb.d` en modo solo lectura. Se ejecutan **una única vez**, al crear
el volumen. Para aplicar un cambio de esquema durante el desarrollo hay que recrear el
volumen con `down -v`. Flyway es opcional (HU-010).

## Solución de problemas

| Síntoma | Causa y solución |
|---|---|
| `port is already allocated` | Ya hay un PostgreSQL o Kafka en el host. Cambia `ORDER_DB_HOST_PORT`, `PAYMENT_DB_HOST_PORT`, `NOTIFICATION_DB_HOST_PORT` o `KAFKA_HOST_PORT` en `.env`. |
| Una base queda `unhealthy` | `docker compose logs order-db`. Si un script SQL falló, el esquema quedó a medias: `down -v` y volver a levantar. |
| Cambié un script SQL y no se aplicó | Los scripts solo corren al crear el volumen. Usa `down -v`. |
| Kafka no arranca tras cambiar `KAFKA_CLUSTER_ID` | Los datos del broker son efímeros (no hay volumen), así que basta con `down` y `up -d`. |
| Un cliente del host no conecta a Kafka | Usa `localhost:29092`, no `9092`. El puerto `9092` es la escucha interna de la red de Compose. |

## Nota sobre la persistencia de Kafka

Las tres bases usan volúmenes con nombre (`foodflow-*-db-data`), así que sus datos sobreviven a un `down`.
**Kafka no monta volumen**: sus datos viven en la capa de escritura del contenedor, sobreviven a
`stop`/`start` pero no a un `down`. Es deliberado en el prototipo — los tópicos los recrea la
automatización de la HU-004 y la retención de 7 días solo importa dentro de una ejecución.
Si más adelante hace falta conservar el log entre recreaciones, es un cambio para la HU-607.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md) · [Persistencia](../../docs/wiki/03-contratos/persistencia.md) · [Versiones](../../docs/wiki/04-implementacion/versiones.md)
