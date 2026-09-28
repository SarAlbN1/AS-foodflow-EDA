# ADR-10: Pago determinista con `PAY-OK` y `PAY-FAIL`

**Estado:** Aprobado
**Fecha:** 2026-09-27 (fecha de registro del ADR; la decisión se tomó en la fase de diseño)
**Decisores:** Sara, Juan

## Contexto

El prototipo debe demostrar **los dos caminos** del flujo: pago aprobado y pago rechazado. Cada camino produce eventos distintos, un estado final distinto del pedido y una notificación distinta.

Integrar una pasarela de pago real está fuera de alcance por completo: exige credenciales, datos sensibles, red externa y cumplimiento. Pero incluso una simulación admite variantes, y la elección importa para la sustentación: si el resultado del pago fuera aleatorio, no se podría reproducir un escenario concreto delante del profesor ni escribir una prueba automática que espere un resultado determinado.

## Decisión

El pago es **determinista** y lo decide un único campo de la solicitud, `paymentToken`, que viaja en `OrderCreated`:

| `paymentToken` | Payment Service publica | Estado final del pedido |
|---|---|---|
| `PAY-OK` | `PaymentApproved` | `PAGADO` |
| `PAY-FAIL` | `PaymentRejected` | `PAGO_RECHAZADO` |

Cualquier otro valor se rechaza en la validación de `POST /orders` con `400` ([ADR-06](ADR-06-validacion-y-eventos-explicitos-de-resultado.md)), así que Payment Service nunca recibe un token desconocido por la vía normal.

No se integra ninguna pasarela real ni se genera aleatoriedad. El campo lo define el cliente de la petición, y el esquema de `OrderCreated` restringe sus valores a esos dos con un `enum`.

## Opciones consideradas

### Opción A: Token determinista en la solicitud (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Muy baja. Un campo validado y una bifurcación |
| Costo | Ninguno |
| Escalabilidad | Irrelevante |
| Familiaridad del equipo | Alta |

**Pros:** cada camino se reproduce a voluntad, lo que hace posibles las pruebas automáticas y una demostración sin sorpresas; el valor va en el contrato (`enum` en el esquema), así que es autoexplicativo; coste de implementación casi nulo, y el foco queda en lo que se quiere demostrar (el flujo de eventos, no la lógica de cobro).
**Contras:** no demuestra ninguna integración con un sistema de pago real; el "pago" no tiene lógica propia, así que Payment Service es deliberadamente delgado; un campo de la petición decide el resultado, lo que en un sistema real sería un agujero de seguridad evidente (aquí es explícitamente un simulador).

### Opción B: Resultado aleatorio (por ejemplo, 80 % de aprobación)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | Ninguno |
| Escalabilidad | Irrelevante |
| Familiaridad del equipo | Alta |

**Pros:** parece más realista; produce los dos caminos sin que el cliente los pida.
**Contras:** **no es reproducible**. Una prueba automática se vuelve inestable y una demostración puede dar el camino contrario al que se quería mostrar. Habría que añadir una forma de forzar el resultado para poder probar, con lo que se acaba en la Opción A pero con código extra.

### Opción C: Pasarela de pago real en modo *sandbox*

| Dimensión | Evaluación |
|---|---|
| Complejidad | Alta. Credenciales, *webhooks*, reintentos, datos sensibles |
| Costo | Alto en tiempo; dependencia de una red y una cuenta externas |
| Escalabilidad | Irrelevante |
| Familiaridad del equipo | Baja |

**Pros:** integración realista; obligaría a tratar de verdad la asincronía de un tercero.
**Contras:** completamente fuera de alcance; introduce datos sensibles y una dependencia externa en un entorno que debe ser reproducible en local; la demostración dependería de que un servicio ajeno esté disponible. Descartada.

### Opción D: Regla sobre un dato del pedido (por ejemplo, rechazar si `total > X`)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | Ninguno |
| Escalabilidad | Irrelevante |
| Familiaridad del equipo | Alta |

**Pros:** determinista y sin campo adicional en el contrato; parece lógica de negocio de verdad.
**Contras:** es una regla **inventada** que no existe en el dominio y que habría que explicar y recordar; mezcla la simulación con datos reales del pedido, así que un cambio de `total` por otro motivo alteraría el resultado del pago. El token es más honesto: dice a las claras que es un simulador.

## Análisis de trade-offs

La **reproducibilidad** es el criterio que decide. El prototipo se evalúa en una sustentación y se verifica con un `smoke-test.sh` que debe ejercitar los dos caminos; ambas cosas exigen poder pedir un resultado concreto. La Opción B lo impide y la C lo hace depender de un tercero.

Entre A y D, las dos son deterministas. A gana por **claridad de intención**: un campo llamado `paymentToken` con los valores `PAY-OK` y `PAY-FAIL` no se puede confundir con lógica de negocio real, mientras que una regla sobre el `total` parece una regla del dominio y acopla el resultado del pago a un dato que cambia por otras razones.

El coste aceptado es que Payment Service queda delgado: no hay lógica de cobro que mostrar. Es coherente con el objetivo —lo que se demuestra es la **coreografía** de eventos, no un motor de pagos— y con YAGNI.

## Consecuencias

**Qué se vuelve más fácil**

- Demostrar los dos caminos a voluntad en la sustentación (`PAY-OK` y `PAY-FAIL`).
- Escribir pruebas automáticas estables de punta a punta.
- Explicar Payment Service: consume `OrderCreated`, mira el token, publica el resultado.

**Qué se vuelve más difícil**

- No se demuestra ninguna integración con un sistema de pago real ni el manejo de su asincronía.
- Payment Service tiene poca lógica propia, lo que puede parecer un servicio "vacío" si no se explica que es deliberado.

**Qué habrá que revisar**

- El campo `paymentToken` es el punto de entrada del pago (supuesto A-1, confirmado): si cambiara de nombre o de ubicación, habría que actualizar `contracts/api/openapi.yaml`, el esquema de `OrderCreated` y este ADR.
- El catálogo de `reasonCode` de `PaymentRejected` está **sin definir** y se decide en HU-202. Mientras tanto, el esquema no lo restringe con un `enum`.

## Acciones

1. [x] Restringir `paymentToken` a `PAY-OK` y `PAY-FAIL` en el esquema de `OrderCreated` (HU-003).
2. [x] Restringir `orders.payment_token` a esos dos valores en el esquema de Order DB (HU-002).
3. [ ] Validar `paymentToken` en `POST /orders` y devolver `400` para cualquier otro valor (HU-101).
4. [ ] Implementar la bifurcación determinista en Payment Service (HU-202).
5. [ ] Definir el catálogo de `reasonCode` y añadir su `enum` al esquema (HU-202).
6. [ ] Ejercitar los dos caminos en `scripts/smoke-test.sh` (HU-607) y en el runbook de la demostración (HU-706).

## Táctica relacionada

Matriz de tácticas del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*): **Pago reproducible** — Payment Service + simulador. Estado: *Implementar*.
