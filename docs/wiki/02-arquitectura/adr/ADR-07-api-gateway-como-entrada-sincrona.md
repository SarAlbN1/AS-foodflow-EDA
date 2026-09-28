# ADR-07: API Gateway como entrada síncrona controlada

**Estado:** Aprobado
**Fecha:** 2026-09-27 (fecha de registro del ADR; la decisión se tomó en la fase de diseño)
**Decisores:** Sara, Juan

## Contexto

El flujo interno es asíncrono por eventos, pero el cliente es una aplicación Angular y necesita una respuesta inmediata cuando crea un pedido y cuando lo consulta. Hace falta decidir **cómo entra** el mundo exterior al sistema.

Si Angular hablara directamente con los tres servicios, el frontend quedaría acoplado a la topología interna: tres URL, tres orígenes CORS, y cualquier cambio de despliegue rompería el cliente. Además, la correlación de extremo a extremo (`X-Correlation-Id`) necesita un punto único donde generarse si el cliente no la envía.

Hay también una tentación que hay que cerrar explícitamente: si existe un componente de entrada, es fácil que acabe conteniendo lógica —agregar respuestas de varios servicios, decidir el siguiente paso— y se convierta en el orquestador central que [ADR-01](ADR-01-kafka-como-broker.md) descarta.

## Decisión

Existe un **API Gateway** como **única entrada síncrona** al sistema. Reglas:

- Angular **solo** consume REST del gateway. Nunca accede a Kafka ni a PostgreSQL.
- El gateway **enruta y nada más**: sin reglas de negocio, sin agregación de respuestas, sin coordinar pasos del flujo.
- Protocolo del borde: **REST/JSON sobre HTTPS**. Protocolo interno: **eventos JSON sobre Kafka**.
- API mínima: `POST /orders`, `GET /orders/{id}`, `GET /orders/{id}/notifications`. No hay endpoint de consulta de pagos: el resultado del pago se observa por el estado del pedido.
- El gateway genera `X-Correlation-Id` si la solicitud no lo trae, y lo propaga.
- **El cliente solo crea el pedido.** El pago no se dispara por una llamada del cliente ni del gateway: lo dispara `OrderCreated`, que Payment Service consume.

## Opciones consideradas

### Opción A: API Gateway que solo enruta (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja. Reglas de enrutamiento y propagación de encabezados |
| Costo | Un contenedor más |
| Escalabilidad | Alta. El frontend no cambia si cambia el despliegue interno |
| Familiaridad del equipo | Media |

**Pros:** el frontend conoce una sola URL; punto único para `X-Correlation-Id` y para las políticas del borde (CORS, *timeouts*); el enrutamiento interno se puede cambiar sin tocar Angular; al prohibirle la lógica, no puede degenerar en orquestador.
**Contras:** un salto de red y un contenedor más; es un punto único de fallo del borde (aceptable: sin él no hay interfaz de todas formas).

### Opción B: Angular llama directamente a cada servicio

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja de entrada, alta de mantener |
| Costo | Ningún contenedor extra |
| Escalabilidad | Baja: el cliente se acopla a la topología |
| Familiaridad del equipo | Alta |

**Pros:** un salto menos; nada que desplegar.
**Contras:** el frontend queda acoplado a la topología interna (tres URL, tres configuraciones CORS); no hay punto único para generar la correlación; cada cambio de despliegue obliga a cambiar y volver a construir el cliente.

### Opción C: Gateway con lógica de agregación (BFF)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Alta |
| Costo | Un contenedor |
| Escalabilidad | Media |
| Familiaridad del equipo | Baja |

**Pros:** una sola llamada podría devolver el pedido junto con sus notificaciones, cómodo para la interfaz.
**Contras:** el gateway empieza a conocer reglas de negocio y a coordinar servicios; es el camino directo al **orquestador central** que el estilo coreografiado descarta. Además duplicaría conocimiento del dominio fuera de su servicio propietario.

### Opción D: El gateway (o el cliente) inicia el pago

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media |
| Costo | Ninguno |
| Escalabilidad | Baja |
| Familiaridad del equipo | Alta |

**Pros:** el cliente sabría el resultado del pago en la misma respuesta.
**Contras:** **rompe el estilo**. Convierte el borde en coordinador del flujo, reintroduce acoplamiento temporal (si Payment está caído, el pedido no se crea) y elimina la propiedad que el prototipo debe demostrar. Descartada por objetivo.

## Análisis de trade-offs

La Opción B ahorra un contenedor y lo paga con acoplamiento del frontend a la topología interna, que es un mal cambio incluso en un prototipo.

Lo importante no es *si* hay gateway, sino **qué se le prohíbe**. Las Opciones C y D son la misma pendiente resbaladiza: una vez que el borde puede componer o coordinar, la coreografía se erosiona hasta convertirse en orquestación, y con ella desaparece el desacoplamiento de [ADR-01](ADR-01-kafka-como-broker.md). Por eso la decisión no es solo "hay un gateway", sino "**el gateway enruta y nada más**", y por eso la ausencia de un endpoint de pagos es deliberada: sin él, no hay forma de pedirle al borde que coordine el pago.

La consecuencia aceptada es que el cliente no conoce el resultado del pago en la respuesta de creación. Se resuelve con consistencia eventual: el frontend consulta `GET /orders/{id}` hasta ver `PAGADO` o `PAGO_RECHAZADO`.

## Consecuencias

**Qué se vuelve más fácil**

- El frontend depende de una sola URL y un solo contrato.
- Aplicar políticas del borde (correlación, CORS, *timeouts*) en un único sitio.
- Mantener la coreografía intacta, porque el borde no tiene permiso para coordinar.

**Qué se vuelve más difícil**

- El cliente debe tolerar estados intermedios y consultar de nuevo para ver el resultado del pago.
- Un salto de red más en cada petición.
- Cada endpoint nuevo exige una decisión explícita: la API mínima es cerrada.

**Qué habrá que revisar**

- Si la interfaz necesitara una vista combinada, la respuesta correcta **no** es darle lógica al gateway: sería un ADR nuevo.
- La autenticación completa está fuera de alcance; si entrara, el gateway sería su lugar natural y haría falta un ADR.

## Acciones

1. [x] Declarar en las reglas arquitectónicas y en `CLAUDE.md` que Angular solo consume REST del gateway y que el gateway no lleva lógica de negocio.
2. [ ] Implementar el enrutamiento de las operaciones de pedidos (HU-401).
3. [ ] Generar y propagar `X-Correlation-Id` en el gateway cuando falte (HU-402).
4. [ ] Documentar los tres endpoints en `contracts/api/openapi.yaml` (HU-404, ver [ADR-12](ADR-12-contrato-rest-openapi-problem-details-idempotency-key.md)).
5. [ ] Verificar que el frontend no tiene ninguna dependencia de Kafka ni de PostgreSQL (HU-006).

## Táctica relacionada

Matriz de tácticas del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*): **Entrada síncrona controlada** — REST/JSON + API Gateway. Estado: *Implementar*.
