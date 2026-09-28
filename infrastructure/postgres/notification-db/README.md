# infrastructure/postgres/notification-db

**Responsabilidad:** Scripts SQL de esquema de Notification DB (Flyway es opcional).

**Historias que lo construyen:** HU-002

**Reglas que aplican:** Solo `notification-service` accede a esta base.

> **Estado:** HU-002 completada. El esquema inicial está en `01-schema.sql`.

## Contenido

| Archivo | Qué crea |
|---|---|
| `01-schema.sql` | `notifications` y `processed_events` |

Contrato de referencia: [Persistencia](../../../docs/wiki/03-contratos/persistencia.md).

## Cómo se ejecuta

El directorio se monta en `/docker-entrypoint-initdb.d` (solo lectura) del contenedor
`notification-db`. PostgreSQL ejecuta los scripts en orden alfabético **una sola vez**, cuando se crea
el volumen `foodflow-notification-db-data`. No hay ningún paso manual.

Para reaplicar el esquema tras editarlo hay que recrear el volumen:

```bash
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml down -v
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml up -d
```

## Credenciales y acceso

Base, usuario y contraseña se definen en `.env` con las variables `NOTIFICATION_DB_NAME`,
`NOTIFICATION_DB_USER` y `NOTIFICATION_DB_PASSWORD`. Son **exclusivas de esta base**: ningún otro
servicio las recibe. Desde el host, la base escucha en `localhost:5435`
(`NOTIFICATION_DB_HOST_PORT`); dentro de la red de Compose, en `notification-db:5432`.

`processed_events` implementa la idempotencia de los consumidores (ADR-09): su fila y el
efecto de negocio se escriben en la **misma transacción local**, y el offset de Kafka se
confirma solo después del commit.

Referencias: [`CLAUDE.md`](../../../CLAUDE.md) · [Wiki](../../../docs/wiki/Home.md)
