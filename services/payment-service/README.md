# services/payment-service — Payment Service

> **Estado:** consume `OrderCreated` desde `orders.events` (HU-201). Todavía no crea ni persiste el pago (HU-202) ni publica su resultado (HU-203, HU-204).

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
| JSON ilegible, envelope incompleto, `eventVersion` no soportada, payload fuera de contrato | Lo registra como no procesable y **no** inicia ningún pago |

El `notificationContact` no se guarda en Payment DB —la tabla `payments` no tiene columnas de contacto—: solo viaja del evento de entrada al evento de pago (ADR-11, supuesto A-2).

### Deuda conocida hasta HU-602

Un evento no procesable debería ir a `orders.events.dlq` sin reintentos ([comportamiento del flujo](../../docs/wiki/02-arquitectura/comportamiento-del-flujo.md)). HU-201 todavía no publica en la DLQ: registra el fallo y confirma el offset, porque dejar de confirmarlo bloquearía la partición y con ella todos los eventos posteriores del mismo pedido. Los reintentos y la DLQ los añade HU-602.

## Configuración

Variables en [`.env.example`](../../.env.example):

| Variable | Por defecto | Para qué |
|---|---|---|
| `PAYMENT_SERVICE_PORT` | `8082` | Puerto HTTP local; solo sirve al health check |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Broker |
| `PAYMENT_ORDERS_CONSUMER_GROUP` | `payment-service.orders` | Grupo de consumidores propio del servicio |
| `ORDERS_TOPIC` | `orders.events` | Tópico de entrada; los tópicos se leen de configuración, nunca como literales |

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
