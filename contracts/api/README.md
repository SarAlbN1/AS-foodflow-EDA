# contracts/api — Contrato REST

**Responsabilidad:** `openapi.yaml` describe las tres operaciones de la API mínima, los errores en formato Problem Details y los encabezados `Idempotency-Key` y `X-Correlation-Id`.

**Historias que lo construyen:** HU-404

**Reglas que aplican:** Se actualiza en el mismo PR que cambie el API.

> **Estado:** HU-404 en revisión. El contrato está escrito y se valida con un comando; las tres operaciones las implementan HU-101, HU-102, HU-305 y el enrutamiento HU-401 y HU-402.

## Las tres operaciones

| Operación | Servicio propietario | Historia que la implementa |
|---|---|---|
| `POST /orders` | Order Service | HU-101 (creación) y HU-107 (`Idempotency-Key`) |
| `GET /orders/{id}` | Order Service | HU-102 |
| `GET /orders/{id}/notifications` | Notification Service | HU-305, enrutada por HU-402 |

No existe operación de pago: el pago se dispara cuando Payment Service consume `OrderCreated` y su resultado se ve en el estado del pedido (regla arquitectónica 9).

## Errores

Todos los errores usan `application/problem+json` (RFC 9457) con dos extensiones del proyecto: `code`, estable para que el cliente lo interprete sin leer el texto, y `correlationId`, para cruzar la respuesta con los registros. Catálogo de `code`:

| `code` | Estado | Cuándo |
|---|---|---|
| `VALIDATION_ERROR` | `400` | Entrada inválida, cabecera obligatoria ausente o cuerpo ilegible |
| `NOT_FOUND` | `404` | El recurso no existe |
| `IDEMPOTENCY_CONFLICT` | `409` | La `Idempotency-Key` se reutilizó con un cuerpo distinto |
| `INTERNAL_ERROR` | `500` | Fallo no controlado. Nunca incluye trazas de pila |
| `DEPENDENCY_UNAVAILABLE` | `503` | Una dependencia no responde |

El `detail` de un `400` enumera **todos** los campos rechazados, separados por `; ` y en formato `campo: motivo`, para que el frontend pueda marcar varios campos con una sola respuesta.

## Validación

```bash
bash scripts/validate-openapi.sh
```

Prepara el entorno virtual aislado `.venv-contracts/` (el mismo que usa la validación de eventos, ignorado por Git), instala `openapi-spec-validator` y comprueba tres cosas:

1. El documento es un OpenAPI **3.1.0** válido.
2. Están las tres operaciones de la API mínima, la cabecera obligatoria `Idempotency-Key`, la cabecera `X-Correlation-Id`, los códigos `400`, `404` y `409`, y `ProblemDetail` exige `code` y `correlationId`.
3. **Cada ejemplo valida contra el esquema de su propia solicitud o respuesta**, así que la documentación no puede quedar mintiendo sobre el formato real.

Devuelve `0` si todo pasa y `1` si algo falla.

## Por qué OpenAPI 3.1

3.1 usa JSON Schema 2020-12, el mismo dialecto que los contratos de eventos de `contracts/events/v1/`. Así los dos contratos se leen y se validan con las mismas reglas, y un tipo como el importe o el UUID se expresa igual en los dos.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [API REST](../../docs/wiki/03-contratos/api-rest.md) · [Eventos](../events/v1/README.md)
