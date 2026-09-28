# services/order-service — Order Service

> **Estado:** esqueleto compilable (HU-001), sin funcionalidad de negocio. La funcionalidad la construyen las historias indicadas.

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

> Desde HU-604 el servicio arranca un servidor web y `spring-boot:run` queda en ejecución. Todavía no se conecta a Kafka ni a PostgreSQL: esas conexiones las añaden sus HU.

## Health check (HU-604)

Con el servicio en ejecución (`./mvnw spring-boot:run`):

| Comprobación | Comando |
|---|---|
| Estado agregado | `curl -i http://localhost:8080/actuator/health` |
| La aplicación arrancó | `curl -i http://localhost:8080/actuator/health/liveness` |
| Puede atender tráfico | `curl -i http://localhost:8080/actuator/health/readiness` |

`200` con `"status":"UP"` cuando está disponible; `503` con `"status":"DOWN"` cuando una dependencia esencial no responde. Es el único grupo de endpoints de Actuator expuesto y no publica detalles ni credenciales. Contrato completo: [api-rest.md](../../docs/wiki/03-contratos/api-rest.md).

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
