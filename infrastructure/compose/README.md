# infrastructure/compose — Docker Compose

**Responsabilidad:** `docker-compose.yml` con Kafka, tres PostgreSQL, servicios, gateway, frontend y mock.

**Historias que lo construyen:** HU-002 (Kafka y las tres bases), HU-004 (tópicos), HU-306 (mock del proveedor), HU-607 (servicios, gateway, frontend y `scripts/up.sh`)

**Reglas que aplican:** Cada servicio recibe únicamente la configuración de su propia base.

> **Estado:** completo. HU-002, HU-004 y HU-306 aportan Kafka (KRaft), sus tópicos y DLQ, las tres PostgreSQL y el proveedor simulado; HU-607 añade los tres servicios, el API Gateway, el frontend y los scripts `scripts/up.sh`, `scripts/down.sh` y `scripts/smoke-test.sh`.

## Qué levanta

| Servicio | Imagen | Puerto en el host | Propietario exclusivo |
|---|---|---|---|
| `kafka` | `apache/kafka:4.3.1` | `29092` (escucha externa) | — |
| `kafka-init` | `apache/kafka:4.3.1` | — (de un solo uso: crea los tópicos y termina) | — |
| `notification-provider` | `foodflow/notification-provider:0.0.1` (se construye desde `mocks/notification-provider`, base `eclipse-temurin:25-jdk`) | `8090` (`NOTIFICATION_PROVIDER_HOST_PORT`) | Lo invoca solo `notification-service` |
| `order-service` | `foodflow/order-service:0.0.1` | — (solo red interna, `8081`) | Único dueño de `order-db` |
| `payment-service` | `foodflow/payment-service:0.0.1` | — (solo red interna, `8082`) | Único dueño de `payment-db` |
| `notification-service` | `foodflow/notification-service:0.0.1` | — (solo red interna, `8083`) | Único dueño de `notification-db` y único que llama al proveedor |
| `api-gateway` | `foodflow/api-gateway:0.0.1` | `8080` (`GATEWAY_PORT`) | Punto de entrada del cliente |
| `frontend` | `foodflow/frontend:0.0.1` (Nginx `1.30.5-alpine` con la compilación de Angular) | `4200` | — |
| `order-db` | `postgres:18.6` | `5433` | `order-service` |
| `payment-db` | `postgres:18.6` | `5434` | `payment-service` |
| `notification-db` | `postgres:18.6` | `5435` | `notification-service` |

Las versiones se toman de [versiones.md](../../docs/wiki/04-implementacion/versiones.md) mediante las variables `KAFKA_IMAGE` y `POSTGRES_IMAGE`; nunca se usa `latest`.

**Imágenes propias (HU-607).** Los servicios y el gateway se construyen con una sola receta, [`infrastructure/docker/spring-boot-service.Dockerfile`](../docker/spring-boot-service.Dockerfile) (base `eclipse-temurin:25-jdk`), usando el directorio de cada componente como contexto: ninguno ve el código de otro (regla 8). El frontend usa [`infrastructure/docker/frontend.Dockerfile`](../docker/frontend.Dockerfile): compila con `node:24.21.0-alpine` y sirve los estáticos con `nginx:1.30.5-alpine`. Las pruebas no corren dentro de la imagen (`./mvnw verify` / `npm test` se ejecutan aparte).

**Solo el gateway y el frontend publican puertos.** Dentro de la red cada servicio recibe la URL interna de **su** base (`jdbc:postgresql://<servicio>-db:5432/...`) y `kafka:9092`; el gateway enruta a `http://order-service:8081` y `http://notification-service:8083`. El navegador llama a `http://localhost:8080`, la URL base compilada en `frontend/foodflow-web/src/app/core/api-config.ts`, así que si cambias `GATEWAY_PORT` también hay que cambiar esa URL.

Kafka corre en **modo KRaft**: un solo nodo con los roles `broker` y `controller`. Kafka 4.x no usa ZooKeeper. `auto.create.topics.enable` está en `false` a propósito: los tópicos se declaran de forma explícita en la HU-004.

**Tópicos (HU-004).** El contenedor `kafka-init` espera a que `kafka` esté `healthy`, crea `orders.events`, `payments.events` y `notifications.events` con sus DLQ (`<tópico>.dlq`) mediante [`infrastructure/kafka/scripts/create-topics.sh`](../kafka/scripts/create-topics.sh) y termina con código 0. En `docker compose ps -a` aparece como `exited (0)`: es lo esperado. Detalle, comprobación e inspección de DLQ: [`infrastructure/kafka/topics.md`](../kafka/topics.md).

## Aislamiento de las bases (reglas 2 y 3)

Cada base tiene **su propia base de datos, su propio usuario y su propia contraseña**, definidos en variables separadas de `.env`. Ningún contenedor recibe las credenciales de otra base. Las URL que cada servicio recibe en Compose (HU-607) son, y solo estas:

| Servicio | URL de SU base |
|---|---|
| `order-service` | `jdbc:postgresql://order-db:5432/${ORDER_DB_NAME}` |
| `payment-service` | `jdbc:postgresql://payment-db:5432/${PAYMENT_DB_NAME}` |
| `notification-service` | `jdbc:postgresql://notification-db:5432/${NOTIFICATION_DB_NAME}` |

Ninguna base publica ni consume eventos, y Kafka nunca escribe en PostgreSQL.

## Arranque

La forma habitual es `bash scripts/up.sh` (ver el [README raíz](../../README.md#todo-el-prototipo-con-un-comando-hu-607)): crea `.env` si falta, construye y espera a que todo esté `healthy`. El equivalente manual, desde la **raíz del repositorio**, con Docker o Podman con Compose disponible:

```bash
# 1. Crear el archivo de variables y cambiar las contraseñas de ejemplo.
cp .env.example .env

# 2. Construir y levantar todo, esperando a que quede healthy.
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml up -d --build --wait

# 3. Ver el estado de cada contenedor.
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml ps
```

`podman compose` acepta los mismos argumentos.

## Comprobación de salud

Todos los contenedores de larga duración declaran `healthcheck`, así que `docker compose ps` muestra `healthy` cuando están listos:

| Contenedor | Comprobación |
|---|---|
| `kafka` | `kafka-broker-api-versions.sh --bootstrap-server localhost:9092` responde |
| `*-db` | `pg_isready -U <usuario> -d <base>` responde |
| `*-service`, `api-gateway` | `GET /actuator/health/readiness` responde `200` (ver abajo) |
| `notification-provider`, `frontend` | No declaran `healthcheck`: `up --wait` los da por listos en cuanto están `running` |

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

### Health check de los servicios y del gateway (HU-604, A-10)

Los tres servicios y el API Gateway publican `GET /actuator/health/readiness` en su puerto
(contrato en [api-rest.md](../../docs/wiki/03-contratos/api-rest.md)); Compose lo usa para
decidir si el contenedor está `healthy`. La imagen `eclipse-temurin` no trae `curl`, así que el
`healthcheck` abre el socket con `bash` (`/dev/tcp`) y exige un `200` en la primera línea.

El gateway no tiene base ni Kafka: su health responde `UP` en cuanto arranca y no consulta a los
servicios a los que enruta (cada uno expone el suyo). Comprobación manual:

```bash
curl -i http://localhost:8080/actuator/health        # gateway
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml ps   # estado de todos
```

## Detener y recrear

Con los scripts: `bash scripts/down.sh` conserva los datos y `bash scripts/down.sh --limpiar` borra los volúmenes; después, `bash scripts/up.sh`. A mano:

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
volumen con `down -v`. El prototipo no usa Flyway.

## Solución de problemas

| Síntoma | Causa y solución |
|---|---|
| `port is already allocated` | Ya hay un PostgreSQL o Kafka en el host. Cambia `ORDER_DB_HOST_PORT`, `PAYMENT_DB_HOST_PORT`, `NOTIFICATION_DB_HOST_PORT` o `KAFKA_HOST_PORT` en `.env`. |
| Una base queda `unhealthy` | `docker compose logs order-db`. Si un script SQL falló, el esquema quedó a medias: `down -v` y volver a levantar. |
| Cambié un script SQL y no se aplicó | Los scripts solo corren al crear el volumen. Usa `down -v`. |
| Kafka no arranca tras cambiar `KAFKA_CLUSTER_ID` | Los datos del broker son efímeros (no hay volumen), así que basta con `down` y `up -d`. |
| Un cliente del host no conecta a Kafka | Usa `localhost:29092`, no `9092`. El puerto `9092` es la escucha interna de la red de Compose. |
| Un servicio ejecutado en el host (`./mvnw spring-boot:run`) crea pedidos pero no llegan eventos | Cargó `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` de `.env`, que solo resuelve dentro de Compose; el productor falla y solo lo registra. Exporta `KAFKA_BOOTSTRAP_SERVERS=localhost:29092` y detén antes el contenedor del mismo servicio (`docker stop foodflow-order-service`) para no tener dos consumidores ni dos productores. |
| Un servicio queda `unhealthy` con error de autenticación a su base | Cambiaste las contraseñas de `.env` después de crear los volúmenes: PostgreSQL guarda las del primer arranque. `bash scripts/down.sh --limpiar` y vuelve a levantar. |
| `up.sh` se detiene con «a .env le faltan variables» | Tu `.env` es anterior a variables nuevas de `.env.example`. Compose las dejaría en blanco sin fallar, y una variable vacía anula el valor por omisión de Spring (`ORDERS_TOPIC=""` pierde los `OrderCreated`). Añade las líneas que lista o ejecuta `bash scripts/up.sh --completar-env`; las que estén vacías hay que rellenarlas a mano. Con `docker compose` a mano, las variables críticas (`KAFKA_BOOTSTRAP_SERVERS`, tópicos, grupos de consumidores, `GATEWAY_PORT` y `NOTIFICATION_PROVIDER_URL`) llevan `:?` y detienen el arranque con su nombre. |
| `up.sh` falla por *timeout* la primera vez | La primera construcción descarga dependencias de Maven y npm. Reintenta con más margen: `UP_TIMEOUT=600 bash scripts/up.sh`. |

## Nota sobre la persistencia de Kafka

Las tres bases usan volúmenes con nombre (`foodflow-*-db-data`), así que sus datos sobreviven a un `down`.
**Kafka no monta volumen**: sus datos viven en la capa de escritura del contenedor, sobreviven a
`stop`/`start` pero no a un `down`. Es deliberado en el prototipo — los tópicos los recrea
`kafka-init` (HU-004) y la retención de 7 días solo importa dentro de una ejecución.
HU-607 lo mantiene así: `scripts/down.sh` deja Kafka vacío y `scripts/up.sh` recrea los tópicos.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md) · [Persistencia](../../docs/wiki/03-contratos/persistencia.md) · [Versiones](../../docs/wiki/04-implementacion/versiones.md)
