# mocks/notification-provider — Proveedor simulado

> **Estado:** HU-306 completada. Solo para desarrollo, demo y pruebas.

**Responsabilidad:** Simula el proveedor externo de notificaciones con modos de fallo por destino (`fail.test`, `flaky.test`, `slow.test`).

**Historias que lo construyen:** HU-306

**Reglas que aplican:** Solo lo invoca Notification Service (regla 7). Contrato: [Proveedor de notificaciones](../../docs/wiki/03-contratos/proveedor-notificaciones.md).

## Contrato

`POST /v1/messages` con `{"channel", "destination", "content", "correlationId"}`. `channel`, `destination` y `content` son obligatorios; si falta alguno, responde `400` en Problem Details. Si lo acepta, responde `202` con `{"providerReference": "MOCK-<uuid>"}`.

| Destino | Comportamiento |
|---|---|
| Email normal | `202` |
| `*@fail.test` | `503` siempre (Problem Details) |
| `*@flaky.test` | `503` en los 2 primeros intentos **por destino** y luego `202` |
| `*@slow.test` | `202` después de `MOCK_SLOW_DELAY` (5 s por defecto, más que el timeout de lectura de 3 s del cliente) |

El contador de `flaky.test` vive en memoria: se reinicia al reiniciar el contenedor. Para repetir la demo sin reiniciarlo, usa otro destino (`demo2@flaky.test`). En los logs, el destino aparece enmascarado (`a***@dominio.com`).

## Configuración

| Variable | Por defecto | Uso |
|---|---|---|
| `NOTIFICATION_PROVIDER_URL` | `http://notification-provider:8080` | URL que recibe **notification-service** (HU-302) dentro de la red de Compose |
| `NOTIFICATION_PROVIDER_HOST_PORT` | `8090` | Puerto en el host, para probarlo con `curl` |
| `MOCK_SLOW_DELAY` | `5s` | Espera de `*@slow.test` |
| `MOCK_FLAKY_FAILURES` | `2` | Intentos que fallan en `*@flaky.test` |

## Construir, ejecutar y probar

Requisitos: JDK 25 (`JAVA_HOME` apuntando a él). Maven lo aporta el *wrapper* (3.9.14).

| Acción | Comando (desde `mocks/notification-provider/`; en Windows, `mvnw.cmd`) |
|---|---|
| Compilar y probar (las pruebas cubren cada modo) | `./mvnw verify` |
| Ejecutar en local (puerto 8080) | `./mvnw spring-boot:run` |
| Detener | `Ctrl+C` |

Con Compose, desde la raíz del repositorio:

```bash
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml up -d --build notification-provider
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml logs -f notification-provider
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml stop notification-provider
```

## Guion de demostración de cada modo

Con el mock en Compose (`localhost:8090`; si usas `spring-boot:run`, cambia a `8080`):

```bash
URL=http://localhost:8090/v1/messages
send() { curl -s -o /dev/null -w "%{http_code} en %{time_total}s\n" -X POST "$URL" \
  -H 'Content-Type: application/json' \
  -d "{\"channel\":\"EMAIL\",\"destination\":\"$1\",\"content\":\"Pago aprobado\",\"correlationId\":\"demo\"}"; }

send ana@example.com      # 202
send ana@fail.test        # 503
send ana@flaky.test       # 503
send ana@flaky.test       # 503
send ana@flaky.test       # 202
send ana@slow.test        # 202 tras ~5 s
curl -s -m 3 -X POST "$URL" -H 'Content-Type: application/json' \
  -d '{"channel":"EMAIL","destination":"ana@slow.test","content":"x"}' || echo "timeout de 3 s, como el cliente"
```

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
