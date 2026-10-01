# Plan de sustentación

[← Backlog](README.md) · [Índice de la wiki](../Home.md)

> Reparto del tiempo y de la palabra en la sustentación final, para que **los dos integrantes participen** como exige el enunciado. Esta página fija el minutaje y quién defiende qué (HU-707).
>
> **El guion de la demo y los ensayos son HU-706 (Juan)** y viven en [Runbook de la demo](../04-implementacion/runbook-demo.md). Esta página no los duplica: solo reserva el tiempo en el que encajan.

## Agenda cronometrada

El enunciado fija una ventana de **10 a 20 minutos**. La agenda se plantea sobre **16 minutos**, que deja margen por los dos lados: si el docente acorta a 10 se recortan los bloques marcados como comprimibles, y si concede 20 quedan 4 minutos de holgura para preguntas.

| # | Bloque | Min | Acumulado | Responsable | Comprimible |
|---:|---|---:|---:|---|---|
| 1 | Problema, alcance y por qué EDA | 2 | 2 | Juan | Sí, a 1 |
| 2 | **Arquitectura y diseño** | **5** | 7 | **Sara** | No |
| 3 | Demo en vivo: `PAY-OK` y `PAY-FAIL` | 4 | 11 | Juan | Sí, a 2 |
| 4 | **Decisiones, riesgos y lecciones aprendidas** | **3** | 14 | **Sara** | Sí, a 1 |
| 5 | Calidad verificada y cierre | 2 | 16 | Juan | Sí, a 1 |

**Sara: 8 de 16 minutos** en dos bloques (2 y 4). El bloque 2 es el **segmento de arquitectura y diseño** que reserva HU-707 y es el único marcado como no comprimible: es el eje de la evaluación y el resto de la exposición se apoya en él.

Si la ventana se reduce a 10 minutos, los bloques quedan en **1 + 5 + 2 + 1 + 1 = 10**. La demo muestra un escenario en vivo y el segundo mediante la traza capturada; las lecciones se reducen al hallazgo principal. **El bloque 2 no se toca en ningún escenario.**

## Qué defiende cada integrante

**Sara — arquitectura y diseño (bloque 2), decisiones y lecciones (bloque 4)**

1. El estilo: coreografía por eventos **sin orquestador**, y por qué los tres servicios no se llaman por REST para avanzar el flujo.
2. Una base PostgreSQL por servicio y qué garantiza ese aislamiento.
3. El envelope común y `aggregateId = orderId` como clave de partición: qué orden conserva y qué no.
4. Idempotencia de los consumidores con la tabla de eventos procesados en la misma transacción local.
5. **ADR-08 y sus consecuencias**: la escritura dual aceptada, por qué la notificación consume el resultado del pago en abanico en vez de encadenarse, y por qué la recuperabilidad es **Parcial** y no alta.
6. La traza real: ocho eventos en tres tópicos con un solo identificador de correlación por pedido.
7. Lo aprendido, con los ejemplos de la §4 del informe: la partición bloqueada por el manejador de errores por omisión y los defectos que solo aparecen contra la base real.

**Juan — contexto, demo y cierre (bloques 1, 3 y 5)**

1. Problema, alcance y decisión de estilo.
2. La demo reproducible de los dos escenarios, con la inspección de eventos.
3. La experiencia web y cómo distingue un dato aún no disponible de un rechazo y de un fallo.
4. Atributos de calidad medidos (HU-608), el `tag` y la release.

**Frontera:** ninguno de los dos presenta el bloque del otro. Sara no monta el guion de la demo y Juan no defiende las decisiones arquitectónicas; cada uno responde las preguntas de su bloque, y las de frontera las toma quien tenga la evidencia a mano.

## Confirmación con el docente

| Qué se confirma | Pregunta exacta | Estado | Fecha | Respuesta |
|---|---|---|---|---|
| Duración total asignada al grupo | «¿La sustentación del grupo es de 10 o de 20 minutos?» | **Confirmada por el enunciado** | 2026-09-29 | Ventana de 10 a 20 minutos por grupo |
| Criterio de participación por integrante | «¿Cada integrante debe exponer un tiempo mínimo, o basta con que participe?» | **Confirmado por el enunciado** | 2026-09-29 | Todos los integrantes deben participar; no fija un mínimo individual |
| Si las preguntas cuentan dentro del tiempo | «¿Los minutos de preguntas van dentro de la ventana o aparte?» | **Pendiente** | — | — |

Las dos primeras respuestas salen directamente del enunciado. Sara confirma ante el docente solo si las preguntas cuentan dentro de la ventana y anota aquí la respuesta; hasta entonces ese reparto concreto sigue siendo una propuesta del equipo.

Si la respuesta a la segunda pregunta fija un mínimo por integrante mayor que los 8 minutos de Sara o los 8 de Juan, la tabla de la agenda se recalcula y se anota el cambio en esta misma página.

## Asistencia

**Los dos integrantes asisten a su propia sustentación y a las de los demás grupos.** La ausencia implica **nota 0** para quien falta, con independencia de lo que haya construido en el repositorio. No hay excepción prevista ni forma de compensarlo con el entregable.

| Integrante | Sustentación propia | Sustentaciones de los demás grupos |
|---|---|---|
| Sara | Confirmada | Confirmada |
| Juan | Confirmada | Confirmada |

## Antes de la sustentación

Lo que debe estar cerrado para que la agenda se sostenga:

- **HU-706 (Juan):** el guion del [Runbook de la demo](../04-implementacion/runbook-demo.md) y al menos un ensayo cronometrado. El bloque 3 depende de que la demo arranque sin incidentes.
- **HU-608 (Sara; lo presenta Juan en el bloque 5):** los atributos medidos que cita ese bloque.
- **HU-704 (Juan):** los diagramas exportados. El bloque 2 se apoya en ellos y hoy la vista dinámica y el C2 todavía dibujan la cadena en vez del abanico.
- **HU-705 (Sara):** las lecciones aprendidas del informe, que son el material del bloque 4.
- **El entorno parte limpio.** Un `broker` con historial de ejecuciones anteriores es lo que hizo fallar la primera validación end-to-end; la demo se ejecuta después de un ciclo de parada y arranque.

Referencias: [Plan de sprints](plan-de-sprints.md) · [Épica EP-07](epicas/EP-07-entregables-y-sustentacion.md) · [Estado](estado.md)
