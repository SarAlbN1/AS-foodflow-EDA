# gateway/api-gateway — API Gateway

> **Estado:** esqueleto compilable (HU-001), sin funcionalidad de negocio. La funcionalidad la construyen las historias indicadas.

**Responsabilidad:** Punto de entrada REST. Enruta hacia el servicio propietario sin aplicar reglas de negocio.

**Historias que lo construyen:** HU-001 (esqueleto), HU-401, HU-402, HU-403

**Reglas que aplican:** Reenvía `Idempotency-Key`; genera o propaga `X-Correlation-Id`; CORS solo para orígenes configurados.

## Estructura

Paquete base `com.foodflow.gateway`, con `config` para la configuración del enrutamiento (HU-401). El gateway no tiene dominio ni persistencia: enruta sin reglas de negocio.

## Construir, ejecutar y probar

Requisitos: JDK 25 (Maven lo aporta el *wrapper*, versión 3.9.14). Versiones en [versiones.md](../../docs/wiki/04-implementacion/versiones.md).

Desde `gateway/api-gateway/`:

| Acción | Linux/macOS/Git Bash | Windows (PowerShell/cmd) |
|---|---|---|
| Compilar y ejecutar las pruebas | `./mvnw verify` | `mvnw.cmd verify` |
| Solo las pruebas | `./mvnw test` | `mvnw.cmd test` |
| Ejecutar | `./mvnw spring-boot:run` | `mvnw.cmd spring-boot:run` |
| Detener | `Ctrl+C` en la terminal | `Ctrl+C` en la terminal |

> Mientras sea un esqueleto no expone HTTP ni se conecta a Kafka o PostgreSQL: `spring-boot:run` arranca el contexto de Spring y termina. El servidor web, el puerto y la conexión a su base los añaden sus HU.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
