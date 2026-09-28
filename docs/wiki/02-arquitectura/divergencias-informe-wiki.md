# Divergencias informe–wiki

[← Índice de la wiki](../Home.md)

El informe técnico (`docs/informe/main.tex`) es la **fuente de verdad del diseño**; esta wiki lo describe, lo explica y deriva de él las reglas, los contratos y las historias. Cuando las dos no coinciden:

1. **Se corrige la wiki** en un PR que cite la sección del informe, y la fila queda en esta página como `Alineado`.
2. **Si el que parece equivocado es el informe**, la fila queda como `Pendiente de decisión` con el texto propuesto. Nadie edita `main.tex` salvo Sara: la corrección se propone, no se aplica.
3. Nunca se cambian los dos lados en direcciones opuestas ni se resuelve la contradicción en silencio.

Revisión de esta página: **2026-09-28**, contra `main.tex` de 1043 líneas (HU-008).

## Alineado: la wiki se corrigió para seguir al informe

| # | Tema | Informe | Qué se cambió en la wiki |
|---|---|---|---|
| D-1 | `Idempotency-Key` en `POST /orders` | «Requiere la cabecera `Idempotency-Key`» (línea 585) | [API REST](../03-contratos/api-rest.md): la cabecera pasa de **opcional** a **obligatoria**; sin ella, `400`. La exigencia la implementa HU-107 |
| D-2 | Estados de Pago | `APROBADO` o `RECHAZADO` (líneas 545 y 854) | [Persistencia](../03-contratos/persistencia.md): se fija el catálogo de `payments.status` y se anota que su `CHECK` entra en HU-202 |
| D-3 | Diagramas | El diseño se comunica con C4 y un modelo Structurizr único (línea 531) | Las nueve imágenes exportadas quedan versionadas y **se muestran** en las páginas de arquitectura, alcance, contratos, convenciones y runbook |

## Compatible: no hay contradicción

| # | Tema | Informe | Wiki |
|---|---|---|---|
| D-4 | Canal de notificación | «al menos un destino de contacto»; `Strategy` multicanal es *diseñado condicional* y «la demo puede limitarse a uno» (líneas 565 y 707) | El prototipo implementa solo `EMAIL`. La wiki **acota** dentro de lo que el informe permite |
| D-5 | Flyway y Testcontainers | Nivel *Opcional* | Opcionales, no requisito |

## Pendiente de decisión de Sara

Cada fila afecta contratos ya fusionados o código. **No se ha cambiado nada** en ninguno de los dos lados.

### D-6 — Quién dispara la notificación · **RESUELTO en el informe**

Decidido el **2026-09-28** por Sara y Juan: Notification Service consume **`payments.events`**, no `OrderStatusChanged`. **Los siete reemplazos ya están aplicados en `main.tex`**, así que el texto del informe y la wiki vuelven a decir lo mismo.

La wiki, las reglas 10 y 13, ADR-11, [Eventos](../03-contratos/eventos.md) y los seis esquemas de `contracts/events/v1/` **no cambiaron**: la decisión los confirma. Tampoco el código: en #71 Payment Service ya copia el `notificationContact` de `OrderCreated` para llevarlo al evento de pago.

**Por qué el abanico**

1. **El evento de pago cruza una sola vez la ventana de ADR-08.** Sin Outbox, toda publicación posterior a un commit puede perderse. En la cadena la notificación atravesaría esa ventana dos veces: si Order hace commit y no publica `OrderStatusChanged`, el pedido queda `PAGADO` y **no llega notificación nunca**, sin nada que lo reconcilie.
2. **Independencia frente a Order Service** (reglas 10 y 13): si Order se detiene, las notificaciones siguen saliendo.
3. **No alarga el camino crítico:** con la cadena, HU-301 a HU-304 dependerían de HU-106.

**Qué se pierde, y cómo se cubre.** La cadena garantizaba que, si existía notificación, el pedido ya había cambiado de estado. El abanico no: si Order no procesa `PaymentApproved` y el evento acaba en la DLQ, la notificación sale con el pedido en `CREADO`. Se cubre **redactando el mensaje sobre el resultado del pago y no sobre el estado del pedido** («tu pago fue aprobado», nunca «tu pedido está pagado»), fijado en [Proveedor de notificaciones](../03-contratos/proveedor-notificaciones.md) e implementado por HU-301.

> **Argumento descartado.** La primera versión justificaba el abanico con el criterio de Disponibilidad del informe: «detener Notification Service 30 s y comprobar que Order/Payment continúan». **No sirve para decidir**: Notification es el último eslabón en las dos topologías, así que esa prueba se cumple igual en la cadena, y al reiniciarla consume el evento retenido en Kafka. La que sí las distingue es **detener Order Service**, y el informe no la plantea.

**Pendiente: los diagramas.** La vista `Dynamic_OrderFlow` y el C2 todavía dibujan la cadena. Hay que corregir el modelo Structurizr y reexportar las imágenes: es **HU-704**. Mientras tanto, el texto del informe y sus figuras no coinciden en ese punto.

**No genera ADR nuevo:** confirma ADR-11 y la regla 10, no las cambia.

### D-7 — Nombre del campo de versión del envelope

| | |
|---|---|
| **Informe** | `schemaVersion` (líneas 435, 462, 652, 702) |
| **Contratos** | `eventVersion` en `contracts/events/v1/envelope.schema.json`, los seis esquemas, los 21 ejemplos, `scripts/validate_events.py` y el README de contratos (HU-003, ya fusionada) |
| **Alcance del cambio si gana el informe** | Renombrar el campo en la v1 completa: esquemas, ejemplos, validador y documentación. Un renombre incompatible normalmente crearía `contracts/events/v2/`, pero aquí ningún servicio produce eventos todavía, así que puede hacerse sobre la v1 |
| **Recomendación** | Ajustar el informe a `eventVersion`. Es un nombre, no una decisión de diseño, y ya está en contratos versionados y en el catálogo de eventos |

### D-8 — Identificador del agregado en el envelope

| | |
|---|---|
| **Informe** | Campo común **`orderId`** en la raíz del envelope (líneas 652, 702) |
| **Contratos** | **`aggregateId`** en la raíz (clave de partición, ADR-04) y `orderId` dentro del `payload`. El validador comprueba que `aggregateId == payload.orderId` |
| **Alcance del cambio si gana el informe** | Renombrar la raíz del envelope y rehacer la comprobación del validador; ADR-04 dejaría de poder hablar de «clave de partición del agregado» en general |
| **Recomendación** | Ajustar el informe: `aggregateId = orderId` dice lo mismo y deja el envelope reutilizable. El informe ya usa esa idea en ADR-04 («Orden por `orderId`», línea 620) |

### D-9 — Nombre del token de pago simulado

| | |
|---|---|
| **Informe** | `paymentTestToken` (líneas 565, 767, 788, 854) |
| **Wiki, contratos y código** | `paymentToken` en [API REST](../03-contratos/api-rest.md), `order-created.schema.json`, la columna `payment_token` de Order DB (HU-002) y el código de Order Service (HU-101) |
| **Alcance del cambio si gana el informe** | Renombrar el campo de la API, el esquema de `OrderCreated`, la columna de Order DB y el código ya entregado |
| **Recomendación** | Ajustar el informe a `paymentToken`. Que el token sea de prueba ya lo dice ADR-10 y el propio texto del informe; el sufijo `Test` en el nombre del campo obligaría a renombrarlo cuando el pago deje de ser simulado |

### D-10 — Clave de idempotencia hacia el proveedor de notificaciones

| | |
|---|---|
| **Informe** | «se envía `notificationId` como clave de idempotencia al adaptador/mock del proveedor» (línea 572) |
| **Contrato** | [Proveedor de notificaciones](../03-contratos/proveedor-notificaciones.md): `POST /v1/messages` con `{channel, destination, content, correlationId}`. El mock de HU-306 (ya fusionado, de Juan) no lee ninguna clave de idempotencia |
| **Alcance del cambio si gana el informe** | Añadir el campo al contrato del proveedor, al mock de HU-306 y al adaptador de HU-302; el mock tendría que recordar las claves vistas por destino |
| **Recomendación** | Decidir antes de HU-302. Si se quiere la garantía, conviene añadirla al contrato del mock; si no, quitar la frase del informe. Hoy el reintento del adaptador **puede** producir un segundo envío del mismo mensaje, que es lo que esa frase dice que no debe pasar |

### D-11 — Trazabilidad táctica → ADR y la palabra «Parcial»

| | |
|---|---|
| **Informe** | La matriz de tácticas (línea 414) es general y no nombra ADR; la trazabilidad vive en la tabla de decisiones de FoodFlow (línea 620), que sí lista ADR-01 a ADR-12. La recuperabilidad se describe como «Alta para eventos publicados, condicionada por la integración» (línea 386) |
| **Wiki** | El criterio 4 de HU-008 pide que «la matriz de tácticas incorpore ADR-08 a ADR-11 y la recuperabilidad figure como **Parcial**», y ADR-08 y [Atributos de calidad](atributos-de-calidad.md) usan `Parcial` |
| **Alcance del cambio** | Dos líneas del informe (una frase de cierre tras la matriz y la segunda columna de la fila Recuperabilidad), o bien reescribir el criterio 4 de HU-008 para describir la estructura nueva |
| **Recomendación** | Aplicar las dos líneas al informe: el texto propuesto está en [Correcciones propuestas HU-008](../../informe/correcciones-propuestas-HU-008.md), propuesta 1. «Parcial» es el término que ya usan ADR-08 y la wiki, y «Alta» puede leerse como garantía de que no se pierden eventos |

### D-12 — Una referencia definida y no citada

| | |
|---|---|
| **Informe** | `\bibitem{spring-kafka-retry}` está en la bibliografía y no se cita en el texto (49 de 50 referencias citadas; ninguna cita sin definir) |
| **Wiki** | El criterio 7 de HU-008 pide citarla o eliminarla |
| **Recomendación** | Citarla en el párrafo de reintentos y DLQ. Texto exacto en [Correcciones propuestas HU-008](../../informe/correcciones-propuestas-HU-008.md), propuesta 2 |

## Cómo se cierra una fila

Cuando Sara decide, la fila se mueve a **Alineado** (si se corrigió la wiki) o desaparece de esta página (si se corrigió el informe), citando en el PR la sección del informe y las páginas afectadas. Si la decisión cambia una regla, un tópico, un estado o el alcance, además se registra como ADR nuevo.

Referencias: [`CLAUDE.md`](../../../CLAUDE.md) §1 · [Informe técnico](../../informe/README.md) · [Puntos abiertos](puntos-abiertos.md)
