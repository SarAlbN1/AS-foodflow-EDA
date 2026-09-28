# gateway/api-gateway — API Gateway

> **Estado:** HU-401 en revisión: enruta `POST /orders` y `GET /orders/{id}` a Order Service. La consulta de notificaciones (HU-402) y el `correlationId` y CORS (HU-403) los añaden sus historias.

**Responsabilidad:** Punto de entrada REST. Enruta hacia el servicio propietario sin aplicar reglas de negocio.

**Historias que lo construyen:** HU-001 (esqueleto), HU-401, HU-402, HU-403

**Reglas que aplican:** Reenvía `Idempotency-Key`; genera o propaga `X-Correlation-Id`; CORS solo para orígenes configurados.

## Estructura

Paquete base `com.foodflow.gateway`. El gateway no tiene dominio ni persistencia: enruta sin reglas de negocio ([ADR-07](../../docs/wiki/02-arquitectura/adr/ADR-07-api-gateway-como-entrada-sincrona.md)).

- `config`: rutas de Spring Cloud Gateway Server MVC (`OrderRoutesConfig`) y la respuesta `503` cuando el destino no responde (`DependencyUnavailable`).

## Rutas

| Operación | Destino | Qué hace el gateway |
|---|---|---|
| `POST /orders` | Order Service | Reenvía método, ruta, cuerpo y cabeceras; `Idempotency-Key` va **sin modificar** |
| `GET /orders/{id}` | Order Service | Igual |
| Cualquier otra ruta | — | `404` del gateway; no llega a ningún servicio |

**Códigos HTTP** (criterio 4 de HU-401):

- Se **preservan** tal cual el código, las cabeceras (incluida `Location`) y el cuerpo de Order Service, incluidos sus `400`, `404` y `409` en Problem Details.
- **Única transformación:** si Order Service no responde (conexión rechazada o sin respuesta), el gateway contesta `503` en Problem Details con `code: DEPENDENCY_UNAVAILABLE`. No incluye la dirección interna del servicio ni trazas.

## Configuración

| Variable | Por defecto | Uso |
|---|---|---|
| `GATEWAY_PORT` | `8080` | Puerto del borde, el del servidor de `contracts/api/openapi.yaml` |
| `ORDER_SERVICE_URL` | `http://localhost:8081` | Ubicación interna de Order Service. En Compose será `http://order-service:8081` |

## Construir, ejecutar y probar

Requisitos: JDK 25 (Maven lo aporta el *wrapper*, versión 3.9.14). Versiones en [versiones.md](../../docs/wiki/04-implementacion/versiones.md).

Desde `gateway/api-gateway/`:

| Acción | Linux/macOS/Git Bash | Windows (PowerShell/cmd) |
|---|---|---|
| Compilar y ejecutar las pruebas | `./mvnw verify` | `mvnw.cmd verify` |
| Solo las pruebas | `./mvnw test` | `mvnw.cmd test` |
| Ejecutar | `./mvnw spring-boot:run` | `mvnw.cmd spring-boot:run` |
| Detener | `Ctrl+C` en la terminal | `Ctrl+C` en la terminal |

Con Order Service corriendo en `8081` (ver su README), el pedido se crea a través del gateway:

```bash
curl -i -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: 7c9e6679-7425-40de-944b-e07fc1f90ae7' \
  -d '{"customerReference":"PED-0001","customerContact":"ana@foodflow.test",
       "notificationChannel":"EMAIL","total":45000.00,"paymentToken":"PAY-OK"}'
```

Las pruebas (`OrderRoutesTests`, `DependencyUnavailableTests`) levantan el gateway en un puerto aleatorio frente a un Order Service simulado con el servidor HTTP del JDK: no necesitan Docker ni el servicio real.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
