# Plan de sprints

[← Índice de la wiki](../Home.md)

> Los sprints se organizan por **incrementos demostrables**. Cada sprint termina con software ejecutable y evidencia verificable. Los puntos son relativos (escala sugerida: Fibonacci) y se recalibran con la velocidad real del Sprint 1.

## Sprint 1 — Fundaciones, contratos, estándares y primer recorrido HTTP

**Objetivo:** disponer del repositorio con sus estándares, la infraestructura, los contratos y las decisiones registradas, y de una entrada HTTP mínima.

| # | HU | Título | Pts | Responsable |
|---:|---|---|---:|---|
| 1 | HU-001 | Inicializar la estructura del repositorio | 5 | Juan |
| 2 | HU-002 | Levantar infraestructura local reproducible | 5 | Sara |
| 3 | HU-003 | Definir y versionar contratos de eventos v1 | 3 | Sara |
| 4 | HU-004 | Configurar tópicos Kafka del prototipo | 2 | Juan |
| 5 | HU-005 | Registrar los ADR del proyecto | 5 | Sara |
| 6 | HU-008 | Alinear el documento técnico con las decisiones consolidadas | 3 | Sara |
| 7 | HU-009 | Configurar los estándares del repositorio | 2 | Juan |
| 8 | HU-401 | Enrutar operaciones de pedidos | 3 | Juan |
| 9 | HU-101 | Crear un pedido válido | 5 | Sara |
| 10 | HU-102 | Consultar un pedido por identificador | 2 | Juan |

**Incremento de Sprint:** mediante el gateway puede crearse y consultarse un pedido persistido en Order DB con estado `CREADO`; el repositorio tiene etiquetas, plantillas, protección y ADR; el documento técnico está alineado.

**Total:** 35 puntos (Sara 21, Juan 14).

## Sprint 2 — Pedido orientado a eventos, contrato OpenAPI e interfaz inicial

**Objetivo:** convertir la creación del pedido en el primer productor real de eventos, documentar el API y permitir iniciar el flujo desde Angular.

| # | HU | Título | Pts | Responsable |
|---:|---|---|---:|---|
| 1 | HU-103 | Publicar OrderCreated al crear el pedido | 5 | Sara |
| 2 | HU-107 | Evitar pedidos duplicados con Idempotency-Key | 3 | Sara |
| 3 | HU-404 | Contrato OpenAPI y errores Problem Details | 5 | Sara |
| 4 | HU-604 | Exponer health checks de componentes | 2 | Sara |
| 5 | HU-501 | Crear un pedido desde Angular | 5 | Juan |
| 6 | HU-502 | Consultar y visualizar el estado del pedido | 2 | Juan |
| 7 | HU-403 | Propagar correlationId y manejar CORS | 3 | Juan |

**Incremento de Sprint:** un usuario crea un pedido desde Angular y se comprueba en Kafka el evento `OrderCreated` correlacionado con el registro; el API está documentado y tolera reintentos.

**Total:** 25 puntos (Sara 15, Juan 10).

## Sprint 3 — Flujo de pago coreografiado

**Objetivo:** completar el tramo `OrderCreated -> Payment -> actualización de Order` sin llamadas REST entre dominios.

| # | HU | Título | Pts | Responsable |
|---:|---|---|---:|---|
| 1 | HU-201 | Consumir OrderCreated para iniciar un pago | 3 | Sara |
| 2 | HU-202 | Procesar y persistir el resultado del pago | 5 | Sara |
| 3 | HU-203 | Publicar PaymentApproved | 2 | Sara |
| 4 | HU-204 | Publicar PaymentRejected | 2 | Sara |
| 5 | HU-703 | Patrones, antipatrones y trazabilidad en el documento técnico | 3 | Sara |
| 6 | HU-104 | Actualizar el pedido ante un pago aprobado | 3 | Juan |
| 7 | HU-105 | Actualizar el pedido ante un pago rechazado | 2 | Juan |
| 8 | HU-106 | Publicar OrderStatusChanged al cambiar el estado del pedido | 3 | Juan |
| 9 | HU-702 | Proyección laboral en la matriz de mercado | 2 | Juan |

**Incremento de Sprint:** crear un pedido con `PAY-OK` o `PAY-FAIL` desencadena el pago y el pedido converge a `PAGADO` o `PAGO_RECHAZADO` exclusivamente mediante Kafka; la proyección laboral y la matriz de trazabilidad están redactadas.

**Total:** 25 puntos (Sara 15, Juan 10).

## Sprint 4 — Notificaciones y visibilidad para el cliente

**Objetivo:** cerrar el flujo funcional desde el resultado del pago hasta la notificación y exponerlo en la interfaz.

| # | HU | Título | Pts | Responsable |
|---:|---|---|---:|---|
| 1 | HU-301 | Crear una notificación ante el resultado del pago | 5 | Sara |
| 2 | HU-302 | Enviar una notificación mediante el proveedor externo | 5 | Sara |
| 3 | HU-303 | Registrar y publicar una notificación enviada | 3 | Sara |
| 4 | HU-304 | Registrar y publicar una notificación fallida | 5 | Sara |
| 5 | HU-306 | Mock del proveedor de notificaciones | 3 | Juan |
| 6 | HU-305 | Consultar las notificaciones de un pedido | 3 | Juan |
| 7 | HU-402 | Enrutar la consulta de notificaciones | 3 | Juan |
| 8 | HU-504 | Visualizar el estado de la notificación | 3 | Juan |

**Incremento de Sprint:** desde Angular se crea un pedido y se observan su estado y sus notificaciones (`ENVIADA` o `FALLIDA`) usando el mock del proveedor.

**Total:** 30 puntos (Sara 18, Juan 12).

## Sprint 5 — Resiliencia, trazabilidad y guardas

**Objetivo:** demostrar que la arquitectura tolera duplicados y fallos sin corromper el estado, y hacer cumplir las reglas de forma automática.

| # | HU | Título | Pts | Responsable |
|---:|---|---|---:|---|
| 1 | HU-601 | Hacer idempotentes los consumidores Kafka | 5 | Sara |
| 2 | HU-602 | Aplicar reintentos y DLQ a eventos fallidos | 5 | Sara |
| 3 | HU-605 | Automatizar pruebas de integración del flujo Kafka | 5 | Sara |
| 4 | HU-603 | Implementar logs estructurados y correlacionados | 3 | Sara |
| 5 | HU-505 | Visualizar el flujo integral de un pedido | 5 | Juan |
| 6 | HU-006 | Guardas de arquitectura ejecutables | 5 | Juan |
| 7 | HU-607 | Scripts de arranque, parada y prueba de humo | 2 | Juan |
| 8 | HU-506 | Unificar la interfaz web con un diseño base | 3 | Juan |

**Incremento de Sprint:** eventos duplicados o defectuosos inyectados de forma controlada demuestran idempotencia, reintentos y DLQ; hay vista integral con un diseño base común, logs estructurados, guardas de arquitectura y scripts de arranque.

**Total:** 33 puntos (Sara 18, Juan 15). HU-506 se añadió durante el sprint y deja este sprint en 55 % / 45 %.

## Sprint 6 — Calidad verificada, entregables y sustentación

**Objetivo:** cerrar el prototipo con una ejecución reproducible, atributos de calidad verificados y los entregables académicos completos.

| # | HU | Título | Pts | Responsable |
|---:|---|---|---:|---|
| 1 | HU-606 | Validar el flujo end-to-end contenerizado | 8 | Sara |
| 2 | HU-705 | Lecciones aprendidas | 5 | Sara |
| 3 | HU-707 | Segmento de arquitectura en la sustentación | 2 | Sara |
| 4 | HU-701 | README, tag y release | 3 | Sara |
| 5 | HU-704 | Diagramas exportados desde sus fuentes | 2 | Juan |
| 6 | HU-706 | Presentación, demo y ensayo | 5 | Juan |
| 7 | HU-608 | Verificar los atributos de calidad | 5 | Juan |

**Incremento de Sprint:** repositorio ejecutable desde cero con demostración reproducible de las dos ramas del flujo, atributos de calidad medidos, tag y release `v1.0.0`, y sustentación ensayada.

**Trabajo de cierre incluido en HU-606**

- Corregir defectos encontrados en integración.
- Validar variables de entorno y `.env.example`.
- Actualizar OpenAPI y contratos de eventos implementados.
- Actualizar los diagramas si la implementación cambió detalles sin alterar las decisiones arquitectónicas.
- Ejecutar pruebas de regresión.
- Verificar que las tres bases permanecen aisladas y que no hay llamadas directas entre servicios para coordinar el flujo.
- Verificar eventos en `orders.events`, `payments.events` y `notifications.events`.

**Total:** 30 puntos (Sara 18, Juan 12).

## Resumen de carga (60 % / 40 %)

| Sprint | Total | Sara | Juan | % Sara |
|---:|---:|---:|---:|---:|
| 1 | 35 | 21 | 14 | 60 % |
| 2 | 25 | 15 | 10 | 60 % |
| 3 | 25 | 15 | 10 | 60 % |
| 4 | 30 | 18 | 12 | 60 % |
| 5 | 33 | 18 | 15 | 55 % |
| 6 | 30 | 18 | 12 | 60 % |
| **Total** | **178** | **105** | **73** | **59 %** |

Cada sprint respeta la proporción 60/40, no solo el total.

## Traspaso para la sustentación

Juan presenta el repositorio completo. Para que pueda defender el sistema:

- Al cierre de cada sprint hay una sesión de traspaso (Sara a Juan) sobre lo construido.
- Cada integrante revisa los PR del otro antes de fusionarlos.
- HU-707 reserva un segmento propio para Sara, de modo que todos los integrantes participen.

## Historias opcionales (P2)

- HU-205 Consultar el pago de un pedido.
- HU-503 Visualizar el resultado del pago.
- HU-007 CI automático (opcional).
- HU-010 Versionado de esquemas con Flyway (opcional).
- HU-011 Pruebas de integración con Testcontainers (opcional).

Se toman solo si sobra capacidad y **no** cuentan para el 60/40 ni para la finalización del prototipo.

## Ruta crítica del Sprint 1

`HU-001` (Juan) es la ruta crítica: crea el esqueleto compilable de los proyectos sobre el que construyen las demás.

```text
HU-001 (Juan) ──► HU-101 (Sara) ──► HU-102 y HU-401 (Juan)
Mientras tanto, Sara: HU-002, HU-003, HU-005 y HU-008 (no dependen del esqueleto)
HU-004 (Juan) depende de HU-002 (Kafka en Compose)
HU-009 (Juan): el bootstrap del repositorio ya cubre casi todo; se verifica y se cierra
```

Detalle del bootstrap: [Bootstrap del repositorio](../05-proceso/bootstrap-repositorio.md).
