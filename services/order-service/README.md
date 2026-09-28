# services/order-service — Order Service

> **Estado:** HU-101 y HU-102 en revisión: `POST /orders` crea y persiste el pedido en estado `CREADO` y `GET /orders/{id}` lo consulta. El resto de la funcionalidad la construyen las historias indicadas.

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

## Health check (HU-604)

Con el servicio en ejecución (`./mvnw spring-boot:run`, puerto `ORDER_SERVICE_PORT`):

| Comprobación | Comando |
|---|---|
| Estado agregado | `curl -i http://localhost:8081/actuator/health` |
| La aplicación arrancó | `curl -i http://localhost:8081/actuator/health/liveness` |
| Puede atender tráfico | `curl -i http://localhost:8081/actuator/health/readiness` |

`200` con `"status":"UP"` cuando está disponible; `503` con `"status":"DOWN"` cuando una dependencia esencial no responde; `readiness` incluye Order DB (`db`). Es el único grupo de endpoints de Actuator expuesto y no publica detalles ni credenciales. Contrato completo: [api-rest.md](../../docs/wiki/03-contratos/api-rest.md).

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
