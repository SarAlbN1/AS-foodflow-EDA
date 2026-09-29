# services/notification-service — Notification Service

> **Estado:** consume el resultado del pago desde `payments.events` y crea la notificación en `PENDIENTE` (HU-301). El envío al proveedor es HU-302, y el registro de su resultado, HU-303 y HU-304.

**Responsabilidad:** Consume el resultado del pago, registra la notificación, la envía al proveedor y publica `NotificationSent` o `NotificationFailed`. Único propietario de Notification DB y único que llama al proveedor.

**Historias que lo construyen:** HU-001, HU-301 a HU-305, HU-604

**Reglas que aplican:** ADR-09, ADR-11. Un fallo del proveedor deja la notificación en `FALLIDA`, no en DLQ.

## Estructura

Paquete base `com.foodflow.notification` ([convenciones](../../docs/wiki/04-implementacion/convenciones.md)):

- `api`: controladores y DTO HTTP.
- `application`: casos de uso.
- `domain`: entidades, estados y reglas de dominio.
- `infrastructure.persistence`: repositorios y mapeos de base de datos.
- `infrastructure.messaging`: productores y consumidores Kafka.
- `config`: configuración.
- `infrastructure.provider`: cliente HTTP del proveedor (único punto con llamadas HTTP salientes).

## Construir, ejecutar y probar

Requisitos: JDK 25 (Maven lo aporta el *wrapper*, versión 3.9.14). Versiones en [versiones.md](../../docs/wiki/04-implementacion/versiones.md).

Desde `services/notification-service/`:

| Acción | Linux/macOS/Git Bash | Windows (PowerShell/cmd) |
|---|---|---|
| Compilar y ejecutar las pruebas | `./mvnw verify` | `mvnw.cmd verify` |
| Solo las pruebas | `./mvnw test` | `mvnw.cmd test` |
| Ejecutar | `./mvnw spring-boot:run` | `mvnw.cmd spring-boot:run` |
| Detener | `Ctrl+C` en la terminal | `Ctrl+C` en la terminal |

> Desde HU-301 el servicio necesita Notification DB y el broker para arrancar completo. `./mvnw verify` funciona sin infraestructura: las pruebas que la necesitan se omiten solas (ver [Pruebas con la base real](#pruebas-con-la-base-real)).

## Health check (HU-604)

Con el servicio en ejecución (`./mvnw spring-boot:run`):

| Comprobación | Comando |
|---|---|
| Estado agregado | `curl -i http://localhost:8083/actuator/health` |
| La aplicación arrancó | `curl -i http://localhost:8083/actuator/health/liveness` |
| Puede atender tráfico | `curl -i http://localhost:8083/actuator/health/readiness` |

`200` con `"status":"UP"` cuando está disponible; `503` con `"status":"DOWN"` cuando una dependencia esencial no responde. Es el único grupo de endpoints de Actuator expuesto y no publica detalles ni credenciales. Contrato completo: [api-rest.md](../../docs/wiki/03-contratos/api-rest.md).

## Consumo de `payments.events` (HU-301)

Notification Service se suscribe a `payments.events` en su propio grupo `notification-service.payments`. Order Service consume **el mismo tópico en otro grupo**: los dos reciben el resultado del pago de forma independiente y ninguno depende del otro (reglas 6 y 12, decisión [D-6](../../docs/wiki/02-arquitectura/divergencias-informe-wiki.md)).

| Situación | Qué hace |
|---|---|
| `PaymentApproved` o `PaymentRejected` v1 válido | Extrae `orderId`, `paymentId`, `amount`, `currency`, `correlationId` y el snapshot `notificationContact`, y crea la notificación |
| Otro tipo de evento | Lo ignora con `DEBUG` y confirma el offset, sin error y sin DLQ |
| JSON ilegible, envelope incompleto, `eventVersion` no soportada, payload fuera de contrato (moneda, `aggregateId`, campo distintivo del tipo ausente) | Lo registra como no procesable y **no** crea ninguna notificación |

**No consulta ninguna otra base ni servicio** (criterio 5). El canal y el destino salen del `notificationContact` que viaja en el evento (ADR-11); por eso el texto tampoco puede nombrar el `customerReference` del pedido, que no viaja en los eventos de pago.

> **Dependencia.** El servicio usa `spring-boot-starter-kafka`, no `spring-kafka` a secas. En Spring Boot 4 la configuración automática de Kafka vive en su propio módulo: sin ella el `@KafkaListener` no se registra y las propiedades `spring.kafka.*` se ignoran, así que el servicio arrancaría sin consumir nada. `PaymentResultListenerRegistrationTest` lo comprueba mirando el registro de contenedores.

### Qué dice el mensaje

El texto habla **del resultado del pago**, nunca del estado del pedido:

| Resultado | `content` |
|---|---|
| Aprobado | `Tu pago de 45.900,00 COP fue aprobado. Estamos preparando tu pedido.` |
| Rechazado | `Tu pago de 45.900,00 COP fue rechazado. No se realizo ningun cobro.` |

La notificación puede salir **antes** de que Order Service registre el cambio de estado, porque los dos reaccionan al mismo evento en paralelo. Un mensaje que afirmara «tu pedido está PAGADO» podría ser falso en ese instante; uno que habla del pago no lo es nunca. El `content` **no incluye el destino**: ya está en su propia columna y repetirlo lo duplicaría en el contrato REST, que lo devuelve enmascarado. Contrato completo: [proveedor-notificaciones.md](../../docs/wiki/03-contratos/proveedor-notificaciones.md).

### Una notificación por evento (ADR-09)

El `eventId` se registra en `processed_events` en la **misma transacción local** que la notificación: o quedan las dos cosas o no queda ninguna. Una reentrega de Kafka encuentra el `eventId` ya registrado, lo ignora con `INFO` y no produce un segundo correo.

El offset se confirma a mano (`ack-mode=manual`) y solo después del commit local. El evento no procesable también confirma: no confirmarlo bloquearía la partición y con ella todos los eventos posteriores del mismo pedido. La DLQ y los reintentos con espera los añade HU-602, igual que en payment-service.

`correlationId` viaja en el envelope y se propaga a los registros y a los eventos que publicará HU-303/HU-304, pero **no se persiste**: la tabla `notifications` de [persistencia.md](../../docs/wiki/03-contratos/persistencia.md) no tiene esa columna.

**Notification DB entra en `readiness`.** El grupo `readiness` contiene solo `readinessState` por omisión, así que la HU que trae la base añade también `management.endpoint.health.group.readiness.include=readinessState,db`. Sin esa línea el servicio respondería `200` en `/actuator/health/readiness` con su base caída.

### Pruebas con la base real

`NotificationServiceApplicationTests` persiste contra Notification DB y solo corre cuando `NOTIFICATION_DB_URL` está definida, para que `./mvnw verify` funcione sin infraestructura:

```bash
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml up -d notification-db
set -a && . ./.env && set +a
export NOTIFICATION_DB_URL="jdbc:postgresql://localhost:$NOTIFICATION_DB_HOST_PORT/$NOTIFICATION_DB_NAME"
cd services/notification-service && ./mvnw verify
```

## Configuración

Variables en [`.env.example`](../../.env.example):

| Variable | Por defecto | Para qué |
|---|---|---|
| `NOTIFICATION_SERVICE_PORT` | `8083` | Puerto HTTP local; el gateway (HU-402) enruta hacia él |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Broker |
| `NOTIFICATION_PAYMENTS_CONSUMER_GROUP` | `notification-service.payments` | Grupo de consumidores propio del servicio |
| `PAYMENTS_TOPIC` | `payments.events` | Tópico de entrada; los tópicos se leen de configuración, nunca como literales |
| `NOTIFICATION_DB_URL` | `jdbc:postgresql://localhost:5435/notificationdb` | URL de **su** base; el servicio no recibe la de ninguna otra (regla 2) |
| `NOTIFICATION_DB_USER`, `NOTIFICATION_DB_PASSWORD` | — | Credenciales propias de Notification DB |

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
