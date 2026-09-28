# ADR-12: Contrato REST con OpenAPI, Problem Details e `Idempotency-Key`

**Estado:** Propuesto
**Fecha:** 2026-09-27 (fecha de registro del ADR)
**Decisores:** Sara, Juan

> **Por qué sigue en `Propuesto`:** la numeración de este ADR está pendiente de confirmar por el equipo, según el [registro de decisiones](../decisiones-adr.md). El contenido de la decisión (OpenAPI, Problem Details e `Idempotency-Key`) sí está acordado y ya se refleja en la página [API REST](../../03-contratos/api-rest.md); lo que falta es ratificar que se registra como ADR-12.

## Contexto

El borde del sistema es síncrono ([ADR-07](ADR-07-api-gateway-como-entrada-sincrona.md)) y tiene tres decisiones pendientes:

1. **Dónde vive el contrato.** Si solo existe en el código, el frontend y el backend lo interpretan por separado y divergen. Dos personas trabajando en paralelo sobre Angular y Order Service necesitan una referencia común.
2. **Qué forma tienen los errores.** Cada servicio inventando su propio formato de error obliga al cliente a manejar varios, y una traza de pila en la respuesta es una fuga de información.
3. **Qué pasa si el cliente reintenta `POST /orders`.** La red falla y el usuario pulsa otra vez: sin protección, se crean dos pedidos y se cobran dos pagos. Es el mismo problema de reentrega que [ADR-09](ADR-09-idempotencia-con-eventid-y-processed-events.md) resuelve para Kafka, pero en la entrada HTTP.

## Decisión

**Contrato en OpenAPI.** El contrato de la API mínima vive en `contracts/api/openapi.yaml` y se actualiza **antes o junto con** el código, nunca después. Tres operaciones y ninguna más:

| Operación | Servicio propietario | Éxito | Error |
|---|---|---|---|
| `POST /orders` | Order Service | `201` | `400`, `409` |
| `GET /orders/{id}` | Order Service | `200` | `404` |
| `GET /orders/{id}/notifications` | Notification Service (vía gateway) | `200` con lista, posiblemente vacía | `400` |

No hay endpoint de consulta de pagos: el resultado del pago se observa por el estado del pedido (supuesto A-3).

**Errores en RFC 9457 Problem Details**, con `Content-Type: application/problem+json`, incluyendo `type`, `title`, `status`, `detail`, `code` y `correlationId`. **Nunca** se devuelven trazas de pila. Códigos: `400` entrada inválida, `404` inexistente, `409` conflicto o duplicidad, `500` error no controlado, `503` dependencia no disponible.

**`Idempotency-Key` en `POST /orders`.** Encabezado **obligatorio**: sin él la solicitud se rechaza con `400`. Lo exige el informe técnico («Requiere la cabecera `Idempotency-Key`») y lo declara `required: true` el contrato OpenAPI. Angular la genera por intento de creación. Se respalda en la tabla `idempotency_keys` de Order DB (`key` PK, `request_hash`, `order_id`, `created_at`):

- Misma clave y **mismo** cuerpo: se devuelve la respuesta original, sin crear otro pedido.
- Misma clave y cuerpo **distinto**: `409`.

## Opciones consideradas

### Opción A: OpenAPI versionado + Problem Details + `Idempotency-Key` con tabla (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media. Un archivo que mantener, un manejador de errores y una tabla con su lógica |
| Costo | Una tabla en Order DB; disciplina de actualizar el contrato en el mismo PR |
| Escalabilidad | Alta. El contrato es la referencia común de frontend y backend |
| Familiaridad del equipo | Media |

**Pros:** el contrato es explícito y revisable en un PR, así que Angular y Order Service pueden avanzar en paralelo; los errores tienen una sola forma en todo el borde y un estándar que respalda las decisiones; el reintento del cliente deja de ser peligroso; `correlationId` en el cuerpo del error conecta la respuesta con los logs de los tres servicios.
**Contras:** el contrato puede quedar desactualizado si nadie lo vigila (se mitiga con un punto del checklist del PR); `Idempotency-Key` añade una tabla y la decisión de qué entra en el `request_hash`; hay que definir los `type` de los problemas.

### Opción B: Sin contrato formal, solo código y un README

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | Ninguno |
| Escalabilidad | Baja |
| Familiaridad del equipo | Alta |

**Pros:** nada que mantener sincronizado; el código es la verdad.
**Contras:** con dos personas trabajando en paralelo en frontend y backend, la interpretación divergirá; no hay artefacto que revisar en un PR cuando el API cambia; la documentación del entregable quedaría en prosa.

### Opción C: Errores con formato propio

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | Ninguno |
| Escalabilidad | Baja |
| Familiaridad del equipo | Alta |

**Pros:** total libertad de forma; un `{ "error": "..." }` se escribe en un minuto.
**Contras:** el cliente acaba manejando variantes distintas por servicio; sin un estándar, cada decisión sobre el cuerpo del error hay que discutirla de cero; riesgo alto de filtrar trazas de pila por descuido. RFC 9457 ya resolvió el problema.

### Opción D: Sin `Idempotency-Key`; deduplicar por contenido de la solicitud

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media |
| Costo | Ninguno adicional |
| Escalabilidad | Media |
| Familiaridad del equipo | Media |

**Pros:** el cliente no tiene que generar ni enviar nada.
**Contras:** **indistinguible del caso legítimo**: dos pedidos idénticos del mismo cliente en pocos segundos pueden ser un reintento o dos pedidos reales, y el servidor no puede saberlo. Habría que inventar una ventana de tiempo arbitraria. La clave explícita traslada la intención al cliente, que es quien la conoce.

## Análisis de trade-offs

Las tres piezas se agrupan en un solo ADR porque las tres definen **el contrato del borde** y se implementan juntas en HU-404.

Sobre el contrato: la Opción B es más barata hoy y más caro mañana. Con dos personas en paralelo, el coste de un malentendido sobre el API supera al de mantener un archivo.

Sobre los errores: la Opción C no ahorra trabajo real —hay que decidir un formato de todas formas— y pierde el respaldo de un estándar. RFC 9457 da además un sitio natural para el `correlationId`, que es lo que conecta una respuesta de error con los logs de los tres servicios.

Sobre la idempotencia: la Opción D es inviable porque confunde reintento con petición legítima. La clave explícita es la solución estándar, y además es simétrica a la idempotencia de eventos de [ADR-09](ADR-09-idempotencia-con-eventid-y-processed-events.md): fuera se usa `Idempotency-Key`, dentro se usa `eventId`, y ambas se respaldan con una restricción única en la base del servicio propietario. Esa simetría hace el diseño más fácil de explicar.

La decisión de devolver `409` cuando la clave se repite con un cuerpo distinto es deliberada: es un error del cliente, no un reintento, y silenciarlo devolviendo la respuesta original ocultaría un defecto.

## Consecuencias

**Qué se vuelve más fácil**

- Frontend y backend avanzan en paralelo contra una referencia común.
- El cliente maneja una sola forma de error en todo el borde.
- Un reintento del usuario deja de crear pedidos y cobros duplicados.
- Un error de producción se rastrea con el `correlationId` que viene en la respuesta.

**Qué se vuelve más difícil**

- Hay que mantener `openapi.yaml` sincronizado: es un punto del checklist de cada PR.
- `POST /orders` gana una tabla y una rama de lógica.
- Hay que definir y documentar los `type` y los `code` de los problemas.

**Qué habrá que revisar**

- **La numeración de este ADR está pendiente de confirmar**; hasta entonces el estado es `Propuesto`.
- Qué campos entran en `request_hash` (afecta a cuándo se devuelve `409`).
- La política de retención de `idempotency_keys` está sin definir (no hace falta en el prototipo).
- Cualquier endpoint nuevo exige una decisión explícita: la API mínima es cerrada.

## Acciones

1. [x] Crear la tabla `idempotency_keys` en Order DB (HU-002).
2. [ ] Confirmar la numeración de este ADR y pasarlo a `Aprobado` (equipo).
3. [ ] Implementar `Idempotency-Key` en `POST /orders` con su tabla y el `409` por cuerpo distinto (HU-105).
4. [ ] Implementar el manejador de errores en Problem Details, sin trazas de pila (HU-403).
5. [ ] Escribir `contracts/api/openapi.yaml` con las tres operaciones, los errores y los encabezados (HU-404).
6. [ ] Añadir al checklist del PR la comprobación de que OpenAPI se actualizó si cambió el API (hecho en la plantilla del bootstrap).

## Táctica relacionada

Matriz de tácticas del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*): **Contrato HTTP e idempotencia de entrada** — RFC 9457 + OpenAPI + `Idempotency-Key`. Estado: *Implementar*.
