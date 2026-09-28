# CLAUDE.md — FoodFlow EDA

Prototipo académico de una plataforma de pedidos basada en **Event-Driven Architecture**: Angular + API Gateway + 3 servicios Spring Boot (Order, Payment, Notification) + una PostgreSQL por servicio + Apache Kafka. Equipo: Sara y Juan.

Este archivo es corto a propósito. **La fuente de verdad es la wiki del repositorio en `docs/wiki/`.** Léela bajo demanda según la tabla de la sección 2; no la cargues completa.

## 1. Jerarquía de fuentes

**`docs/informe/main.tex` es la fuente de verdad del diseño.** La wiki no decide: **describe, explica y deriva** lo que el informe establece. Si dos fuentes se contradicen, prevalece la de menor número:

1. El informe técnico (`docs/informe/main.tex`) y sus diagramas (`docs/wiki/02-arquitectura/diagramas/`).
2. Las reglas de la sección 3 y las decisiones ADR aprobadas (`docs/wiki/02-arquitectura/decisiones-adr.md`), que desarrollan lo que fija el informe.
3. Contratos versionados en `contracts/` (OpenAPI y JSON Schema).
4. El resto de la wiki (`docs/wiki/`) y el backlog (`docs/wiki/06-backlog/`).

**`main.tex` lo edita únicamente Sara.** Ninguna herramienta ni asistente lo modifica: una corrección al informe **se propone, no se aplica**.

Una contradicción no se resuelve en silencio. Si la wiki se desvía del informe, se corrige la wiki. Si parece que el equivocado es el informe, se registra en [Divergencias informe–wiki](docs/wiki/02-arquitectura/divergencias-informe-wiki.md) y decide Sara.

## 2. Qué leer según la tarea

| Tarea | Lee |
|---|---|
| Cualquier historia (HU) | Su épica en `docs/wiki/06-backlog/epicas/` y `docs/wiki/02-arquitectura/reglas-arquitectonicas.md` |
| Endpoint REST | `docs/wiki/03-contratos/api-rest.md` |
| Productor o consumidor Kafka | `docs/wiki/03-contratos/eventos.md` y `docs/wiki/02-arquitectura/comportamiento-del-flujo.md` |
| Base de datos | `docs/wiki/03-contratos/persistencia.md` |
| Proveedor de notificaciones / mock | `docs/wiki/03-contratos/proveedor-notificaciones.md` |
| Estructura, convenciones, versiones | `docs/wiki/04-implementacion/` |
| Rama, commit, issue, etiqueta, PR, release | `docs/wiki/05-proceso/` |
| Pruebas y calidad | `docs/wiki/02-arquitectura/atributos-de-calidad.md` y `docs/wiki/05-proceso/dor-y-dod.md` |
| Qué está hecho | `docs/wiki/06-backlog/estado.md` |
| Duda de alcance | `docs/wiki/01-producto/vision-y-alcance.md` y `docs/wiki/02-arquitectura/puntos-abiertos.md` |
| El informe y la wiki no coinciden | `docs/wiki/02-arquitectura/divergencias-informe-wiki.md` |

Índice completo: `docs/wiki/Home.md`.

## 3. Reglas arquitectónicas (obligatorias)

1. Angular nunca accede a Kafka ni a PostgreSQL; solo consume APIs REST del API Gateway.
2. Cada servicio es el único propietario de su base PostgreSQL (Order DB, Payment DB, Notification DB).
3. Ninguna base publica ni consume eventos; Kafka nunca escribe en PostgreSQL.
4. Los servicios no se llaman por REST entre sí para coordinar el flujo; se coordinan solo por eventos Kafka (coreografía, sin orquestador).
5. El cliente **solo crea el pedido**. `OrderCreated` dispara el pago: Payment Service lo consume.
6. Order Service y Notification Service reaccionan de forma independiente al resultado del pago.
7. Solo Notification Service llama al proveedor externo de notificaciones.
8. Sin dependencias de código, tablas, repositorios JPA ni clases de dominio compartidos entre servicios.
9. Los consumidores son idempotentes (`eventId` + tabla `processed_events`, en la misma transacción local).
10. Errores transitorios: reintentos controlados; eventos no procesables: DLQ. Un fallo de negocio del proveedor deja la notificación en `FALLIDA` y **no** va a DLQ.
11. Los eventos son hechos inmutables con el envelope común y `aggregateId = orderId` como clave de partición.
12. Un fallo de Notification Service no impide que Order Service registre el resultado del pago.

Detalle y justificación: `docs/wiki/02-arquitectura/reglas-arquitectonicas.md`.

## 4. Decisiones aprobadas

- **ADR-08:** no hay Transactional Outbox. Se acepta que un pedido quede persistido sin que su evento llegue a Kafka; la recuperabilidad es **Parcial**.
- **ADR-09:** idempotencia con `eventId` único y `processed_events` por servicio.
- **ADR-10:** pago determinista: `PAY-OK` produce `PaymentApproved` y `PAY-FAIL` produce `PaymentRejected`.
- **ADR-11:** el pedido guarda un *snapshot* de contacto y canal de notificación (`notificationContact`).
- Protocolos: **REST/JSON sobre HTTPS** en el borde y **eventos JSON sobre Kafka** internamente.
- API REST mínima: `POST /orders`, `GET /orders/{id}`, `GET /orders/{id}/notifications`. `POST /orders` acepta `Idempotency-Key`. Errores en RFC 9457 Problem Details. Contratos en OpenAPI.
- Eventos: `OrderCreated`, `OrderStatusChanged`, `PaymentApproved`, `PaymentRejected`, `NotificationSent`, `NotificationFailed`.

## 5. No implementar

Transactional Outbox, Saga, CQRS, Event Sourcing, Circuit Breaker, tracing distribuido completo, Kubernetes, Backoffice, Analítica, autenticación completa, endpoint de consulta de pagos, base de datos compartida, orquestador central. **No usar `OrderUpdated`** (el evento se llama `OrderStatusChanged`).

Flyway, Testcontainers y CI son **opcionales**: no los conviertas en requisito.

## 6. Flujo de trabajo (resumen)

- Una HU = un issue = una rama = un PR. Rama: `<tipo>/<HU-###>-<slug>` (por ejemplo `feat/HU-101-crear-pedido`). Nunca commits directos a `main`.
- Commit: `<tipo>(<ámbito>): <descripción imperativa en español> [HU-###]`.
- Cada issue lleva 1 etiqueta de tipo, ≥1 de área, 1 de prioridad, 1 de épica y un milestone `Sprint N`.
- El PR usa la plantilla, referencia `Closes #<n>`, lo revisa la otra persona y se integra con squash.
- Detalle: `docs/wiki/05-proceso/`.

## 7. Propiedad del trabajo (obligatoria)

Cada persona del equipo trabaja con su propio asistente. **Cada quien corrige lo suyo.**

1. **Solo tocas las HU de tu responsable.** El responsable de cada HU está en `docs/wiki/06-backlog/estado.md`. No edites código, pruebas ni documentación que pertenezcan a una HU de la otra persona, ni siquiera para arreglar algo que acabas de encontrar.
2. **Solo subes a tus propias ramas.** Nunca hagas *commit*, *push*, *rebase* ni resuelvas conflictos en la rama de un PR ajeno.
3. **Un hallazgo en un PR ajeno se reporta, no se arregla.** Se comenta en el PR con la evidencia y se pide cambios si procede. Quien lo escribió decide y corrige.
4. **No le encargues trabajo a la otra persona dentro de tus propias HU.** Si tu cambio necesita un ajuste en un archivo que comparte con otra HU, hazlo tú en tu rama. No dejes instrucciones del tipo «cuando integres esto, añade tal línea».
5. **Al integrar, quien integra no repara código ajeno.** Si un *merge* deja algo roto que pertenece a la otra persona, se reporta en su PR o en un issue nuevo y se detiene la integración.
6. **La revisión sí cruza.** Revisar, comentar, aprobar o pedir cambios en el PR de la otra persona no solo está permitido: es obligatorio (sección 6). Lo que no cruza es la escritura.

**Por qué.** Dos asistentes editando el mismo archivo producen conflictos que ninguno de los dos ve venir, y borran el rastro de quién decidió qué. La trazabilidad HU → issue → rama → PR → responsable es parte de lo que se evalúa.

Excepción única: Sara puede editar cualquier cosa si lo decide explícitamente, porque es la responsable del repositorio. Un asistente no asume esa excepción por su cuenta.

## 8. La traza del proyecto no nombra herramientas de IA (obligatoria)

El repositorio es el entregable académico. Lo que se evalúa es el trabajo del equipo, así que **ninguna marca de herramienta de IA aparece en la traza del proyecto**.

**No se escribe, nunca:**

- Ningún `Co-Authored-By`, `Generated with`, `Assisted by` ni firma equivalente en un mensaje de commit.
- Ninguna mención de marca en la descripción de un PR, en un comentario de revisión, en un issue o en una release.
- Ningún nombre comercial (`Claude`, `Claude Code`, `Anthropic`, `Copilot`, `ChatGPT`, `Gemini`…) en la wiki, en el informe, en el README o en los comentarios del código.

**Cuando haya que referirse al asistente**, se escribe en genérico: «un asistente de IA», «el asistente». La página [Trabajo con asistentes de IA](docs/wiki/05-proceso/trabajo-con-ia.md) ya está redactada así y es el modelo.

**Excepciones, y solo estas dos:** el archivo `CLAUDE.md` y la carpeta `.claude/` conservan su nombre, porque son configuración que la herramienta localiza por ruta y renombrarlos la rompe. Las referencias a esos dos nombres son rutas de archivo, no menciones.

**Al integrar con squash, el mensaje se escribe a mano.** GitHub propone un cuerpo con los mensajes de los commits del PR, *trailers* incluidos: aceptarlo mete la firma en `main` aunque el PR estuviera limpio. Título escrito a mano y cuerpo vacío.

**Al revisar un PR se comprueba.** Una firma de herramienta en un commit o en la descripción es motivo de pedir cambios.

## 9. Cómo trabajar una HU

1. Confirma que la HU existe como issue y lee su épica y sus criterios de aceptación.
2. Identifica el servicio propietario de los datos y los contratos afectados.
3. Crea la rama, implementa (dominio y aplicación primero; luego adaptadores HTTP, Kafka y base de datos) y agrega pruebas.
4. Ejecuta las pruebas y la verificación de arquitectura (cuando exista `scripts/verify-architecture.sh`).
5. Actualiza contratos, OpenAPI, ADR y `docs/wiki/06-backlog/estado.md` cuando corresponda.
6. Abre el PR y entrega el reporte de `docs/wiki/05-proceso/trabajo-con-ia.md`.
7. No marques la HU como terminada si algún criterio de aceptación no está verificado.

## 10. Ante ambigüedad

- Si la decisión afecta un contrato, un tópico, un estado o el alcance: **detente y pregunta**.
- Si es una decisión local (nombre de una clase privada): elige la opción más simple coherente con EDA y anótala en el reporte.
- Nunca inventes versiones, URLs, credenciales, endpoints ni entidades. Las versiones se fijan en `docs/wiki/04-implementacion/versiones.md`.

## 11. Comandos

| Acción | Comando | Disponible desde |
|---|---|---|
| Verificar estructura del repo | `bash scripts/check-structure.sh` | Bootstrap |
| Levantar / detener entorno | `scripts/up.sh` / `scripts/down.sh` | HU-607 |
| Prueba de humo E2E | `scripts/smoke-test.sh` | HU-607 |
| Validar contratos de eventos | `bash scripts/validate-events.sh` | HU-003 |
| Validar el contrato REST | `bash scripts/validate-openapi.sh` | HU-404 |
| Verificar arquitectura | `scripts/verify-architecture.sh` | HU-006 |
| Construir y probar un servicio | `./mvnw verify` (en `services/<servicio>` o `gateway/api-gateway`; Windows: `mvnw.cmd verify`) | HU-001 |
| Construir y probar el frontend | `npm ci && npm run build && npm test -- --watch=false` (en `frontend/foodflow-web`) | HU-001 |

## 12. Comandos del asistente

- `/hu HU-101`: implementa una historia siguiendo el flujo de este archivo.
- `/verificar`: ejecuta pruebas y checklist arquitectónico sobre los cambios actuales.
- `/pr`: prepara el PR con la plantilla y las etiquetas correctas.

## 13. Estado

El estado vivo del backlog está en `docs/wiki/06-backlog/estado.md`. Actualízalo al cerrar cada HU.
