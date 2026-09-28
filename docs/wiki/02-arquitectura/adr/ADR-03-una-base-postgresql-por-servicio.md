# ADR-03: Una base PostgreSQL por servicio

**Estado:** Aprobado
**Fecha:** 2026-09-27 (fecha de registro del ADR; la decisión se tomó en la fase de diseño)
**Decisores:** Sara, Juan

## Contexto

Los tres servicios ([ADR-02](ADR-02-tres-servicios-de-negocio.md)) necesitan persistencia. La decisión es si comparten una base o cada uno tiene la suya.

Compartir una base es tentador en un prototipo local: un contenedor menos, un solo esquema, consultas con `JOIN` entre pedidos y pagos sin pasar por eventos. Pero es también el antipatrón clásico de los microservicios: *Shared Database* / *Database-as-IPC*. En cuanto dos servicios leen la misma tabla, el esquema se convierte en un contrato implícito que nadie versiona y el desacoplamiento que aporta Kafka queda anulado por la puerta de atrás.

## Decisión

**Cada servicio es el único propietario de su base PostgreSQL:** `order-db`, `payment-db` y `notification-db`, cada una con su **propia base de datos, su propio usuario y su propia contraseña**.

Reglas que se derivan:

- Ningún servicio recibe la URL ni las credenciales de una base ajena.
- Ningún servicio lee ni escribe tablas de otro, ni comparte entidades JPA o repositorios.
- Si un servicio necesita un dato de otro, llega por **evento**, nunca por consulta a su base.
- **Ninguna base publica ni consume eventos**, y Kafka nunca escribe en PostgreSQL.

## Opciones consideradas

### Opción A: Una base por servicio, credenciales separadas (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media. Tres contenedores de base, tres esquemas, tres juegos de credenciales |
| Costo | Tres contenedores PostgreSQL en la máquina de desarrollo |
| Escalabilidad | Alta. Cada base se dimensiona y evoluciona por separado |
| Familiaridad del equipo | Alta: es PostgreSQL de siempre, solo replicado |

**Pros:** la propiedad del dato es inequívoca y **verificable**: basta mirar qué credenciales recibe cada contenedor; el esquema de un servicio evoluciona sin coordinar con los demás; hace imposible el acoplamiento por base de datos.
**Contras:** no hay `JOIN` entre servicios, así que un dato que se necesita en dos sitios se duplica por evento (de ahí el *snapshot* de [ADR-11](ADR-11-snapshot-de-contacto-y-canal.md)); más recursos en local; tres esquemas que mantener.

### Opción B: Una base compartida con un esquema por servicio

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | Un contenedor |
| Escalabilidad | Baja: un único punto de contención y de fallo |
| Familiaridad del equipo | Alta |

**Pros:** un contenedor menos; se pueden dar permisos por esquema, así que el aislamiento *lógico* es posible.
**Contras:** el aislamiento depende de una configuración de permisos que nadie verifica y que un cambio descuidado rompe; la tentación de un `JOIN` entre esquemas es permanente; un bloqueo o una caída afecta a los tres servicios. Sigue siendo, en la práctica, *Shared Database*.

### Opción C: Una base y un esquema compartidos

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja de entrada, alta al evolucionar |
| Costo | Un contenedor |
| Escalabilidad | Baja |
| Familiaridad del equipo | Alta |

**Pros:** lo más rápido de montar.
**Contras:** es directamente el antipatrón que el trabajo debe evitar. Anula el desacoplamiento de [ADR-01](ADR-01-kafka-como-broker.md). **Descartada sin más análisis.**

## Análisis de trade-offs

Entre A y B la diferencia real no es técnica, es de **verificabilidad**. Con una base compartida el aislamiento es una promesa que depende de permisos y disciplina; con una base por servicio es un hecho observable: si el contenedor de Payment no tiene las credenciales de Order DB, no puede leerla ni por error ni por un asistente de IA descuidado. Dado que uno de los objetivos es que las reglas arquitectónicas se puedan comprobar de forma ejecutable (HU-006), esa diferencia decide.

El coste aceptado es la duplicación de datos por evento. Se asume de forma explícita y controlada: el contacto del cliente viaja como *snapshot* en los eventos ([ADR-11](ADR-11-snapshot-de-contacto-y-canal.md)) precisamente para que Notification Service nunca tenga que consultar Order DB.

## Consecuencias

**Qué se vuelve más fácil**

- Comprobar la propiedad de los datos: se mira la configuración de Compose y se ve que cada servicio solo conoce su base.
- Evolucionar el esquema de un servicio sin coordinar con los otros dos.
- Razonar sobre fallos: una base caída degrada un servicio, no los tres.

**Qué se vuelve más difícil**

- No hay consultas que cruzan agregados; cualquier vista combinada habría que construirla con un consumidor propio (fuera de alcance: CQRS no se implementa).
- El dato duplicado puede quedar obsoleto; por eso el *snapshot* es del momento del pedido y se trata como un hecho histórico, no como una caché.
- Tres esquemas y tres juegos de credenciales que gestionar en `.env`.

**Qué habrá que revisar**

- Cada dato nuevo que dos servicios necesiten obliga a decidir **por qué evento viaja**, no a añadir una consulta.
- Si en el futuro hiciera falta una vista consolidada, sería una decisión nueva (y un ADR nuevo).

## Acciones

1. [x] Declarar `order-db`, `payment-db` y `notification-db` en Compose, con credenciales separadas en `.env.example` (HU-002).
2. [x] Versionar el esquema inicial de cada base en `infrastructure/postgres/<db>/` (HU-002).
3. [x] Documentar en el README de cada base que solo su servicio la usa, y la única URL que debe recibir.
4. [ ] Verificar de forma ejecutable que en Compose cada servicio recibe únicamente la URL de su propia base (HU-006).

## Táctica relacionada

Matriz de tácticas del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*): **Persistencia local por servicio** — PostgreSQL. Estado: *Implementar*. También la fila *Database per Service* de la tabla de trazabilidad de patrones, que mitiga *Shared Database / Database-as-IPC*.
