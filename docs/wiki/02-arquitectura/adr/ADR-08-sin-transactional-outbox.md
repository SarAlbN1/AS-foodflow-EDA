# ADR-08: Sin Transactional Outbox; se acepta el riesgo de escritura dual

**Estado:** Aprobado
**Fecha:** 2026-09-27 (fecha de registro del ADR; la decisión se tomó en la fase de diseño)
**Decisores:** Sara, Juan

## Contexto

Order Service hace dos escrituras en dos sistemas distintos al crear un pedido: persiste el pedido en Order DB y publica `OrderCreated` en Kafka. **No hay transacción que abarque los dos**, y no la puede haber sin un coordinador distribuido.

Existe, por tanto, una ventana de fallo: si el proceso muere —o Kafka está inalcanzable y agota los reintentos del productor— **después** del commit en PostgreSQL y **antes** de que el evento llegue al broker, el pedido queda persistido en estado `CREADO` y nadie procesará su pago. El pedido existe pero su flujo nunca arranca.

El patrón estándar para cerrar esa ventana es **Transactional Outbox**: el evento se escribe en una tabla de la misma base, en la misma transacción que el pedido, y un proceso aparte lee esa tabla y publica en Kafka. Eso convierte dos escrituras en dos sistemas en una escritura local más una entrega garantizada.

El coste de Outbox en este prototipo: una tabla más por servicio, un publicador con su propio ciclo de vida, la deduplicación en el consumidor y la operación de vigilar que el publicador no se atrase. Es infraestructura significativa para dos personas en un semestre.

## Decisión

**No se implementa Transactional Outbox.** Se acepta explícitamente el riesgo de la escritura dual.

En su lugar:

- Order Service publica `OrderCreated` **después** del commit de la transacción que persiste el pedido. Nunca antes: publicar primero podría anunciar un pedido que luego no existe, lo cual es peor.
- El productor usa `acks=all` e idempotencia habilitada, para maximizar la probabilidad de que el evento llegue.
- Si la publicación falla tras los reintentos del productor, se registra un `ERROR` con `correlationId` y `orderId`, y el pedido **permanece en `CREADO`**.
- **No** hay tarea de reconciliación ni barrido de pedidos huérfanos.
- La recuperabilidad del prototipo se clasifica como **Parcial**, no como Alta, y así figura en la matriz de atributos de calidad y en el informe técnico.

## Opciones consideradas

### Opción A: Aceptar el riesgo, publicar después del commit (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja. Es el camino directo |
| Costo | Ninguno |
| Escalabilidad | Alta: no hay publicador intermedio que se convierta en cuello de botella |
| Familiaridad del equipo | Alta |

**Pros:** no añade tablas, procesos ni operación; el flujo es fácil de explicar y de demostrar; deja tiempo para las propiedades que sí se quieren mostrar (idempotencia, DLQ, *replay*, aislamiento de datos).
**Contras:** existe una ventana real de pérdida; un pedido puede quedar bloqueado en `CREADO` para siempre; **la recuperabilidad no puede declararse Alta**, y hay que decirlo en el informe en lugar de esconderlo.

### Opción B: Transactional Outbox

| Dimensión | Evaluación |
|---|---|
| Complejidad | Alta. Tabla *outbox*, publicador, marcado de publicados, deduplicación, orden |
| Costo | Un componente más por servicio, con su propio ciclo de vida y su propia monitorización |
| Escalabilidad | Media: el publicador es un punto de serialización |
| Familiaridad del equipo | Baja |

**Pros:** cierra la ventana de fallo; es la respuesta correcta en producción; entrega *al menos una vez* garantizada desde la transacción local.
**Contras:** es el mayor añadido de infraestructura de todo el diseño, y **está en la lista explícita de "no implementar"** del alcance acordado; su beneficio solo se aprecia provocando un fallo muy concreto, así que aporta poco valor demostrable frente a lo que cuesta; consumiría el tiempo de varias HU de flujo.

### Opción C: Publicar dentro de la transacción de base de datos

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | Ninguno |
| Escalabilidad | Alta |
| Familiaridad del equipo | Alta |

**Pros:** aparentemente resuelve el problema sin nada nuevo.
**Contras:** **no funciona, y falla en la dirección peor**. Si la transacción se revierte después de publicar, el evento ya está en Kafka: Payment Service cobraría un pedido que no existe. Cambia "pedido sin evento" por "evento sin pedido", que es un fallo más dañino y más difícil de detectar.

### Opción D: Tarea de reconciliación periódica

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media |
| Costo | Un proceso programado |
| Escalabilidad | Media |
| Familiaridad del equipo | Media |

**Pros:** más barata que Outbox; detectaría pedidos en `CREADO` más antiguos que un umbral y republicaría su evento.
**Contras:** es una solución parcial que **parece** completa, lo que es didácticamente peor que asumir el límite; hay que elegir un umbral arbitrario; republicar exige que el consumidor sea idempotente (lo es, por [ADR-09](ADR-09-idempotencia-con-eventid-y-processed-events.md)), pero añade un componente y un estado más; no está en el alcance.

## Análisis de trade-offs

La Opción C se descarta porque es incorrecta, no por costosa: invierte el sentido del fallo y lo empeora.

La decisión real es entre A, B y D, y el criterio no es técnico sino de **alcance y honestidad**. Outbox (B) es la respuesta correcta en producción y así se documenta; pero el objetivo del prototipo es demostrar EDA y sus propiedades verificables, y Outbox consumiría el tiempo de varias historias de flujo para cerrar una ventana que solo se aprecia forzando un fallo muy específico. La Opción D es peor que A: añade un componente y genera la ilusión de que el problema está resuelto.

Lo que hace esta decisión defendible **no es** elegir A, sino elegir A **y declararlo**: la recuperabilidad se marca como **Parcial** en la matriz de calidad, el límite se explica en el informe y en el README, y aparece en las lecciones aprendidas. Un riesgo aceptado y documentado es una decisión arquitectónica; el mismo riesgo sin documentar sería un defecto.

## Consecuencias

**Qué se vuelve más fácil**

- El flujo de publicación es directo y fácil de explicar en la sustentación.
- No hay publicador intermedio que vigilar, ni tabla *outbox* que pueda atrasarse.
- Queda tiempo para implementar y verificar idempotencia, reintentos, DLQ y *replay*.

**Qué se vuelve más difícil**

- **El prototipo no garantiza atomicidad entre PostgreSQL y Kafka.** Un pedido puede quedar en `CREADO` sin que su pago se procese nunca.
- El *replay* de Kafka recupera los eventos **publicados**, no los que nunca llegaron al broker. Esa es exactamente la razón por la que la recuperabilidad es **Parcial**.
- No se puede afirmar entrega *al menos una vez* desde la transacción local.

**Qué habrá que revisar**

- El límite debe estar escrito en el ADR, en el README, en la matriz de calidad y en las lecciones aprendidas. Si falta en alguno, es un defecto de documentación.
- Si el prototipo pasara a producción, Outbox sería la primera evolución, y exigiría un ADR nuevo que reemplace a este.

## Acciones

1. [x] Declarar la recuperabilidad como **Parcial** en [Atributos de calidad verificables](../atributos-de-calidad.md).
2. [x] Declarar en `CLAUDE.md` que Transactional Outbox no se implementa.
3. [ ] Publicar `OrderCreated` después del commit, con `acks=all` e idempotencia del productor (HU-103).
4. [ ] Registrar `ERROR` con `correlationId` y `orderId` si la publicación falla, dejando el pedido en `CREADO` (HU-103).
5. [ ] Documentar la limitación en el `README.md` del repositorio (HU-701).
6. [ ] Recogerla en las lecciones aprendidas del informe técnico (HU-008, HU-703).
7. [x] Verificar el *replay* con un grupo de consumidores nuevo y comprobar que no recupera lo no publicado (HU-608: el replay desde `earliest` no cambió estados ni publicó transiciones nuevas, y el pedido creado con el broker caído siguió en `CREADO` sin su `OrderCreated`; ver [Atributos de calidad medidos](../../04-implementacion/pruebas/atributos-de-calidad.md)).

## Táctica relacionada

Matriz de tácticas del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*): **Riesgo de escritura dual** — PostgreSQL + Kafka sin Outbox. Estado: *Diseñado / riesgo aceptado*.
