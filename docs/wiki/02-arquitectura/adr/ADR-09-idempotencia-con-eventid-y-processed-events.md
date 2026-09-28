# ADR-09: Idempotencia con `eventId` y tabla `processed_events`

**Estado:** Aprobado
**Fecha:** 2026-09-27 (fecha de registro del ADR; la decisión se tomó en la fase de diseño)
**Decisores:** Sara, Juan

## Contexto

Kafka entrega **al menos una vez**. Un mismo evento puede llegar dos veces por razones perfectamente normales: el consumidor procesó el mensaje pero murió antes de confirmar el offset; hubo un rebalanceo del grupo; se reintentó tras un fallo transitorio ([ADR-05](ADR-05-reintentos-y-dlq.md)); o se relee el tópico a propósito para probar el *replay*.

Sin protección, cada reentrega duplica el efecto de negocio: dos pagos para el mismo pedido, dos transiciones de estado, dos correos al cliente. El segundo cobro es un error grave, y el segundo correo es visible para el usuario.

Además, esto interactúa con [ADR-08](ADR-08-sin-transactional-outbox.md): al no haber Outbox, la reentrega es el mecanismo que sostiene la recuperabilidad parcial. Si el *replay* duplicara efectos, no serviría de nada.

## Decisión

Cada consumidor es **idempotente** mediante el `eventId` del envelope:

- Cada servicio tiene una tabla **`processed_events`** en su propia base, con `event_id` como clave primaria (restricción única), más `consumer` y `processed_at`.
- Antes de aplicar el efecto de negocio, el consumidor comprueba si el `eventId` ya está registrado. Si lo está, **descarta el evento y confirma el offset**, sin repetir nada.
- El registro en `processed_events` y el efecto de negocio se escriben en la **misma transacción local**. No en dos transacciones: en una.
- El **offset se confirma manualmente y solo después** del commit de esa transacción.

Como segunda línea de defensa, la propia base refuerza la unicidad en el dominio: `payments.order_id` es **único**, así que un pedido no puede tener dos pagos ni aunque falle la lógica de idempotencia.

En la entrada HTTP existe un mecanismo análogo pero independiente: el encabezado `Idempotency-Key` de `POST /orders`, con su tabla `idempotency_keys` (ver [ADR-12](ADR-12-contrato-rest-openapi-problem-details-idempotency-key.md)).

## Opciones consideradas

### Opción A: `eventId` + `processed_events` en la misma transacción local (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media. Una tabla y un patrón de consumo que hay que aplicar igual en los tres servicios |
| Costo | Una tabla por base; el `processed_events` crece de forma monótona |
| Escalabilidad | Alta. La comprobación es una búsqueda por clave primaria |
| Familiaridad del equipo | Media |

**Pros:** funciona para **cualquier** evento, sin depender de que la operación de negocio sea naturalmente idempotente; la atomicidad entre "marcar procesado" y "aplicar efecto" es real, porque es una sola transacción local en una sola base; sirve de evidencia auditable de qué se procesó y cuándo; hace que el *replay* sea seguro, lo que sostiene la recuperabilidad parcial de ADR-08.
**Contras:** una tabla más por servicio que crece sin límite (haría falta una política de purga en producción); hay que recordar aplicar el patrón en cada consumidor nuevo; el orden importa —si se registra en una transacción y se aplica el efecto en otra, la garantía desaparece.

### Opción B: Confiar en operaciones naturalmente idempotentes

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | Ninguno |
| Escalabilidad | Alta |
| Familiaridad del equipo | Alta |

**Pros:** nada que añadir; una transición de estado condicionada (`CREADO` → `PAGADO` solo si está en `CREADO`) ya es idempotente por sí misma.
**Contras:** **no cubre los efectos con lado externo**. Enviar un correo no es idempotente: reprocesar `PaymentApproved` enviaría un segundo mensaje al cliente. Tampoco cubre la creación de un pago. Y obliga a razonar caso por caso, así que un consumidor nuevo puede romper la propiedad sin que nadie lo note.

### Opción C: Exactly-once semantics de Kafka (transacciones de Kafka)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Alta. Productor transaccional, `isolation.level`, coordinación de transacciones |
| Costo | Sobrecarga en el broker y en el cliente |
| Escalabilidad | Media |
| Familiaridad del equipo | Baja |

**Pros:** garantía más fuerte en el plano de Kafka; menos código de aplicación para el caso consumir-transformar-producir.
**Contras:** **el "exactly-once" de Kafka no cubre el efecto secundario externo**: no hay transacción que abarque Kafka, PostgreSQL y una llamada HTTP al proveedor. Seguiría haciendo falta idempotencia en el consumidor para el correo, así que añade complejidad sin eliminar la necesidad. Además, la atomicidad Kafka–PostgreSQL es justo lo que ADR-08 decide no resolver.

### Opción D: Deduplicación en memoria (caché de `eventId` recientes)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | Memoria |
| Escalabilidad | Baja: no se comparte entre instancias |
| Familiaridad del equipo | Alta |

**Pros:** rapidísimo; sin tabla ni escrituras extra.
**Contras:** se pierde al reiniciar, que es precisamente cuando ocurren las reentregas; no se comparte entre réplicas del mismo grupo, así que con dos instancias de Payment Service la garantía desaparece. Inútil para el caso que hay que resolver.

## Análisis de trade-offs

La Opción B es suficiente para las transiciones de estado y **insuficiente** para todo lo demás; adoptarla sería tener idempotencia en unos consumidores y no en otros, sin una regla clara. La Opción D falla exactamente en el escenario que motiva la decisión (reinicio y réplicas). La Opción C da una garantía más fuerte en el plano del broker pero no elimina la necesidad de idempotencia en el consumidor, porque el correo al cliente queda fuera de cualquier transacción.

La Opción A es la única que da una regla **uniforme y verificable** para los tres servicios. Y el detalle crítico no es la tabla, sino la **atomicidad**: `processed_events` y el efecto de negocio en la **misma transacción local**, y el offset confirmado solo después. Si se separaran en dos transacciones, un fallo entre ambas dejaría el evento marcado como procesado sin haberse aplicado —perdiéndolo en silencio— o aplicado sin marcar —duplicándolo—. La garantía vive en esa única transacción.

La restricción única en `payments.order_id` es una defensa en profundidad: si un día la lógica falla, la base lo impide.

## Consecuencias

**Qué se vuelve más fácil**

- Reprocesar un tópico con un grupo nuevo sin duplicar pagos, transiciones ni correos: es lo que hace útil el *replay* y sostiene la recuperabilidad **Parcial** de ADR-08.
- Recuperarse de una caída del consumidor sin efectos duplicados.
- Verificar la propiedad de forma automática: entregar el mismo evento dos veces debe producir 1 pago, 1 transición y 1 notificación.
- Auditar qué evento procesó cada servicio y cuándo.

**Qué se vuelve más difícil**

- Cada consumidor nuevo debe aplicar el patrón; olvidarlo es un defecto silencioso.
- `processed_events` crece sin límite; en producción haría falta una política de purga.
- El código del consumidor es algo más verboso: comprobar, aplicar y registrar en una transacción, y confirmar el offset al final.

**Qué habrá que revisar**

- `event_id` es la clave primaria **simple**, tal como especifica la página de persistencia. Si un día dos consumidores distintos **del mismo servicio** tuvieran que procesar el mismo evento, la clave tendría que pasar a `(event_id, consumer)`. Hoy cada servicio tiene un único consumidor, así que la clave simple es correcta; el campo `consumer` queda registrado para poder hacer esa evolución sin perder información.
- La política de purga de `processed_events` está sin definir (no hace falta en el prototipo).

## Acciones

1. [x] Crear `processed_events` (`event_id` PK, `consumer`, `processed_at`) en las tres bases (HU-002).
2. [x] Declarar `payments.order_id` como único (HU-002).
3. [x] Incluir `eventId` como campo obligatorio del envelope en los seis esquemas (HU-003).
4. [ ] Implementar el patrón de consumo idempotente en los tres consumidores, con la escritura en la misma transacción local (HU-201, HU-104, HU-301).
5. [ ] Confirmar el offset manualmente solo después del commit local.
6. [ ] Probar de forma automática que el mismo evento entregado dos veces produce un solo efecto (HU-602).

## Táctica relacionada

Matriz de tácticas del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*): **Idempotencia de consumidores** — `eventId` + PostgreSQL. Estado: *Implementar*.
