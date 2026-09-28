# ADR-01: Kafka como broker; los servicios no se invocan entre sí

**Estado:** Aprobado
**Fecha:** 2026-09-27 (fecha de registro del ADR; la decisión se tomó en la fase de diseño)
**Decisores:** Sara, Juan

## Contexto

FoodFlow debe demostrar una **Event-Driven Architecture** con un flujo funcional: crear un pedido, procesar su pago y notificar el resultado. Los tres pasos los ejecutan servicios distintos y cada uno debe poder fallar, reiniciarse o escalar sin arrastrar a los demás.

Las fuerzas en tensión:

- El objetivo académico es demostrar EDA, no construir la integración más corta posible.
- Tres servicios que se llaman por REST entre sí producirían acoplamiento temporal: si Payment está caído, la creación del pedido falla.
- El prototipo se ejecuta en una sola máquina con Compose: la infraestructura que se añada tiene que caber ahí.
- El equipo son dos personas y un semestre; no hay margen para operar un broker complejo.

## Decisión

Se adopta **Apache Kafka** como broker de eventos, con topología **broker/coreografía**: cada servicio reacciona de forma autónoma a los eventos que le interesan y **ningún servicio de negocio llama a otro por REST** para coordinar el flujo. No hay orquestador central.

Order Service publica `OrderCreated`; Payment Service lo consume y publica `PaymentApproved` o `PaymentRejected`; Order Service y Notification Service reaccionan a ese resultado de forma independiente.

## Opciones consideradas

### Opción A: Apache Kafka (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media. Un nodo en modo KRaft basta para el prototipo; los conceptos de partición, offset y grupo de consumidores hay que aprenderlos |
| Costo | Nulo en licencias; una imagen oficial en Compose |
| Escalabilidad | Alta. Particiones y grupos de consumidores permiten añadir réplicas y consumidores nuevos sin tocar el productor |
| Familiaridad del equipo | Media. Es la tecnología que el curso quiere ver y sobre la que hay integración madura en Spring |

**Pros:** log persistente que permite *replay*; desacoplamiento temporal real; orden garantizado por clave de partición; añadir un consumidor no modifica al productor; integración de primera clase con Spring for Apache Kafka.
**Contras:** consistencia eventual, que obliga a que el frontend tolere estados intermedios; más infraestructura que una cola simple; la semántica de offsets y reintentos hay que diseñarla explícitamente.

### Opción B: REST síncrono entre servicios

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja de entrada, alta al añadir resiliencia (reintentos, *timeouts*, Circuit Breaker) |
| Costo | Nulo: no se añade infraestructura |
| Escalabilidad | Baja. Cada consumidor nuevo obliga a modificar al llamador |
| Familiaridad del equipo | Alta |

**Pros:** trivial de depurar; consistencia inmediata; sin infraestructura extra.
**Contras:** **no demuestra EDA**, que es el objetivo del trabajo; acoplamiento temporal (si Payment está caído, el pedido no se crea); el fallo se propaga; sin log de eventos no hay *replay*.

### Opción C: RabbitMQ (cola de mensajes)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja a media |
| Costo | Nulo |
| Escalabilidad | Media. Buen enrutamiento, pero el mensaje se consume y desaparece |
| Familiaridad del equipo | Baja |

**Pros:** más sencillo de operar que Kafka; enrutamiento flexible con *exchanges*.
**Contras:** es una cola, no un log: sin retención no hay *replay* ni consumidores independientes que relean el histórico, que es justo lo que se quiere mostrar. Menos alineado con el enunciado del curso.

## Análisis de trade-offs

La Opción B es la más simple y la que menos infraestructura pide, pero elimina precisamente la propiedad que el prototipo debe demostrar: desacoplamiento en el tiempo. Se descarta por objetivo, no por calidad.

Entre A y C, lo decisivo es el **log persistente**. La recuperabilidad por *replay*, los grupos de consumidores independientes y el orden por `orderId` son propiedades que se quieren enseñar y verificar (ver [Atributos de calidad verificables](../atributos-de-calidad.md)), y en una cola tradicional no existen de la misma forma. El coste es aceptar consistencia eventual y diseñar de forma explícita la idempotencia ([ADR-09](ADR-09-idempotencia-con-eventid-y-processed-events.md)) y el tratamiento de errores ([ADR-05](ADR-05-reintentos-y-dlq.md)).

## Consecuencias

**Qué se vuelve más fácil**

- Añadir un consumidor nuevo a `orders.events` sin modificar Order Service (criterio verificable de desacoplamiento).
- Que un servicio caído no bloquee el flujo: Notification puede estar detenido y los pedidos igual alcanzan su estado final ([regla 12](../reglas-arquitectonicas.md)).
- Reprocesar eventos ya publicados con un grupo de consumidores nuevo.

**Qué se vuelve más difícil**

- La consistencia entre servicios pasa a ser **eventual**: el frontend tiene que tolerar estados intermedios.
- Cada consumidor debe ser idempotente, porque Kafka entrega *al menos una vez*.
- Depurar un flujo implica seguir un `correlationId` por varios servicios en lugar de una sola traza HTTP.

**Qué habrá que revisar**

- La atomicidad entre PostgreSQL y Kafka queda sin resolver; el riesgo se documenta en [ADR-08](ADR-08-sin-transactional-outbox.md).
- Si el número de servicios creciera, habría que revisar si la coreografía sigue siendo legible o hace falta un patrón de coordinación explícito (fuera del alcance del prototipo).

## Acciones

1. [x] Declarar Kafka en modo KRaft en `infrastructure/compose/docker-compose.yml` (HU-002).
2. [ ] Declarar los tópicos `orders.events`, `payments.events`, `notifications.events` y sus DLQ (HU-004).
3. [x] Prohibir explícitamente las llamadas REST entre servicios en las reglas arquitectónicas y en `CLAUDE.md`.
4. [ ] Verificar de forma ejecutable que no hay dependencias ni llamadas cruzadas entre servicios (HU-006).

## Táctica relacionada

Matriz de tácticas del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*): **Desacoplar en el tiempo** — EDA + Apache Kafka. Estado: *Implementar*.
