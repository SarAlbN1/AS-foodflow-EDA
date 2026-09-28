# ADR-04: Clave de partición por `orderId`

**Estado:** Aprobado
**Fecha:** 2026-09-27 (fecha de registro del ADR; la decisión se tomó en la fase de diseño)
**Decisores:** Sara, Juan

## Contexto

Kafka garantiza el orden **dentro de una partición**, no dentro de un tópico. Los tópicos del prototipo tienen 3 particiones para poder demostrar escalabilidad con varias réplicas de un consumidor en el mismo grupo. Con más de una partición, si la clave del mensaje no se elige bien, dos eventos del mismo pedido pueden acabar en particiones distintas y procesarse en cualquier orden.

Eso importa: si `PaymentApproved` se procesara antes que el `OrderCreated` del mismo pedido, Payment Service intentaría cobrar un pedido que aún no existe en su vista del mundo, y Order Service podría intentar aplicar una transición sobre un pedido inexistente.

Al mismo tiempo, todo el flujo gira en torno a un único concepto: el pedido. Pago y notificación existen **por causa de** un pedido.

## Decisión

**`aggregateId` es siempre el `orderId`**, en los seis eventos, y se usa como **clave de partición** del mensaje Kafka.

Esto garantiza que todos los eventos de un mismo pedido —`OrderCreated`, `PaymentApproved` o `PaymentRejected`, `OrderStatusChanged`, `NotificationSent` o `NotificationFailed`— caen en la misma partición y se procesan en orden por cada grupo de consumidores, aunque el tópico tenga varias particiones y el grupo varias instancias.

El campo forma parte del envelope común y el validador de contratos comprueba que `aggregateId == payload.orderId`.

## Opciones consideradas

### Opción A: `orderId` como clave de partición (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja. Es una línea en el productor y un campo en el envelope |
| Costo | Ninguno |
| Escalabilidad | Alta. Permite hasta tantos consumidores en paralelo como particiones, conservando el orden por pedido |
| Familiaridad del equipo | Alta |

**Pros:** orden garantizado donde importa (por pedido) sin renunciar al paralelismo; la clave es natural y estable, porque el pedido es la raíz del agregado de todo el flujo; hace verificable la regla, porque el campo está en el contrato.
**Contras:** el reparto entre particiones depende de la distribución de los `orderId`; si un pedido concreto generara un volumen desproporcionado, su partición sería un punto caliente (irrelevante en el prototipo, donde los UUID reparten bien).

### Opción B: Sin clave (reparto *round-robin*)

| Dimensión | Evaluación |
|---|---|
| Complejidad | La más baja: no se decide nada |
| Costo | Ninguno |
| Escalabilidad | Alta en reparto |
| Familiaridad del equipo | Alta |

**Pros:** reparto perfectamente uniforme entre particiones.
**Contras:** **sin garantía de orden por pedido**. Obligaría a que cada consumidor tolerase eventos fuera de orden (buffers, reintentos por dependencia no satisfecha), es decir, mucha más complejidad en el consumidor para ahorrar una línea en el productor.

### Opción C: Un tópico con una sola partición

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | Ninguno |
| Escalabilidad | **Nula**: un solo consumidor activo por grupo |
| Familiaridad del equipo | Alta |

**Pros:** orden total garantizado, el modelo mental más simple posible.
**Contras:** imposibilita demostrar el reparto de particiones entre réplicas, que es un criterio verificable de escalabilidad del prototipo. Cambia una propiedad que se quiere enseñar por una simplificación que no hace falta.

## Análisis de trade-offs

La Opción B ahorra una decisión en el productor y la paga con complejidad en los tres consumidores, lo cual es un mal cambio. La Opción C da orden total pero elimina el paralelismo, y con él uno de los criterios de calidad que el prototipo debe poder demostrar.

La Opción A es el compromiso estándar de Kafka: **ordenar por la clave del agregado**. El orden solo se necesita *por pedido*, no entre pedidos distintos, así que particionar por `orderId` da exactamente la garantía necesaria y ni una más. Que el `orderId` sea además la raíz natural del flujo hace que la elección no sea arbitraria: `Payment` y `Notification` no tienen identidad propia fuera de un pedido.

## Consecuencias

**Qué se vuelve más fácil**

- Razonar sobre el orden: los eventos de un pedido se procesan en el orden en que ocurrieron.
- Escalar un consumidor a varias instancias sin romper la secuencia de un pedido.
- Verificar el contrato: `aggregateId` está en el envelope y su igualdad con `payload.orderId` se comprueba automáticamente.

**Qué se vuelve más difícil**

- El reparto de carga depende de la distribución de las claves; con pocos pedidos, las particiones quedan desiguales (aceptable).
- Cualquier evento futuro que no pertenezca a un pedido no encaja en este envelope y necesitaría una decisión nueva.

**Qué habrá que revisar**

- Si apareciera un agregado sin pedido asociado, habría que replantear el significado de `aggregateId` en un ADR nuevo.
- El número de particiones (3) fija el paralelismo máximo por grupo; cambiarlo es una decisión de infraestructura documentada en los tópicos.

## Acciones

1. [x] Incluir `aggregateId` en el envelope común de los seis esquemas, documentado como clave de partición (HU-003).
2. [x] Comprobar de forma automática que `aggregateId == payload.orderId` (HU-003, `scripts/validate-events.sh`).
3. [ ] Usar `orderId` como clave del mensaje en cada productor Kafka (HU-103, HU-203, HU-303).
4. [ ] Declarar los tópicos con 3 particiones (HU-004).
5. [ ] Demostrar el reparto de particiones con 2 réplicas de Payment Service en el mismo grupo (HU-608).

## Táctica relacionada

Matriz de tácticas del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*): **Orden del flujo** — Kafka + `orderId`. Estado: *Implementar*.
