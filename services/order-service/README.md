# services/order-service — Order Service

> **Estado:** `POST /orders` crea y persiste el pedido en estado `CREADO` (HU-101), `GET /orders/{id}` lo consulta (HU-102), la creación publica `OrderCreated` en `orders.events` (HU-103) y `Idempotency-Key` impide crear dos pedidos con la misma solicitud (HU-107). El resto de la funcionalidad la construyen las historias indicadas.

**Responsabilidad:** Crea y consulta pedidos; publica `OrderCreated` y `OrderStatusChanged`; consume `PaymentApproved` y `PaymentRejected`. Único propietario de Order DB.

**Historias que lo construyen:** HU-001, HU-101 a HU-107, HU-604

**Reglas que aplican:** ADR-08, ADR-09, ADR-11. Sin llamadas REST a otros servicios.

## Estructura

Paquete base `com.foodflow.order` ([convenciones](../../docs/wiki/04-implementacion/convenciones.md)):

- `api`: controladores y DTO HTTP.
- `application`: casos de uso.
- `domain`: entidades, estados y reglas de dominio.
- `infrastructure.persistence`: repositorios y mapeos de base de datos.
- `infrastructure.messaging`: productores y consumidores Kafka.
- `config`: configuración.
- `validation`: validadores del pedido.

## Construir, ejecutar y probar

Requisitos: JDK 25 (Maven lo aporta el *wrapper*, versión 3.9.14). Versiones en [versiones.md](../../docs/wiki/04-implementacion/versiones.md).

Desde `services/order-service/`:

| Acción | Linux/macOS/Git Bash | Windows (PowerShell/cmd) |
|---|---|---|
| Compilar y ejecutar las pruebas | `./mvnw verify` | `mvnw.cmd verify` |
| Solo las pruebas | `./mvnw test` | `mvnw.cmd test` |
| Ejecutar | `./mvnw spring-boot:run` | `mvnw.cmd spring-boot:run` |
| Detener | `Ctrl+C` en la terminal | `Ctrl+C` en la terminal |

### Ejecutar con su base de datos

`spring-boot:run` necesita Order DB en marcha, porque Hibernate valida el mapeo contra el esquema
(`spring.jpa.hibernate.ddl-auto=validate`). Desde la raíz del repositorio:

```bash
cp .env.example .env            # una sola vez; reemplaza las contraseñas
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml up -d order-db
set -a && . ./.env && set +a    # exporta ORDER_DB_URL, ORDER_DB_USER y ORDER_DB_PASSWORD
cd services/order-service && ./mvnw spring-boot:run
```

Queda escuchando en `ORDER_SERVICE_PORT` (8081 por defecto). Prueba rápida:

```bash
curl -i -X POST http://localhost:8081/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerReference":"PED-0001","customerContact":"ana@foodflow.test",
       "notificationChannel":"EMAIL","total":45000.00,"paymentToken":"PAY-OK"}'

# Con el id de la respuesta anterior (cabecera Location):
curl -i http://localhost:8081/orders/<id>
```

La consulta devuelve `200` con la misma representación que la creación, `404` si el pedido no
existe y `400` si el identificador no es un UUID. Lee siempre Order DB: no pregunta a Payment
Service, a Notification Service ni a Kafka.

### Qué cubren las pruebas

`./mvnw verify` corre sin infraestructura: valida las reglas de entrada, el caso de uso con el
repositorio simulado y el contrato HTTP con MockMvc. La prueba de persistencia real
(`OrderServiceApplicationTests`) **se omite** si no está definida `ORDER_DB_URL`; para ejecutarla,
levanta Order DB y exporta las variables como arriba.

> Kafka todavía no se usa: `OrderCreated` se publica en HU-103. Order Service nunca llama a
> Payment Service por REST (regla arquitectónica 8).

## `Idempotency-Key` (HU-107)

La cabecera es **obligatoria** en `POST /orders`: sin ella la solicitud se rechaza con `400` antes de validar nada. Lo exige el informe técnico y lo declara `required: true` el contrato OpenAPI.

| Situación | Respuesta |
|---|---|
| Clave nueva | `201`. Se crea el pedido y se publica `OrderCreated` |
| Misma clave, mismo cuerpo | `201` con el **pedido original**. No se crea otro ni se publica otro evento |
| Misma clave, cuerpo distinto | `409` `IDEMPOTENCY_CONFLICT`. El pedido original no se toca |
| Sin cabecera o vacía | `400` `VALIDATION_ERROR` con `detail: 'Idempotency-Key: es obligatoria'` |

**Qué cuenta como «el mismo cuerpo».** La huella se calcula sobre los campos **ya validados**, no sobre los bytes recibidos. Así, reenviar el mismo pedido con otro formato de JSON —espacios, saltos de línea, otro orden de claves— o con el total escrito `45000` en vez de `45000.00` cuenta como reintento y no como conflicto. Si se hiciera sobre el texto crudo, un cliente que reformatease su JSON recibiría un `409` sin haber cambiado ningún dato.

**Dos solicitudes a la vez con la misma clave.** La clave primaria de `idempotency_keys` decide: la que pierde ve fallar su transacción entera —pedido incluido, porque ambas escrituras van juntas— y vuelve a leer, ya fuera de la transacción, para devolver el pedido que ganó. Por eso el caso de uso no es transaccional y la escritura vive en `OrderCreationTransaction`: si estuvieran en el mismo objeto, Spring no aplicaría el proxy y la relectura ocurriría sobre una transacción marcada para descarte.

Y por eso `IdempotencyKey` implementa `Persistable`: su identificador se asigna a mano, así que Spring Data la trataría como una entidad ya existente y haría `merge` en vez de `persist`. Con todas las columnas `updatable = false`, ese `merge` encontraría la fila de la otra solicitud y **no escribiría nada ni lanzaría excepción**: la perdedora confirmaría su transacción con un segundo pedido y un segundo `OrderCreated`. Declarar que la entidad es nueva hace que el `INSERT` llegue a la base y la clave primaria lo impida.

## Publicación de `OrderCreated` (HU-103)

Al crear un pedido, Order Service publica `OrderCreated` en `orders.events`. Payment Service lo consume y arranca el pago: **Order Service no llama a Payment Service** (reglas arquitectónicas 4 y 5).

| Qué | Cómo |
|---|---|
| Cuándo se publica | **Después del commit** de la transacción que persiste el pedido. Si la persistencia falla, no se publica nada |
| Clave del mensaje | El `orderId`, que es la clave de partición (regla 11, ADR-04): los eventos de un pedido conservan su orden |
| Garantías del productor | `acks=all` y `enable.idempotence=true`: un reintento interno del productor no duplica el registro |
| Contenido | Envelope de `contracts/events/v1/envelope.schema.json` con el payload de `order-created.schema.json`, incluido el snapshot `notificationContact` (ADR-11) |
| `correlationId` | El de la petición HTTP. Si llega sin cabecera o con un valor que no es UUID, se genera uno |

**El riesgo aceptado de ADR-08.** No hay Transactional Outbox. Si el commit sale bien y la publicación falla, el pedido queda en `CREADO` **sin evento y sin que nadie lo reconcilie**. Se registra un `ERROR` con el `orderId` y el `correlationId`, y ahí termina: no se reintenta desde la base ni existe tarea de recuperación. Es la decisión de ADR-08, no un olvido.

Por eso el productor tiene un tiempo límite (`ORDERS_PUBLISH_TIMEOUT_MS`, 10 s): la publicación ocurre después del commit, así que sin límite una espera larga retrasaría la respuesta de un pedido que **ya está creado**.

Ver los eventos con la infraestructura levantada:

```bash
docker exec foodflow-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic orders.events --from-beginning --max-messages 1
```

## Health check (HU-604)

Con el servicio en ejecución (`./mvnw spring-boot:run`, puerto `ORDER_SERVICE_PORT`):

| Comprobación | Comando |
|---|---|
| Estado agregado | `curl -i http://localhost:8081/actuator/health` |
| La aplicación arrancó | `curl -i http://localhost:8081/actuator/health/liveness` |
| Puede atender tráfico | `curl -i http://localhost:8081/actuator/health/readiness` |

`200` con `"status":"UP"` cuando está disponible; `503` con `"status":"DOWN"` cuando una dependencia esencial no responde; `readiness` incluye Order DB (`db`). Es el único grupo de endpoints de Actuator expuesto y no publica detalles ni credenciales. Contrato completo: [api-rest.md](../../docs/wiki/03-contratos/api-rest.md).

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
