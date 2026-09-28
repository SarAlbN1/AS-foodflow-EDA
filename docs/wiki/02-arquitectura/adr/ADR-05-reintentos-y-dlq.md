# ADR-05: Reintentos controlados y DLQ

**Estado:** Aprobado
**Fecha:** 2026-09-27 (fecha de registro del ADR; la decisión se tomó en la fase de diseño)
**Decisores:** Sara, Juan

## Contexto

Un consumidor Kafka falla por razones muy distintas y tratarlas igual produce dos desastres simétricos:

- Si todo se reintenta para siempre, un mensaje que **nunca** podrá procesarse (JSON corrupto, versión de esquema desconocida) bloquea su partición indefinidamente y detiene el flujo de todos los pedidos que caen en ella.
- Si nada se reintenta, una caída momentánea de PostgreSQL de dos segundos descarta eventos que eran perfectamente válidos.

Además hay un tercer caso que no es un error técnico en absoluto: que el **proveedor externo de notificaciones rechace el envío** tras sus reintentos. Eso es un resultado de negocio legítimo —la notificación falló— y tratarlo como un fallo de infraestructura llenaría la DLQ de hechos normales del dominio.

## Decisión

Se clasifica cada fallo y se le da un tratamiento distinto:

| Tipo de fallo | Ejemplo | Tratamiento |
|---|---|---|
| **Técnico recuperable** | Base momentáneamente caída, *timeout* de red | Reintentar: **3 intentos**, espera inicial **1 s**, multiplicador **2**. Si persiste, DLQ |
| **Técnico no recuperable** | Mensaje corrupto, esquema o `eventVersion` no soportada | **DLQ directo, sin reintentos** |
| **De negocio** | El proveedor de notificaciones falla tras sus propios reintentos | La notificación pasa a `FALLIDA`, se publica `NotificationFailed`, **se confirma el offset** y **NO va a DLQ** |

Cada tópico principal tiene su DLQ: `orders.events.dlq`, `payments.events.dlq`, `notifications.events.dlq`. Los mensajes corruptos se toleran con `ErrorHandlingDeserializer`, de modo que un fallo de deserialización no tumba el contenedor del consumidor. El offset se confirma **manualmente y solo después** del commit de la transacción local.

**No se implementa Circuit Breaker.**

## Opciones consideradas

### Opción A: Clasificación por tipo de fallo, con reintentos acotados y DLQ (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media. Hay que distinguir excepciones recuperables de no recuperables y configurar el manejador de errores |
| Costo | Tres tópicos DLQ adicionales |
| Escalabilidad | Alta: una partición nunca queda bloqueada por un mensaje imposible |
| Familiaridad del equipo | Media. Spring for Apache Kafka lo soporta de forma nativa con `DefaultErrorHandler` |

**Pros:** un mensaje imposible no bloquea la partición; un fallo transitorio no pierde el evento; la DLQ contiene **solo** lo que de verdad requiere intervención humana, así que es útil como herramienta de diagnóstico en la demostración.
**Contras:** hay que decidir explícitamente qué excepción es recuperable y qué excepción no lo es, y equivocarse en esa clasificación es un fallo silencioso; añade tres tópicos y la operación de inspeccionarlos.

### Opción B: Reintentos infinitos, sin DLQ

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | Ninguno |
| Escalabilidad | **Muy baja**: un mensaje imposible bloquea su partición para siempre |
| Familiaridad del equipo | Alta |

**Pros:** no se pierde ningún evento; nada que configurar.
**Contras:** un único mensaje corrupto detiene el procesamiento de su partición y, con él, todos los pedidos que comparten esa clave de partición. Inaceptable.

### Opción C: Sin reintentos; descartar y registrar

| Dimensión | Evaluación |
|---|---|
| Complejidad | La más baja |
| Costo | Ninguno |
| Escalabilidad | Alta |
| Familiaridad del equipo | Alta |

**Pros:** nunca se bloquea nada; trivial de razonar.
**Contras:** una caída de base de un segundo pierde eventos de forma definitiva, y con ellos la consistencia del pedido. Convierte un problema transitorio en pérdida de datos.

### Opción D: Opción A más Circuit Breaker en el cliente del proveedor

| Dimensión | Evaluación |
|---|---|
| Complejidad | Alta: un componente y un estado más que entender y calibrar |
| Costo | Una dependencia adicional |
| Escalabilidad | Alta |
| Familiaridad del equipo | Baja |

**Pros:** protege al proveedor externo de una avalancha de reintentos cuando ya está caído.
**Contras:** el proveedor es un **mock local**, así que no hay nada real que proteger; calibrar los umbrales de apertura y cierre consume tiempo sin aportar nada demostrable. Está en la lista explícita de "no implementar".

## Análisis de trade-offs

Las Opciones B y C son los dos extremos y ambas fallan por el mismo motivo: tratan todos los errores como si fueran del mismo tipo. La decisión real es **aceptar el coste de clasificar**.

Dentro de esa clasificación, el punto más importante y menos obvio es el tercer caso: el fallo del proveedor **no** va a DLQ. Si fuera a DLQ, cada notificación que falla legítimamente aparecería como un incidente de infraestructura, la DLQ perdería su valor diagnóstico, y el offset quedaría sin confirmar para un mensaje que en realidad **sí se procesó correctamente** —el resultado simplemente fue "falló el envío". Modelarlo como estado `FALLIDA` más el evento `NotificationFailed` mantiene el hecho dentro del dominio, donde pertenece.

La Opción D se descarta por alcance: es una táctica correcta contra una dependencia real, y aquí la dependencia es un mock.

## Consecuencias

**Qué se vuelve más fácil**

- Diagnosticar: lo que hay en una DLQ requiere de verdad intervención.
- Sobrevivir a fallos transitorios sin perder eventos.
- Demostrar los tres caminos en la sustentación: éxito, fallo de negocio (`*@fail.test`) e inspección de DLQ.

**Qué se vuelve más difícil**

- Clasificar bien cada excepción; una clasificación equivocada convierte un fallo permanente en un bucle de reintentos, o descarta a DLQ algo recuperable.
- Hay que vaciar o inspeccionar las DLQ manualmente: no existe reproceso automático (y no se va a implementar).

**Qué habrá que revisar**

- Los números (3 intentos, 1 s, multiplicador 2) son propuestos y se calibran junto con los umbrales de calidad en HU-608.
- Si el proveedor dejara de ser un mock, habría que reconsiderar el Circuit Breaker en un ADR nuevo.

## Acciones

1. [ ] Declarar los tres tópicos DLQ (HU-004).
2. [ ] Configurar `ErrorHandlingDeserializer` y el manejador de errores con 3 intentos, espera 1 s y multiplicador 2 en cada consumidor (HU-601).
3. [ ] Confirmar el offset manualmente, solo tras el commit local, en los tres consumidores.
4. [ ] Implementar el fallo del proveedor como estado `FALLIDA` + `NotificationFailed`, sin DLQ (HU-302).
5. [ ] Documentar cómo inspeccionar una DLQ durante la demostración (HU-004, `infrastructure/kafka/topics.md`).
6. [ ] Provocar cada uno de los tres tipos de fallo y dejar evidencia (HU-601, HU-608).

## Táctica relacionada

Matriz de tácticas del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*): **Tolerancia a errores de consumo** — Kafka + retry + DLQ. Estado: *Implementar*.
