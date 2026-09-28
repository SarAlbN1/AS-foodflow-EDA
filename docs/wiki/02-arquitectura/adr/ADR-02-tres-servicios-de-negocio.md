# ADR-02: Tres servicios de negocio

**Estado:** Aprobado
**Fecha:** 2026-09-27 (fecha de registro del ADR; la decisión se tomó en la fase de diseño)
**Decisores:** Sara, Juan

## Contexto

Hay que decidir cuántos servicios tiene el prototipo y con qué límites. Un dominio de comida a domicilio admite muchos: restaurante, catálogo, inventario, reparto, autenticación, facturación. Cada servicio añadido cuesta un proyecto, una base, un contenedor, un conjunto de pruebas y una superficie de contrato que dos personas deben mantener.

Al mismo tiempo, con un solo servicio no hay nada que desacoplar: sin al menos dos consumidores independientes del resultado de un paso, la arquitectura orientada a eventos no se puede demostrar.

## Decisión

El prototipo se limita a **tres servicios de negocio**, alineados uno a uno con los conceptos del flujo mínimo:

| Servicio | Agregado que posee | Responsabilidad |
|---|---|---|
| `order-service` | `Order` | Valida y persiste el pedido; registra el resultado del pago |
| `payment-service` | `Payment` | Procesa el pago al consumir `OrderCreated` |
| `notification-service` | `Notification` | Notifica el resultado llamando al proveedor externo |

No se crean servicios de restaurante, inventario, reparto ni autenticación, ni entidades de dominio fuera de `Order`, `Payment` y `Notification`.

## Opciones consideradas

### Opción A: Tres servicios, uno por agregado del flujo (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Media. Tres proyectos, tres bases, tres conjuntos de pruebas |
| Costo | Tres contenedores de aplicación más tres de base de datos |
| Escalabilidad | Suficiente: cada servicio escala por separado y el resultado del pago tiene dos consumidores independientes |
| Familiaridad del equipo | Media |

**Pros:** los límites coinciden con los agregados, así que la propiedad de los datos es inequívoca; `payments.events` tiene **dos** consumidores independientes (Order y Notification), que es lo que demuestra la coreografía; el alcance cabe en el semestre.
**Contras:** tres proyectos que mantener y tres esquemas que versionar; el flujo completo obliga a mirar tres conjuntos de logs.

### Opción B: Un monolito modular

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja |
| Costo | Un contenedor y una base |
| Escalabilidad | Baja: se escala todo o nada |
| Familiaridad del equipo | Alta |

**Pros:** mucho más rápido de construir y depurar; transacciones locales atómicas, así que el problema de la escritura dual de [ADR-08](ADR-08-sin-transactional-outbox.md) ni aparece.
**Contras:** **no demuestra el objetivo**. Los eventos dentro de un proceso no prueban desacoplamiento entre servicios, ni propiedad exclusiva de datos, ni idempotencia de consumidores distribuidos.

### Opción C: Descomposición fina (cinco o más servicios)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Alta |
| Costo | Alto: más contenedores de los que caben cómodamente en una máquina de desarrollo |
| Escalabilidad | Alta en teoría |
| Familiaridad del equipo | Baja |

**Pros:** más realista respecto a una plataforma de verdad; más oportunidades de mostrar patrones.
**Contras:** riesgo alto de no terminar; obliga a coordinación entre pasos (Saga), que está explícitamente fuera del alcance; el esfuerzo se iría en infraestructura en lugar de en las propiedades que hay que demostrar. Incumple YAGNI.

## Análisis de trade-offs

El criterio no es "cuántos servicios tendría una plataforma real", sino **cuál es el número mínimo que permite demostrar EDA y verificarlo**. Ese mínimo es tres: hace falta un productor (Order), un consumidor que produzca un resultado (Payment) y **dos** consumidores independientes de ese resultado (Order y Notification), porque la reacción independiente al mismo evento es justo lo que distingue la coreografía de una cadena de llamadas.

La Opción B es más barata pero no demuestra nada distribuido. La Opción C demuestra más, pero a un riesgo de entrega que no se justifica, y arrastraría patrones (Saga) que el alcance descarta. Tres servicios es el punto en el que el beneficio didáctico deja de crecer más rápido que el coste.

## Consecuencias

**Qué se vuelve más fácil**

- Asignar la propiedad de cada dato sin ambigüedad: un agregado, un servicio, una base ([ADR-03](ADR-03-una-base-postgresql-por-servicio.md)).
- Demostrar la reacción independiente al resultado del pago ([regla 6](../reglas-arquitectonicas.md)) y la tolerancia al fallo de Notification (regla 12).
- Repartir el trabajo entre dos personas por servicio.

**Qué se vuelve más difícil**

- El flujo de punta a punta atraviesa tres procesos: hace falta `correlationId` y logs estructurados para seguirlo.
- Tres despliegues, tres esquemas y tres conjuntos de pruebas que mantener coherentes.

**Qué habrá que revisar**

- Cualquier petición de ampliar el dominio (restaurante, inventario, reparto, autenticación) exige un ADR nuevo, no un cambio silencioso.
- Si el prototipo evolucionara, habría que revisar si `Notification` sigue siendo un servicio o pasa a ser una capacidad transversal.

## Acciones

1. [x] Fijar las tres carpetas de servicio en la estructura del repositorio.
2. [x] Declarar en `CLAUDE.md` que no se crean entidades de dominio ni servicios adicionales.
3. [ ] Crear los tres proyectos compilables (HU-001).
4. [ ] Verificar de forma ejecutable que ningún `pom.xml` depende de otro servicio (HU-006).

## Táctica relacionada

Matriz de tácticas del informe técnico (`docs/informe/main.tex`, sección *Matriz de Tácticas vs Estilo y Stack*): **Separación de responsabilidades** — Spring Boot + tres servicios. Estado: *Implementar*.
