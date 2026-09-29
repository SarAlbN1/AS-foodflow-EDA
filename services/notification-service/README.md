# services/notification-service — Notification Service

> **Estado:** consume el resultado del pago desde `payments.events`, crea la notificación en `PENDIENTE` (HU-301), la entrega al proveedor externo (HU-302) y registra el resultado del envío: `NotificationSent` (HU-303) o `NotificationFailed` (HU-304).

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
| Aprobado | `Tu pago de 45.900,00 COP fue aprobado.` |
| Rechazado | `Tu pago de 45.900,00 COP fue rechazado. No se realizó ningún cobro.` |

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

## Envío al proveedor externo (HU-302)

`infrastructure.provider` es el **único paquete del sistema con un cliente HTTP saliente** (regla 7). Ni Order ni Payment tienen uno, y dentro de este servicio ningún otro paquete puede tenerlo: `ArchitectureTest` falla si aparece un `RestClient`, un `WebClient`, un `RestTemplate` o un `HttpClient` fuera de ahí.

Contrato: `POST /v1/messages` con `{channel, destination, content, correlationId}` → `202` con `providerReference` ([proveedor-notificaciones.md](../../docs/wiki/03-contratos/proveedor-notificaciones.md)). Se envía además el `notificationId` en la cabecera `Idempotency-Key`, como fija el informe.

| Qué | Cómo |
|---|---|
| Timeouts | **Explícitos**: 2 s de conexión y 3 s de lectura. Sin ellos el cliente esperaría indefinidamente y bloquearía el hilo del consumidor de Kafka, deteniendo el consumo de todos los demás eventos |
| Reintentos | 3 intentos con espera de 500 ms que se duplica (500 ms y 1 s). Configurables por variable de entorno |
| Circuit Breaker | **No**. Está en la lista de no implementar: con un único proveedor no hay riesgo de fallo en cascada que lo justifique |
| Un `5xx`, un tiempo agotado o una conexión rechazada | Se reintentan, y al agotarse producen un resultado con su `failureCode` |
| Un `4xx` | **No se reintenta**: dice que el mensaje no es aceptable, y repetirlo produce el mismo rechazo |
| Un `2xx` con un cuerpo que no se entiende | Es una **aceptación**: el mensaje ya fue aceptado. Se registra sin `providerReference` y con un aviso |
| Una respuesta fuera de contrato (un `3xx`) o un fallo del cliente al tratarla | `RESPUESTA_INESPERADA`, sin reintento: no cambia por repetir la petición |
| Un fallo | Devuelve un resultado, **no lanza excepción**. Es resultado de negocio: el offset se confirma, no va a DLQ (regla 10) y el pago sigue registrado en Order Service (regla 12) |

El catálogo de `failureCode` está en el [contrato del proveedor](../../docs/wiki/03-contratos/proveedor-notificaciones.md#catálogo-de-failurecode-hu-302) y lo implementa el enum `DeliveryFailure`.

**Por qué el envío va fuera de la transacción.** `NotificationDispatcher` separa los dos pasos a propósito: `NotificationApplicationService` registra la notificación en su transacción y el envío ocurre después. Con 3 intentos y 3 s de lectura, hacerlo dentro mantendría la conexión a Notification DB tomada hasta 9 s por notificación. Y llamar a un método `@Transactional` desde otro método de la misma clase no pasa por el proxy de Spring, así que la transacción no existiría: son dos componentes, no dos métodos.

### Pruebas del envío

| Prueba | Contra qué | Qué cubre |
|---|---|---|
| `ProviderNotificationSenderTests` | Un servidor HTTP real del JDK | Cuerpo y cabecera que recibe el proveedor, reintento del fallo transitorio, agotamiento, el `4xx` sin reintento, el timeout de lectura y el proveedor inalcanzable |
| `ProviderDeliveryIntegrationTest` | **El proveedor simulado de HU-306**, por red | Los tres modos por destino: `*@flaky.test` entrega al tercer intento, `*@fail.test` agota, `*@slow.test` agota el tiempo de lectura |

La segunda se omite si `NOTIFICATION_PROVIDER_URL` no está definida:

```bash
set -a && . ./.env && set +a
docker compose -f infrastructure/compose/docker-compose.yml up -d notification-provider
export NOTIFICATION_PROVIDER_URL="http://localhost:${NOTIFICATION_PROVIDER_HOST_PORT:-8090}"
cd services/notification-service && ./mvnw verify
```

## Registro de la entrega (HU-303)

Cuando el proveedor acepta el mensaje, la notificación pasa de `PENDIENTE` a `ENVIADA` con los intentos que costó, y se publica **`NotificationSent`** en `notifications.events`.

| Qué | Cómo |
|---|---|
| Transición | Solo desde `PENDIENTE`. Cualquier otra se ignora con `WARN`, sin error y sin evento ([comportamiento del flujo](../../docs/wiki/02-arquitectura/comportamiento-del-flujo.md)) |
| Cuándo se publica | **Después del commit** de la transacción que persiste el cambio. Una notificación que no llega a quedar `ENVIADA` no produce evento |
| Clave del mensaje | El `orderId`, que es la clave de partición (regla 11, ADR-04) |
| Garantías del productor | `acks=all` y `enable.idempotence=true`, igual que en order y payment |
| Qué lleva el evento | `notificationId`, `orderId`, `paymentId`, `channel` y `providerReference`. **No lleva el destino ni el contenido**: dice que se envió, no lo que se envió |
| Un fallo al publicar | Se registra como `ERROR` y ahí termina. No revierte nada ni hace fallar al consumidor |

**Quién consume `notifications.events`: nadie del prototipo.** Está para observabilidad y consumidores futuros ([eventos](../../docs/wiki/03-contratos/eventos.md)). Que no tenga consumidor no lo hace prescindible: es el registro de que el flujo terminó.

**El riesgo aceptado de ADR-08, otra vez.** Si el commit sale bien y la publicación falla, la notificación queda `ENVIADA` y nadie se entera fuera del log. Es la misma ventana de escritura dual que `OrderCreated` y los eventos de pago; no hay Outbox ni reconciliación.

**Por qué la entidad se vuelve a leer.** `registrarEnvio` busca la notificación por su identificador en vez de escribir sobre la que trae quien llama: esa viene de la transacción que la creó y está desligada, así que guardarla sería una fusión y no una actualización.

## Entrega fallida (HU-304)

Agotados los reintentos, la notificación pasa a `FALLIDA` con su `failureCode` y se publica **`NotificationFailed`** con el motivo y los intentos.

**Es un resultado de negocio, no una avería.** El proveedor contestó y dijo que no. De ahí las tres consecuencias que el criterio 1 y la regla 10 exigen y que se verificaron en ejecución:

| | |
|---|---|
| El evento de pago | **No** va a DLQ, y su offset se confirma |
| El pago | Sigue `APROBADO` en Payment DB |
| El pedido | Conserva su estado final (`PAGADO`) |

Si en vez de registrarlo se lanzara, el consumidor no confirmaría el offset, Kafka reentregaría el evento de pago y la idempotencia de ADR-09 lo descartaría: la notificación se quedaría en `PENDIENTE` para siempre y sin rastro del motivo.

`NotificationFailed` lleva `failureCode` y `attempts` para poder diagnosticar sin abrir la base (criterio 4). El catálogo de motivos está en [proveedor-notificaciones.md](../../docs/wiki/03-contratos/proveedor-notificaciones.md).

Se demuestra con un pedido cuyo contacto sea `*@fail.test`, el modo del proveedor simulado que responde `503` siempre.

## Configuración

Variables en [`.env.example`](../../.env.example):

| Variable | Por defecto | Para qué |
|---|---|---|
| `NOTIFICATION_SERVICE_PORT` | `8083` | Puerto HTTP local; el gateway (HU-402) enruta hacia él |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Broker |
| `NOTIFICATION_PAYMENTS_CONSUMER_GROUP` | `notification-service.payments` | Grupo de consumidores propio del servicio |
| `PAYMENTS_TOPIC` | `payments.events` | Tópico de entrada; los tópicos se leen de configuración, nunca como literales |
| `NOTIFICATIONS_TOPIC` | `notifications.events` | Tópico de salida de `NotificationSent` y `NotificationFailed` |
| `NOTIFICATIONS_PUBLISH_TIMEOUT_MS` | `10000` | Cuánto se espera a que Kafka confirme antes de darlo por fallido |
| `NOTIFICATION_DB_URL` | `jdbc:postgresql://localhost:5435/notificationdb` | URL de **su** base; el servicio no recibe la de ninguna otra (regla 2) |
| `NOTIFICATION_DB_USER`, `NOTIFICATION_DB_PASSWORD` | — | Credenciales propias de Notification DB |
| `NOTIFICATION_PROVIDER_URL` | `http://localhost:8090` | Proveedor externo. En la red de Compose, `http://notification-provider:8080` |
| `NOTIFICATION_PROVIDER_CONNECT_TIMEOUT` | `2s` | Tiempo de conexión con el proveedor |
| `NOTIFICATION_PROVIDER_READ_TIMEOUT` | `3s` | Tiempo de lectura; menor que la espera de `*@slow.test` |
| `NOTIFICATION_PROVIDER_MAX_ATTEMPTS` | `3` | Intentos contra el proveedor |
| `NOTIFICATION_PROVIDER_INITIAL_BACKOFF` | `500ms` | Espera del primer reintento |
| `NOTIFICATION_PROVIDER_BACKOFF_MULTIPLIER` | `2` | Factor por el que crece la espera |

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
