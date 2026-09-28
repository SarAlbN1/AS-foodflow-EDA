# contracts/events/v1 — Contratos de eventos

**Responsabilidad:** Esquemas JSON (`*.schema.json`) de los seis eventos. Son documentación de interoperabilidad, no código compartido.

**Historias que lo construyen:** HU-003

**Reglas que aplican:** Envelope común; `aggregateId = orderId`; nombres según la wiki (`OrderStatusChanged`, no `OrderUpdated`).

> **Estado:** HU-003 completada. Los seis esquemas v1 están definidos, con ejemplos válidos e inválidos y validación automática.

## Los seis eventos

| Esquema | Evento | Productor | Tópico |
|---|---|---|---|
| `order-created.schema.json` | `OrderCreated` | Order Service | `orders.events` |
| `order-status-changed.schema.json` | `OrderStatusChanged` | Order Service | `orders.events` |
| `payment-approved.schema.json` | `PaymentApproved` | Payment Service | `payments.events` |
| `payment-rejected.schema.json` | `PaymentRejected` | Payment Service | `payments.events` |
| `notification-sent.schema.json` | `NotificationSent` | Notification Service | `notifications.events` |
| `notification-failed.schema.json` | `NotificationFailed` | Notification Service | `notifications.events` |

Catálogo de payloads y productores: [Eventos Kafka](../../../docs/wiki/03-contratos/eventos.md). El nombre `OrderUpdated` **no existe**; un esquema lo rechaza explícitamente.

## Envelope común

`envelope.schema.json` define la estructura obligatoria de **todo** evento y los tipos reutilizables. Cada esquema de evento lo referencia con `allOf` + `$ref` y solo añade la restricción de su `eventType` y de su `payload`:

| Campo | Regla |
|---|---|
| `eventId` | UUID. Clave de idempotencia (ADR-09): el consumidor lo registra en `processed_events` |
| `eventType` | Uno de los seis nombres del catálogo |
| `eventVersion` | `1` exacto. Estos esquemas son la v1 |
| `occurredAt` | ISO 8601 en **UTC** (obligatorio el sufijo `Z`) |
| `correlationId` | UUID. Entra por el gateway (`X-Correlation-Id`) y se propaga |
| `aggregateId` | UUID. **Siempre el `orderId`**; es la clave de partición (ADR-04) |
| `payload` | Objeto cerrado, específico de cada evento |

No se admiten campos adicionales ni en la raíz ni en el `payload`: un campo desconocido es un incumplimiento del contrato.

Tipos reutilizables en `$defs`: `uuid`, `importe` (`NUMERIC(12,2)`, mayor que 0), `moneda` (`COP`), `canal` (`EMAIL`), `notificationContact` (snapshot de ADR-11) y `estadoPedido` (`CREADO`, `PAGADO`, `PAGO_RECHAZADO`).

## Ejemplos

```text
examples/
├── validos/<slug>.json                 debe VALIDAR contra <slug>.schema.json
└── invalidos/<slug>__<motivo>.json     debe FALLAR  contra <slug>.schema.json
```

El nombre del archivo es el contrato de la prueba: no hay manifiesto que se pueda desincronizar. Para añadir un caso basta con dejar un archivo en la carpeta correcta.

Hay un ejemplo válido por evento (6) y 15 inválidos que cubren: envelope incompleto (`eventId`, `correlationId`), `eventVersion` distinta de 1, `occurredAt` sin UTC, `aggregateId` distinto de `payload.orderId`, `eventType: OrderUpdated`, estado de pedido inexistente, `paymentToken` fuera de `PAY-OK`/`PAY-FAIL`, total negativo, moneda distinta de `COP`, `notificationContact` ausente o sin destino, campo extra en el `payload`, `attempts` igual a 0 y UUID mal formado.

## Validación automática

```bash
bash scripts/validate-events.sh
```

El script prepara un entorno virtual aislado en `.venv-contracts/` (ignorado por Git), instala `jsonschema` y comprueba que:

1. Los seis esquemas existen y son JSON Schema 2020-12 válidos.
2. Cada ejemplo de `validos/` valida contra su esquema.
3. Cada ejemplo de `invalidos/` **es rechazado**, e imprime el motivo del rechazo.
4. `aggregateId == payload.orderId` — regla de ADR-04 que JSON Schema no puede expresar por sí sola, así que la comprueba el validador.

Devuelve código de salida `0` si todo pasa y `1` si algo falla, de modo que sirve como puerta de verificación local (y, si algún día se adopta CI, como paso de un flujo de trabajo).

## Por qué no hay código Java compartido

Estos archivos son **contratos**, no una biblioteca. Cada servicio serializa y deserializa sus eventos con sus propias clases internas y valida contra el esquema si lo necesita. Ningún servicio importa un módulo de dominio de otro (regla arquitectónica 8). El validador es una herramienta de verificación en Python, fuera del camino de compilación de los servicios.

## Versionado

Un cambio **compatible** (añadir un campo opcional) se hace sobre la v1. Un cambio **incompatible** (quitar o renombrar un campo, cambiar un tipo o un enum) crea `contracts/events/v2/` y un ADR que lo justifique; la v1 se conserva mientras exista algún consumidor.

Referencias: [`CLAUDE.md`](../../../CLAUDE.md) · [Wiki](../../../docs/wiki/Home.md) · [Eventos Kafka](../../../docs/wiki/03-contratos/eventos.md) · [Comportamiento del flujo](../../../docs/wiki/02-arquitectura/comportamiento-del-flujo.md)
