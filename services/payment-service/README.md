# services/payment-service — Payment Service

> **Estado:** consume `OrderCreated` (HU-201) y resuelve y persiste el pago en Payment DB (HU-202). Todavía no publica el resultado: `PaymentApproved` es HU-203 y `PaymentRejected` es HU-204.

**Responsabilidad:** Consume `OrderCreated`, decide el pago de forma determinista (`PAY-OK` / `PAY-FAIL`) y publica `PaymentApproved` o `PaymentRejected`. Único propietario de Payment DB.

**Historias que lo construyen:** HU-001, HU-201 a HU-204, HU-604

**Reglas que aplican:** ADR-09, ADR-10. Ignora eventos de `orders.events` distintos de `OrderCreated`.

## Estructura

Paquete base `com.foodflow.payment` ([convenciones](../../docs/wiki/04-implementacion/convenciones.md)):

- `api`: controladores y DTO HTTP.
- `application`: casos de uso.
- `domain`: entidades, estados y reglas de dominio.
- `infrastructure.persistence`: repositorios y mapeos de base de datos.
- `infrastructure.messaging`: productores y consumidores Kafka.
- `config`: configuración.

## Construir, ejecutar y probar

Requisitos: JDK 25 (Maven lo aporta el *wrapper*, versión 3.9.14). Versiones en [versiones.md](../../docs/wiki/04-implementacion/versiones.md).

Desde `services/payment-service/`:

| Acción | Linux/macOS/Git Bash | Windows (PowerShell/cmd) |
|---|---|---|
| Compilar y ejecutar las pruebas | `./mvnw verify` | `mvnw.cmd verify` |
| Solo las pruebas | `./mvnw test` | `mvnw.cmd test` |
| Ejecutar | `./mvnw spring-boot:run` | `mvnw.cmd spring-boot:run` |
| Detener | `Ctrl+C` en la terminal | `Ctrl+C` en la terminal |

> Desde HU-604 el servicio arranca un servidor web y `spring-boot:run` queda en ejecución. Todavía no se conecta a Kafka ni a PostgreSQL: esas conexiones las añaden sus HU.

## Health check (HU-604)

Con el servicio en ejecución (`./mvnw spring-boot:run`):

| Comprobación | Comando |
|---|---|
| Estado agregado | `curl -i http://localhost:8080/actuator/health` |
| La aplicación arrancó | `curl -i http://localhost:8080/actuator/health/liveness` |
| Puede atender tráfico | `curl -i http://localhost:8080/actuator/health/readiness` |

`200` con `"status":"UP"` cuando está disponible; `503` con `"status":"DOWN"` cuando una dependencia esencial no responde. Es el único grupo de endpoints de Actuator expuesto y no publica detalles ni credenciales. Contrato completo: [api-rest.md](../../docs/wiki/03-contratos/api-rest.md).

## Consumo de `orders.events` (HU-201)

Payment Service se suscribe a `orders.events` en su propio grupo `payment-service.orders`. El pago lo dispara el evento: **nunca** consulta Order DB ni llama por REST a Order Service (reglas arquitectónicas 2, 4 y 5).

| Situación | Qué hace |
|---|---|
| `OrderCreated` v1 válido | Extrae `orderId`, `total`, `currency`, `paymentToken`, `correlationId` y el snapshot `notificationContact`, y entrega la orden a la capa de aplicación |
| `OrderStatusChanged` u otro tipo | Lo ignora con `DEBUG` y confirma el offset, sin error y sin DLQ |
| JSON ilegible, envelope incompleto, `eventVersion` no soportada, payload fuera de contrato (moneda, `paymentToken`, `aggregateId`, más de dos decimales en `total`) | Lo registra como no procesable y **no** inicia ningún pago |

> **Dependencia.** El servicio usa `spring-boot-starter-kafka`, no `spring-kafka` a secas. En Spring Boot 4 la configuración automática de Kafka vive en su propio módulo: sin ella el `@KafkaListener` no se registra y las propiedades `spring.kafka.*` se ignoran, así que el servicio arrancaría sin consumir nada. `OrderCreatedListenerRegistrationTest` lo comprueba mirando el registro de contenedores.

El `notificationContact` no se guarda en Payment DB —la tabla `payments` no tiene columnas de contacto—: solo viaja del evento de entrada al evento de pago (ADR-11, supuesto A-2).

### Deuda conocida hasta HU-602

Dos huecos que cierra HU-602, la historia de reintentos y DLQ. Se dejan aquí por escrito porque hasta entonces el comportamiento por omisión no es el que describe la wiki.

**1. Un evento no procesable no va a la DLQ.** Debería ir a `orders.events.dlq` sin reintentos ([comportamiento del flujo](../../docs/wiki/02-arquitectura/comportamiento-del-flujo.md)). Hoy se registra el fallo y se confirma el offset, porque no confirmarlo bloquearía la partición y con ella todos los eventos posteriores del mismo pedido.

**2. Un fallo transitorio de Payment DB pierde el pago.** Si la base no responde un momento, la excepción no es la de evento no procesable y sube al contenedor de Kafka. Como todavía no hay `CommonErrorHandler` propio, actúa el `DefaultErrorHandler` de Spring Kafka con su `FixedBackOff(0, 9)`: **diez intentos seguidos sin espera** y, agotados, confirma el offset y sigue. Un corte de unos segundos deja el pedido en `CREADO` para siempre, sin rastro en ninguna DLQ.

La política que corresponde está fijada en [convenciones](../../docs/wiki/04-implementacion/convenciones.md): 3 intentos, espera inicial 1 s, multiplicador 2. La aplica HU-602 junto con el publicador de DLQ, para no partir esa configuración entre dos historias.

## Resolución y persistencia del pago (HU-202)

El pago es determinista (ADR-10): el resultado se conoce al procesar `OrderCreated`, así que no hay estado pendiente y un pago existe solo cuando ya está resuelto.

| `paymentToken` del pedido | `payments.status` | `payments.reasonCode` |
|---|---|---|
| `PAY-OK` | `APROBADO` | nulo |
| `PAY-FAIL` | `RECHAZADO` | `PAGO_RECHAZADO_POR_TOKEN` |
| cualquier otro | no se crea pago | el evento es no procesable y lo descarta el consumidor (HU-201) |

`transactionReference` tiene el formato `TXN-<yyyyMMdd>-<orderId>` y nunca es nula, tampoco cuando el pago se rechaza: sirve para rastrear el intento. Es una referencia propia del servicio, no de una pasarela externa; el prototipo no integra ninguna.

**Un pedido, un pago.** `payments.order_id` es único: reprocesar el mismo `OrderCreated` devuelve el pago que ya existía y no cobra de nuevo.

Dos consumidores del grupo no pueden procesar el mismo pedido a la vez: la clave de partición es el `orderId` (regla 11, ADR-04), así que todos sus eventos van a la misma partición y la atiende un solo consumidor. El índice único queda como última garantía de la base, no como el mecanismo del que depende el caso normal. El registro del `eventId` en `processed_events` (ADR-09) lo añade HU-601.

El esquema lo crean los scripts de [`infrastructure/postgres/payment-db/`](../../infrastructure/postgres/payment-db) y Hibernate solo lo valida (`ddl-auto=validate`).

`payments.status` tiene su restricción `CHECK` (`payments_status_valido`), igual que `orders` y `notifications`: la base rechaza un estado fuera del catálogo aunque el código se equivoque. **Se aplica al crear el volumen**, así que en una base ya existente hay que recrearla (`down -v`) o añadirla a mano:

```sql
ALTER TABLE payments ADD CONSTRAINT payments_status_valido CHECK (status IN ('APROBADO','RECHAZADO'));
```

**Payment DB entra en `readiness`.** El grupo `readiness` contiene solo `readinessState` por omisión, así que la HU que trae la base añade también `management.endpoint.health.group.readiness.include=readinessState,db`. Sin esa línea el servicio respondería `200` en `/actuator/health/readiness` con su base caída, que es justo lo que un health check de Compose consulta para decidir si el contenedor está listo.

### Pruebas con la base real

`PaymentServiceApplicationTests` persiste contra Payment DB y solo corre cuando `PAYMENT_DB_URL` está definida, para que `./mvnw verify` funcione sin infraestructura:

```bash
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml up -d payment-db
set -a && . ./.env && set +a
export PAYMENT_DB_URL="jdbc:postgresql://localhost:$PAYMENT_DB_HOST_PORT/$PAYMENT_DB_NAME"
cd services/payment-service && ./mvnw verify
```

## Configuración

Variables en [`.env.example`](../../.env.example):

| Variable | Por defecto | Para qué |
|---|---|---|
| `PAYMENT_SERVICE_PORT` | `8082` | Puerto HTTP local; solo sirve al health check |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Broker |
| `PAYMENT_ORDERS_CONSUMER_GROUP` | `payment-service.orders` | Grupo de consumidores propio del servicio |
| `ORDERS_TOPIC` | `orders.events` | Tópico de entrada; los tópicos se leen de configuración, nunca como literales |
| `PAYMENT_DB_URL` | `jdbc:postgresql://localhost:5434/paymentdb` | URL de **su** base; el servicio no recibe la de ninguna otra (regla 2) |
| `PAYMENT_DB_USER`, `PAYMENT_DB_PASSWORD` | — | Credenciales propias de Payment DB |

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
