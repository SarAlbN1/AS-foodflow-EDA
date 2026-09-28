# services/notification-service — Notification Service

> **Estado:** esqueleto compilable (HU-001), sin funcionalidad de negocio. La funcionalidad la construyen las historias indicadas.

**Responsabilidad:** Consume el resultado del pago, registra la notificación, la envía al proveedor y publica `NotificationSent` o `NotificationFailed`. Único propietario de Notification DB y único que llama al proveedor.

**Historias que lo construyen:** HU-001, HU-301 a HU-305

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

> Mientras sea un esqueleto no expone HTTP ni se conecta a Kafka o PostgreSQL: `spring-boot:run` arranca el contexto de Spring y termina. El servidor web, el puerto y la conexión a su base los añaden sus HU.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
