# ÉPICA EP-07 — Entregables académicos y sustentación

[← Backlog](../README.md) · [Plan de sprints](../plan-de-sprints.md) · [Índice de la wiki](../../Home.md)

**Objetivo:** cumplir los entregables exigidos: documento técnico, repositorio público con tag y release, README y sustentación.  
**Prioridad de la épica:** P0.

## HU-701 — README, tag y release

**Orden:** 1  
**Prioridad:** P0  
**Sprint:** 6 · **Puntos:** 3 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como evaluador, quiero un repositorio público con README completo y una versión etiquetada, para revisar y ejecutar exactamente lo entregado.

**Criterios de aceptación**

1. El repositorio es público y el README describe el sistema, las tecnologías usadas y los pasos de despliegue, siguiendo la página [Estándares de documentación](../../05-proceso/documentacion.md).
2. Existe el tag `v1.0.0` y su release con las notas descritas en la página [Pull requests, protección y releases](../../05-proceso/pull-requests-y-releases.md).
3. Se recomienda un pre-release `v0.<N>.0` al cierre de cada sprint.
4. El README declara las limitaciones conocidas, incluida la ausencia de Outbox (ADR-08).

## HU-702 — Proyección laboral en la matriz de mercado

**Orden:** 2  
**Prioridad:** P0  
**Sprint:** 3 · **Puntos:** 2 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo, quiero que la matriz de mercado laboral incluya la proyección de cada tecnología, para cumplir lo que pide el enunciado.

**Criterios de aceptación**

1. Cada tecnología (Java/Spring Boot, Angular/TypeScript, PostgreSQL, Kafka/EDA) tiene una tendencia (crece, estable o decrece) con fuente citada.
2. Se distinguen claramente los **datos observados** de las **inferencias** realizadas a partir de ellos.
3. No se incluyen cifras sin fuente.

## HU-703 — Patrones, antipatrones y trazabilidad en el documento técnico

**Orden:** 3  
**Prioridad:** P0  
**Sprint:** 3 · **Puntos:** 3 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo, quiero que el documento muestre qué patrones se investigaron, cuáles se diseñaron y cuáles se implementaron, para distinguir lo estudiado de lo construido.

**Criterios de aceptación**

1. Existe una matriz **Investigado, Diseñado, Implementado** consistente con la página [Trazabilidad](../../02-arquitectura/trazabilidad.md).
2. La tabla de patrones incluye los que aplica el diseño: Database per Service, Publish-Subscribe, Repository, API Gateway, Idempotent Consumer, Retry/DLQ, Strategy y Adapter.
3. Existe una tabla de antipatrones evitados con la táctica que los previene.
4. Los elementos "no implementar" figuran como evolución futura con su motivo.

## HU-704 — Diagramas exportados desde sus fuentes

**Orden:** 4  
**Prioridad:** P0  
**Sprint:** 6 · **Puntos:** 2 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como evaluador, quiero ver los diagramas finales dentro del documento, para comprender la arquitectura sin abrir herramientas externas.

**Criterios de aceptación**

1. Los marcadores de figura del `.tex` se reemplazan por imágenes exportadas.
2. C1, C2, C3 y System Landscape salen de un único `workspace.dsl`; el dinámico de `.mmd`, el despliegue de `.puml` y el modelo de datos de `.dbml`, todos versionados en el repositorio.
3. Los diagramas usan `OrderStatusChanged`, `PAY-OK`/`PAY-FAIL` y coinciden con esta versión del contexto.
4. Los elementos fuera de alcance usan borde punteado.

## HU-705 — Lecciones aprendidas

**Orden:** 5  
**Prioridad:** P0  
**Sprint:** 6 · **Puntos:** 5 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo, quiero documentar lo aprendido durante la implementación, para cerrar el documento técnico con evidencia real.

**Criterios de aceptación**

1. Incluye dificultades técnicas reales (Spring Boot con Kafka, consumer groups, particiones, serialización).
2. Lista las decisiones que cambiaron respecto al diseño inicial con su ADR.
3. Analiza retos de consistencia eventual observados y el efecto de ADR-08.
4. Recoge aprendizajes sobre contenedores y recomendaciones para replicar el stack.

## HU-706 — Presentación, demo y ensayo

**Orden:** 6  
**Prioridad:** P0  
**Sprint:** 6 · **Puntos:** 5 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como presentador, quiero una presentación y una demo ensayadas, para exponer el sistema completo con claridad dentro del tiempo asignado.

**Criterios de aceptación**

1. La presentación usa diagramas e infografías, colores adecuados y una estructura fluida.
2. `docs/wiki/04-implementacion/runbook-demo.md` describe un guion reproducible con los escenarios `PAY-OK` y `PAY-FAIL`, la inspección de eventos y de la DLQ.
3. Hay al menos un ensayo cronometrado dentro de 10 a 20 minutos.
4. El presentador ha recibido el traspaso de cada sprint (página [Plan de sprints](../plan-de-sprints.md)).

## HU-707 — Segmento de arquitectura en la sustentación

**Orden:** 7  
**Prioridad:** P0  
**Sprint:** 6 · **Puntos:** 2 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como integrante del equipo, quiero un segmento propio en la sustentación, para que todos los integrantes participen como exige el enunciado.

**Criterios de aceptación**

1. El segmento de arquitectura y diseño tiene un tiempo asignado dentro de los 10 a 20 minutos.
2. Se confirma con el docente el criterio de participación por integrante.
3. Ambos integrantes asisten a su sustentación y a las de los demás grupos (su ausencia implica nota 0).
