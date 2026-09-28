# ADR-06: Validación en la entrada y eventos explícitos de resultado

**Estado:** Aprobado
**Fecha:** 2026-09-27 (fecha de registro del ADR; la decisión se tomó en la fase de diseño)
**Decisores:** Sara, Juan

## Contexto

Dos preguntas relacionadas:

1. **¿Dónde se valida?** Si cada consumidor tiene que defenderse de datos inválidos, la misma validación se duplica en tres servicios y acaba divergiendo. Si no se valida en ninguna parte, un pedido con total negativo se propaga por todo el flujo.
2. **¿Cómo se comunica el resultado de un paso?** Un evento genérico del tipo `PaymentProcessed` con un campo `status` obliga a cada consumidor a inspeccionar el campo para saber qué pasó, y a manejar el valor inesperado. Un evento cuyo **nombre** es el resultado se puede enrutar y filtrar sin abrir el payload.

## Decisión

**Validación en la entrada.** Order Service valida la solicitud en `POST /orders` con Bean Validation antes de persistir nada: `customerReference` obligatorio y de máximo 60 caracteres, `customerContact` un correo válido, `total` decimal mayor que cero con escala 2, `paymentToken` exactamente `PAY-OK` o `PAY-FAIL`. Un incumplimiento produce `400` con Problem Details y **no** publica ningún evento.

**Eventos explícitos de resultado.** El resultado de cada paso se modela con un evento cuyo nombre es el hecho ocurrido, no con un campo de estado dentro de un evento genérico:

- Pago: `PaymentApproved` / `PaymentRejected`
- Pedido: `OrderStatusChanged`
- Notificación: `NotificationSent` / `NotificationFailed`

Los nombres van en **pasado** (principio de mínima sorpresa): describen hechos ya ocurridos e inmutables, no órdenes. Por eso el evento se llama `OrderStatusChanged` y **nunca `OrderUpdated`**.

## Opciones consideradas

### Opción A: Validar en la entrada + un evento por resultado (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja. Anotaciones de Bean Validation y dos esquemas en lugar de uno por paso |
| Costo | Ninguno |
| Escalabilidad | Alta. Un consumidor puede suscribirse solo al resultado que le interesa |
| Familiaridad del equipo | Alta |

**Pros:** los datos inválidos se detienen en la frontera, así que los consumidores confían en lo que reciben y no duplican reglas (DRY); el enrutamiento por tipo de evento no exige abrir el payload; el evento sigue siendo un hecho inmutable con un nombre que dice exactamente qué pasó; un valor de estado inesperado es imposible por construcción.
**Contras:** el doble de esquemas para pago y notificación; si se añadiera un tercer resultado (por ejemplo un pago pendiente), habría que crear otro evento y otro esquema, no un valor de enum.

### Opción B: Validar en cada consumidor

| Dimensión | Evaluación |
|---|---|
| Complejidad | Alta: la misma regla en tres sitios |
| Costo | Ninguno |
| Escalabilidad | Baja: cada consumidor nuevo hereda la obligación de validar |
| Familiaridad del equipo | Alta |

**Pros:** cada servicio se defiende solo, sin confiar en nadie.
**Contras:** duplica reglas de negocio entre servicios y las hace divergir con el tiempo (viola DRY); el dato inválido llega igualmente al broker y queda en el log de Kafka como un hecho, aunque nunca debió existir; empuja hacia una librería de validación compartida, que es justo la dependencia entre servicios que la regla 8 prohíbe.

### Opción C: Un evento genérico con campo de estado

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media |
| Costo | Ninguno |
| Escalabilidad | Media: todos los consumidores reciben todo y filtran |
| Familiaridad del equipo | Alta |

**Pros:** menos esquemas que mantener; añadir un resultado nuevo es añadir un valor al enum.
**Contras:** cada consumidor debe inspeccionar el payload para decidir si le interesa, y manejar el estado desconocido; no se puede filtrar ni enrutar por tipo de evento; el nombre del evento deja de describir el hecho, lo que va contra la mínima sorpresa; los estados posibles se convierten en un contrato implícito dentro de un campo.

## Análisis de trade-offs

Sobre la validación: la Opción B parece más robusta —"no confíes en nadie"— pero su coste real es la duplicación de reglas de negocio entre servicios, que es precisamente el acoplamiento que [ADR-03](ADR-03-una-base-postgresql-por-servicio.md) y la regla 8 intentan evitar. La entrada única al sistema es `POST /orders`; validar ahí es validar una vez. Los consumidores siguen validando la **forma** del mensaje contra su esquema (lo que protege de un productor mal implementado), pero no reimplementan las reglas del **negocio**.

Sobre los eventos: la Opción C ahorra esquemas y lo paga con un contrato peor. La diferencia decisiva es que con eventos explícitos el enrutamiento es declarativo y el nombre es autoexplicativo. Que Order Service y Notification Service reaccionen de forma independiente al mismo resultado ([regla 6](../reglas-arquitectonicas.md)) es mucho más limpio cuando cada uno se suscribe a un hecho con nombre propio. El coste —el doble de esquemas— es bajo porque comparten el envelope común.

## Consecuencias

**Qué se vuelve más fácil**

- Los consumidores confían en la validez de negocio de lo que reciben.
- Enrutar y filtrar por tipo de evento sin inspeccionar el payload.
- Devolver errores claros al cliente: `400` con Problem Details antes de publicar nada.
- Documentar el catálogo de eventos, porque cada nombre es un hecho.

**Qué se vuelve más difícil**

- Añadir un resultado nuevo cuesta un esquema, no un valor de enum.
- Mantener el doble de esquemas para pago y notificación (mitigado por el envelope común de [ADR-04](ADR-04-clave-de-particion-por-orderid.md) y HU-003).

**Qué habrá que revisar**

- Cualquier estado intermedio del pago (por ejemplo "pendiente") exigiría un evento nuevo y una decisión explícita, no un campo.
- Las reglas de validación viven en un único sitio: si cambian, hay que actualizar también `contracts/api/openapi.yaml`.

## Acciones

1. [x] Fijar en los esquemas de eventos que `eventType` solo admite los seis nombres, y que `OrderUpdated` no existe (HU-003).
2. [ ] Implementar la validación de `POST /orders` con Bean Validation y respuesta `400` en Problem Details (HU-101).
3. [ ] Publicar `PaymentApproved` / `PaymentRejected` según el resultado (HU-202, HU-203).
4. [ ] Publicar `OrderStatusChanged` tras registrar el resultado del pago (HU-104).
5. [ ] Publicar `NotificationSent` / `NotificationFailed` según el resultado del proveedor (HU-302).

## Táctica relacionada

Matriz de tácticas del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*): **Validación y resultados explícitos** — Spring Boot + Bean Validation. Estado: *Implementar*.
