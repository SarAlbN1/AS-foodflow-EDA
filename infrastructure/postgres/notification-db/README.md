# infrastructure/postgres/notification-db

**Responsabilidad:** Referencia del esquema de Notification DB. **Desde HU-010 el esquema lo crea `notification-service` con Flyway** (`services/notification-service/src/main/resources/db/migration/V1__esquema_inicial.sql`); este script ya no se monta en Compose.

**Historias que lo construyen:** HU-002

**Reglas que aplican:** Solo `notification-service` accede a esta base.

> **Estado:** HU-002 completada. El esquema inicial está en `01-schema.sql`.

## Contenido

| Archivo | Qué crea |
|---|---|
| `01-schema.sql` | `notifications` y `processed_events` |

Contrato de referencia: [Persistencia](../../../docs/wiki/03-contratos/persistencia.md).

## Cómo se ejecuta

**Ya no se ejecuta.** Compose dejaba este directorio en `/docker-entrypoint-initdb.d`; desde HU-010 el
esquema lo aplica `notification-service` con Flyway al arrancar, y la **fuente del esquema es la migración**
del servicio, con el mismo modelo que `01-schema.sql`. Este archivo se conserva como referencia
de HU-002: si el esquema cambia, el cambio va en una migración nueva del servicio, no aquí.

## Credenciales y acceso

Base, usuario y contraseña se definen en `.env` con las variables `NOTIFICATION_DB_NAME`,
`NOTIFICATION_DB_USER` y `NOTIFICATION_DB_PASSWORD`. Son **exclusivas de esta base**: ningún otro
servicio las recibe. Desde el host, la base escucha en `localhost:5435`
(`NOTIFICATION_DB_HOST_PORT`); dentro de la red de Compose, en `notification-db:5432`.

`processed_events` implementa la idempotencia de los consumidores (ADR-09): su fila y el
efecto de negocio se escriben en la **misma transacción local**, y el offset de Kafka se
confirma solo después del commit.

Referencias: [`CLAUDE.md`](../../../CLAUDE.md) · [Wiki](../../../docs/wiki/Home.md)
