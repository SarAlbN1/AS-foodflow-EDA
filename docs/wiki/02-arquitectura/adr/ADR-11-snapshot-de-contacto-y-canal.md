# ADR-11: Snapshot de contacto y canal de notificación en el pedido

**Estado:** Aprobado
**Fecha:** 2026-09-27 (fecha de registro del ADR; la decisión se tomó en la fase de diseño)
**Decisores:** Sara, Juan

## Contexto

Notification Service necesita saber **a dónde** y **por qué canal** avisar al cliente. Ese dato lo aporta el cliente al crear el pedido (`customerContact` y `notificationChannel` en `POST /orders`) y vive en Order DB, propiedad exclusiva de Order Service ([ADR-03](ADR-03-una-base-postgresql-por-servicio.md)).

Notification Service **no puede consultar Order DB**: lo prohíbe la propiedad exclusiva de los datos. Así que el dato tiene que llegarle por el único camino permitido, un evento. La pregunta es **en qué evento** y **con qué semántica**.

Y hay una restricción adicional que condiciona la respuesta: la [regla 6](../reglas-arquitectonicas.md) exige que Order Service y Notification Service reaccionen de forma **independiente** al resultado del pago. Si Notification tuviera que esperar `OrderStatusChanged` —que publica Order Service **después** de procesar el pago— su notificación dependería de que Order Service hubiera hecho su trabajo primero, y la independencia desaparecería.

## Decisión

El pedido guarda un **snapshot** del contacto y el canal en el momento de su creación, y ese snapshot **viaja por los eventos** como un objeto `notificationContact`:

```json
{ "channel": "EMAIL", "destination": "cliente@ejemplo.com" }
```

- Order Service lo persiste en `orders` (`notification_channel`, `customer_contact`) al crear el pedido y lo incluye en `OrderCreated`.
- **Payment Service lo copia** desde `OrderCreated` a `PaymentApproved` y `PaymentRejected`.
- **Notification Service consume `payments.events`** y obtiene el contacto del propio evento de pago. **No depende de `OrderStatusChanged`** y **nunca consulta Order DB**.
- `OrderStatusChanged` también lo transporta, pero solo para consumidores futuros.

Es un **snapshot**, no una referencia: es el contacto tal como estaba cuando se creó el pedido. Si el cliente cambiara su correo después, la notificación de ese pedido sigue yendo al contacto capturado, porque el evento es un hecho histórico inmutable.

El `destination` es **dato personal**: se registra enmascarado en los logs (`a***@dominio.com`).

Esta decisión resuelve el supuesto abierto **A-2**.

## Opciones consideradas

### Opción A: Snapshot en el pedido, propagado en los eventos de pago (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja. Un objeto más en cuatro esquemas de evento |
| Costo | Duplicación controlada del dato en cada evento |
| Escalabilidad | Alta. Notification no consulta a nadie; escala sin tocar Order |
| Familiaridad del equipo | Alta |

**Pros:** Notification Service es autónomo: todo lo que necesita está en el evento que consume, así que **preserva la regla 6**; el evento es autocontenido, que es lo correcto para un hecho inmutable; el histórico es fiel, porque refleja el contacto del momento del pedido; no hay llamada síncrona ni consulta cruzada de base.
**Contras:** el dato se duplica en `OrderCreated`, `OrderStatusChanged`, `PaymentApproved` y `PaymentRejected`; **Payment Service transporta un dato personal que no le pertenece**, lo que amplía la superficie de exposición y obliga a enmascararlo también en sus logs; si el catálogo de canales creciera, los cuatro esquemas cambian.

### Opción B: Notification Service consulta Order Service por REST

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media |
| Costo | Un endpoint más y una llamada por notificación |
| Escalabilidad | Baja: acoplamiento temporal |
| Familiaridad del equipo | Alta |

**Pros:** el dato no se duplica y siempre está actualizado; los eventos quedan más pequeños.
**Contras:** **viola la regla 4** (los servicios no se llaman por REST para coordinar el flujo) y reintroduce acoplamiento temporal: si Order Service está caído, no hay notificación. Además haría falta un endpoint que la API mínima no contempla.

### Opción C: Notification Service lee Order DB

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | Ninguno aparente |
| Escalabilidad | Baja |
| Familiaridad del equipo | Alta |

**Pros:** el camino más corto de todos.
**Contras:** **viola la regla 2** y es el antipatrón *Shared Database* que [ADR-03](ADR-03-una-base-postgresql-por-servicio.md) descarta expresamente. Descartada sin más análisis.

### Opción D: El contacto viaja solo en `OrderStatusChanged`

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | El dato se duplica en menos eventos |
| Escalabilidad | Media |
| Familiaridad del equipo | Alta |

**Pros:** el dato viaja en menos eventos y Payment Service no toca datos personales, lo que reduce la superficie de exposición.
**Contras:** **rompe la regla 6**. Notification tendría que esperar a que Order Service procese el pago y publique el cambio de estado, así que su reacción dejaría de ser independiente: un fallo o un retraso de Order Service retrasaría la notificación. Además encadena los servicios en una secuencia, que es un paso hacia la orquestación.

## Análisis de trade-offs

Las Opciones B y C se descartan por incumplir reglas arquitectónicas, no por coste.

La decisión real es **A frente a D**, y es el punto más delicado de este ADR. D es atractiva por privacidad: menos eventos con datos personales y Payment Service sin tocar el contacto del cliente. Pero paga un precio arquitectónico alto: encadena Notification a Order, y con ello pierde la reacción independiente al resultado del pago que la regla 6 exige y que es una de las propiedades que el prototipo debe demostrar (con Notification detenido, los pedidos alcanzan igualmente su estado final).

Se elige A y se mitiga su contra: el `destination` se enmascara en **todos** los logs, incluidos los de Payment Service, y el esquema del evento lo marca explícitamente como dato personal.

La naturaleza de **snapshot** —y no de referencia— es lo que hace que la duplicación sea correcta en lugar de ser una caché mal hecha: el evento registra un hecho pasado, y el contacto de ese hecho es el que había entonces. No hay nada que invalidar.

## Consecuencias

**Qué se vuelve más fácil**

- Notification Service es autónomo: no consulta ninguna base ni servicio ajeno.
- Se preserva la reacción independiente al resultado del pago (regla 6) y la tolerancia al fallo de Notification (regla 12).
- El histórico es fiel: cada notificación fue al contacto vigente cuando se creó el pedido.

**Qué se vuelve más difícil**

- El contacto se duplica en cuatro esquemas de evento; un cambio de forma afecta a los cuatro.
- Payment Service maneja un dato personal ajeno a su dominio, así que debe enmascararlo en sus logs igual que Notification.
- Un cambio posterior del contacto del cliente **no** afecta a los pedidos ya creados (es lo pretendido, pero hay que explicarlo).

**Qué habrá que revisar**

- El canal es hoy solo `EMAIL`. Añadir otro (SMS, push) obliga a revisar los cuatro esquemas y la validación de `POST /orders`.
- El enmascarado debe verificarse en los tres servicios, no solo en Notification.
- El informe técnico decía que Notification consume **únicamente** `OrderStatusChanged`; eso contradice esta decisión y la regla 6, y se corrige en HU-008.

## Acciones

1. [x] Persistir `notification_channel` y `customer_contact` en `orders`, marcados como dato personal (HU-002).
2. [x] Incluir `notificationContact` (`{channel, destination}`) en los esquemas de `OrderCreated`, `OrderStatusChanged`, `PaymentApproved` y `PaymentRejected` (HU-003).
3. [ ] Capturar el snapshot al crear el pedido y publicarlo en `OrderCreated` (HU-101, HU-103).
4. [ ] Copiar el snapshot desde `OrderCreated` a los eventos de pago en Payment Service (HU-203).
5. [ ] Consumir `payments.events` en Notification Service y tomar el contacto del evento (HU-301).
6. [ ] Enmascarar el `destination` en los logs de los **tres** servicios (HU-604).
7. [ ] Corregir en `main.tex` la afirmación de que Notification consume únicamente `OrderStatusChanged` (HU-008).

## Táctica relacionada

Matriz de tácticas del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*): **Datos de contacto** — Evento versionado + snapshot. Estado: *Implementar*.
