# ÉPICA EP-00 — Base técnica, contratos, decisiones y estándares del repositorio

[← Backlog](../README.md) · [Plan de sprints](../plan-de-sprints.md) · [Índice de la wiki](../../Home.md)

**Objetivo:** disponer de un entorno reproducible, contratos estables, decisiones registradas y un repositorio ordenado sobre los que puedan desarrollarse los servicios sin acoplamiento accidental.  
**Prioridad de la épica:** P0.

## HU-001 — Inicializar la estructura del repositorio

**Orden:** 1  
**Prioridad:** P0  
**Sprint:** 1 · **Puntos:** 5 · **Responsable:** Juan  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo de desarrollo, quiero disponer de la estructura base del monorepo con frontend, gateway, servicios, contratos, infraestructura, mocks, documentación y scripts, para que cada componente tenga una responsabilidad y ubicación inequívocas.

**Criterios de aceptación**

1. **Dado** un clon limpio del repositorio, **cuando** se inspecciona su raíz, **entonces** existen las carpetas definidas en la página [Estructura del repositorio](../../04-implementacion/estructura-del-repositorio.md).
2. **Dado** cada proyecto Angular/Spring Boot, **cuando** se ejecuta su comando de compilación base, **entonces** compila sin errores aunque todavía no contenga funcionalidad de negocio.
3. **Dado** el repositorio, **cuando** un desarrollador consulta `README.md`, **entonces** puede identificar cómo ejecutar, detener y probar localmente la solución.
4. No se incluyen credenciales, secretos ni archivos `.env` reales versionados.

> **Nota de bootstrap:** el criterio 1 (carpetas) y el 4 (sin secretos) ya los cumple el [bootstrap del repositorio](../../05-proceso/bootstrap-repositorio.md). Quedan el criterio 2 (proyectos Angular y Spring Boot que compilan) y el 3 (README con cómo ejecutar, detener y probar).

## HU-002 — Levantar infraestructura local reproducible

**Orden:** 2  
**Prioridad:** P0  
**Sprint:** 1 · **Puntos:** 5 · **Responsable:** Sara  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como desarrollador, quiero levantar Kafka y las tres persistencias PostgreSQL mediante Compose, para ejecutar FoodFlow localmente con el mismo esquema de aislamiento definido por la arquitectura.

**Criterios de aceptación**

1. **Dado** Docker/Podman disponible, **cuando** se ejecuta el comando de arranque documentado, **entonces** se inicia Kafka y la persistencia de Order, Payment y Notification.
2. Cada servicio dispone de credenciales y base de datos propias.
3. Ningún servicio recibe configuración de acceso a una base de datos ajena.
4. Los contenedores tienen health checks o una comprobación equivalente documentada.
5. El entorno puede detenerse y recrearse sin pasos manuales no documentados.
6. El esquema inicial de cada base (tablas del servicio y `processed_events`) se crea con scripts SQL versionados en `infrastructure/postgres/<db>/`. Flyway es opcional (página [Visión y alcance](../../01-producto/vision-y-alcance.md)).

## HU-003 — Definir y versionar contratos de eventos v1

**Orden:** 3  
**Prioridad:** P0  
**Sprint:** 1 · **Puntos:** 3 · **Responsable:** Sara  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como desarrollador de un servicio consumidor, quiero disponer de contratos versionados para los eventos de FoodFlow, para integrar servicios sin depender de clases internas de otros componentes.

**Criterios de aceptación**

1. Existen esquemas v1 para `OrderCreated`, `OrderStatusChanged`, `PaymentApproved`, `PaymentRejected`, `NotificationSent` y `NotificationFailed`.
2. Todos incluyen `eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId`, `aggregateId` y `payload`.
3. Los contratos se almacenan bajo `contracts/events/v1/`.
4. Ningún servicio necesita importar un módulo Java compartido con entidades de dominio de otro servicio.
5. Existe al menos una prueba o validación automática que detecta un evento que no cumple su esquema.
6. Los payloads siguen el catálogo de la página [Eventos Kafka](../../03-contratos/eventos.md), incluido el `notificationContact`.

## HU-004 — Configurar tópicos Kafka del prototipo

**Orden:** 4  
**Prioridad:** P0  
**Sprint:** 1 · **Puntos:** 2 · **Responsable:** Juan  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo de desarrollo, quiero que los tópicos requeridos por FoodFlow estén disponibles al levantar el entorno, para que productores y consumidores puedan integrarse de forma reproducible.

**Criterios de aceptación**

1. Se encuentran disponibles `orders.events`, `payments.events` y `notifications.events`.
2. La creación/configuración está automatizada o claramente declarada en infraestructura.
3. Los nombres de tópicos se obtienen por configuración y no están replicados como literales arbitrarios por toda la base de código.
4. La documentación indica qué servicio produce y consume en cada tópico.
5. Existen los tópicos DLQ `orders.events.dlq`, `payments.events.dlq` y `notifications.events.dlq`, y los tópicos principales usan 3 particiones y retención de 7 días (página [Eventos Kafka](../../03-contratos/eventos.md)).

## HU-005 — Registrar los ADR del proyecto

**Orden:** 5  
**Prioridad:** P0  
**Sprint:** 1 · **Puntos:** 5 · **Responsable:** Sara  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo de desarrollo, quiero tener cada decisión arquitectónica registrada como ADR en el repositorio, para que las decisiones tomadas guíen la implementación y no se contradigan.

**Criterios de aceptación**

1. Existen `docs/wiki/02-arquitectura/adr/ADR-01` a `ADR-11`, y `ADR-12` como propuesto, con estado, contexto, decisión, alternativas y consecuencias.
2. ADR-08 documenta el riesgo aceptado de la escritura dual y la recuperabilidad **Parcial**; ADR-09, ADR-10 y ADR-11 reflejan la página [Decisiones (ADR)](../../02-arquitectura/decisiones-adr.md).
3. Existe un índice de ADR con su estado.
4. Cada ADR enlaza la táctica correspondiente de la matriz del documento técnico.
5. Un cambio de decisión crea un ADR nuevo que reemplaza al anterior (estado `Superseded`).

## HU-006 — Guardas de arquitectura ejecutables

**Orden:** 6  
**Prioridad:** P1  
**Sprint:** 5 · **Puntos:** 5 · **Responsable:** Juan  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo de desarrollo, quiero que las reglas arquitectónicas se verifiquen automáticamente, para detectar violaciones (propias o de un asistente de IA) antes de fusionar un cambio.

**Criterios de aceptación**

1. `scripts/verify-architecture.sh` comprueba: ningún `pom.xml` depende de otro servicio; en Compose cada servicio recibe solo la URL de su propia base; no hay literales de tópicos fuera de la configuración.
2. Pruebas ArchUnit (o equivalente) verifican en cada servicio: `@KafkaListener` y `KafkaTemplate` solo en `infrastructure.messaging`; el dominio no depende de `api` ni `infrastructure`; solo `infrastructure.provider` de Notification usa un cliente HTTP saliente.
3. Una violación deliberada hace fallar la verificación (evidencia documentada).
4. Se ejecutan localmente con un comando documentado, sin depender de CI.

## HU-007 — CI automático (opcional)

**Orden:** 7  
**Prioridad:** P2  
**Sprint:** — (opcional, fuera del plan) · **Puntos:** — · **Responsable:** —  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo de desarrollo, quiero compilación y pruebas automáticas en cada PR, para detectar rupturas sin ejecutar todo manualmente.

**Criterios de aceptación**

1. Un flujo de trabajo compila y prueba los servicios, el gateway y el frontend.
2. Ejecuta `verify-architecture.sh` si existe.
3. No usa secretos versionados.
4. Su ausencia no impide dar por terminado el prototipo (página [Visión y alcance](../../01-producto/vision-y-alcance.md)).

## HU-008 — Alinear el documento técnico con las decisiones consolidadas

**Orden:** 8  
**Prioridad:** P0  
**Sprint:** 1 · **Puntos:** 3 · **Responsable:** Sara  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo, quiero que `main.tex` refleje las decisiones consolidadas, para que el documento, los diagramas y el código no se contradigan.

**Criterios de aceptación**

1. El Cliente solo crea el pedido; el pago se inicia al consumir `OrderCreated` en toda la narrativa, incluidas las secciones de Angular y C1.
2. La portada y la página [Visión y alcance](../../01-producto/vision-y-alcance.md).2 del documento técnico declaran REST/JSON sobre HTTPS (borde) y eventos JSON sobre Kafka (interno); REST y API Gateway se investigan con el formato de las demás tecnologías.
3. `OrderStatusChanged` reemplaza a `OrderUpdated` en texto, C2, C3 y diagramas.
4. La matriz de tácticas incorpora ADR-08 a ADR-11 y la recuperabilidad figura como **Parcial**.
5. La matriz de calidad incluye la columna de criterio verificable (página [Atributos de calidad verificables](../../02-arquitectura/atributos-de-calidad.md)).
6. En el System Landscape, Backoffice y Analítica están marcados como fuera de alcance y conectados con relaciones punteadas (o retirados); la afirmación de "único actor" se limita al prototipo implementado.
7. Las referencias definidas y no citadas se citan o se eliminan.

## HU-009 — Configurar los estándares del repositorio

**Orden:** 9  
**Prioridad:** P0  
**Sprint:** 1 · **Puntos:** 2 · **Responsable:** Juan  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo de desarrollo, quiero un repositorio con ramas, etiquetas, plantillas y protecciones definidas, para trabajar de forma ordenada y para que un asistente de IA aplique las mismas reglas que una persona.

**Criterios de aceptación**

1. Las etiquetas de la página [Issues, etiquetas y milestones](../../05-proceso/issues-etiquetas-y-milestones.md) existen (creadas con `scripts/setup-labels.sh`) y las existentes no se modifican.
2. Existen los milestones `Sprint 1` a `Sprint 6` y un Project con los campos `Points`, `Sprint` y `Responsable`.
3. Existen `.github/pull_request_template.md` y las plantillas de issue `story`, `bug` y `adr`.
4. `main` está protegida según la página [Pull requests, protección y releases](../../05-proceso/pull-requests-y-releases.md).
5. Existen `.gitignore`, `.editorconfig`, `.env.example` y `CONTRIBUTING.md`.
6. Los issues del Sprint 1 están creados con etiquetas, milestone y responsable; los demás se crean al iniciar su sprint.

> **Nota de bootstrap:** el bootstrap cumple los criterios 1 a 5 y el 6 (issues del Sprint 1). Queda verificar el checklist y completar el Project si falló; la HU se cierra al verificarlo.

## HU-010 — Versionado de esquemas con Flyway (opcional)

**Orden:** 10  
**Prioridad:** P2  
**Sprint:** — (opcional, fuera del plan) · **Puntos:** — · **Responsable:** —  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo de desarrollo, quiero versionar los esquemas de base de datos con migraciones, para evolucionar las tablas de forma controlada.

**Criterios de aceptación**

1. Cada servicio migra su propio esquema y ninguna migración toca bases ajenas.
2. Los scripts de `infrastructure/postgres/` se convierten en migraciones sin cambiar el modelo.
3. Su ausencia no impide dar por terminado el prototipo.
