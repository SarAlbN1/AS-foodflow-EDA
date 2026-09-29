# Estado del backlog

[← Backlog](README.md) · [Índice de la wiki](../Home.md)

> Estado vivo. Se actualiza al cerrar cada HU (en el mismo PR). Estados: `Pendiente`, `En curso`, `En revisión`, `Terminada`, `Bloqueada`, `Opcional`.

| HU | Título | Sprint | Resp. | Pts | Estado | PR | Notas |
|---|---|---:|---|---:|---|---|---|
| [HU-001](epicas/EP-00-base-tecnica-y-estandares.md) | Inicializar la estructura del repositorio | 1 | Juan | 5 | Terminada | #16 |  |
| [HU-002](epicas/EP-00-base-tecnica-y-estandares.md) | Levantar infraestructura local reproducible | 1 | Sara | 5 | Terminada | #11 | Corrección del montaje de volúmenes en revisión (#64) |
| [HU-003](epicas/EP-00-base-tecnica-y-estandares.md) | Definir y versionar contratos de eventos v1 | 1 | Sara | 3 | Terminada | #12 |  |
| [HU-004](epicas/EP-00-base-tecnica-y-estandares.md) | Configurar tópicos Kafka del prototipo | 1 | Juan | 2 | Terminada | #15 |  |
| [HU-005](epicas/EP-00-base-tecnica-y-estandares.md) | Registrar los ADR del proyecto | 1 | Sara | 5 | Terminada | #13 |  |
| [HU-008](epicas/EP-00-base-tecnica-y-estandares.md) | Alinear el documento técnico con las decisiones consolidadas | 1 | Sara | 3 | En revisión | #66 |  |
| [HU-009](epicas/EP-00-base-tecnica-y-estandares.md) | Configurar los estándares del repositorio | 1 | Juan | 2 | Terminada | — | Integrada en el commit inicial del repositorio, sin PR |
| [HU-101](epicas/EP-01-pedidos.md) | Crear un pedido válido | 1 | Sara | 5 | En revisión | #65 |  |
| [HU-102](epicas/EP-01-pedidos.md) | Consultar un pedido por identificador | 1 | Juan | 2 | Terminada | #69 | Verificada con Order DB real por Sara (25/25) |
| [HU-401](epicas/EP-04-gateway-y-contratos.md) | Enrutar operaciones de pedidos | 1 | Juan | 3 | Terminada | #70 | Verificada contra un Order Service simulado; prueba E2E con el servicio real pendiente de Docker |
| [HU-103](epicas/EP-01-pedidos.md) | Publicar OrderCreated al crear el pedido | 2 | Sara | 5 | En revisión | #81 | Publica OrderCreated tras el commit; sin Outbox (ADR-08) |
| [HU-107](epicas/EP-01-pedidos.md) | Evitar pedidos duplicados con Idempotency-Key | 2 | Sara | 3 | En revisión | #82 | Cabecera obligatoria; corrige ADR-12 y el CA4 de la HU |
| [HU-403](epicas/EP-04-gateway-y-contratos.md) | Propagar correlationId y manejar CORS | 2 | Juan | 3 | Terminada | #72 | Verificada contra un Order Service simulado; prueba E2E pendiente de Docker |
| [HU-404](epicas/EP-04-gateway-y-contratos.md) | Contrato OpenAPI y errores Problem Details | 2 | Sara | 5 | En revisión | #67 |  |
| [HU-501](epicas/EP-05-frontend.md) | Crear un pedido desde Angular | 2 | Juan | 5 | Terminada | #74 | Criterios verificados con pruebas de componente; prueba E2E con el backend real pendiente de Docker |
| [HU-502](epicas/EP-05-frontend.md) | Consultar y visualizar el estado del pedido | 2 | Juan | 2 | Terminada | #75 | Criterios verificados con pruebas de componente; prueba E2E pendiente de Docker |
| [HU-604](epicas/EP-06-resiliencia-y-calidad.md) | Exponer health checks de componentes | 2 | Sara | 2 | En revisión | #68 | Health checks de los tres servicios; el del gateway lo añade HU-401 |
| [HU-104](epicas/EP-01-pedidos.md) | Actualizar el pedido ante un pago aprobado | 3 | Juan | 3 | Pendiente | | |
| [HU-105](epicas/EP-01-pedidos.md) | Actualizar el pedido ante un pago rechazado | 3 | Juan | 2 | Pendiente | | |
| [HU-106](epicas/EP-01-pedidos.md) | Publicar OrderStatusChanged al cambiar el estado del pedido | 3 | Juan | 3 | Pendiente | | |
| [HU-201](epicas/EP-02-pagos.md) | Consumir OrderCreated para iniciar un pago | 3 | Sara | 3 | En revisión | #71 | Consume OrderCreated; el pago se crea en HU-202 |
| [HU-202](epicas/EP-02-pagos.md) | Procesar y persistir el resultado del pago | 3 | Sara | 5 | En revisión | #73 | Resuelve y persiste el pago; la publicación es HU-203 y HU-204 |
| [HU-203](epicas/EP-02-pagos.md) | Publicar PaymentApproved | 3 | Sara | 2 | En revisión | | Publica PaymentApproved tras el commit; el rechazo es HU-204 |
| [HU-204](epicas/EP-02-pagos.md) | Publicar PaymentRejected | 3 | Sara | 2 | En revisión | | Publica PaymentRejected; un pago produce un solo evento |
| [HU-702](epicas/EP-07-entregables-y-sustentacion.md) | Proyección laboral en la matriz de mercado | 3 | Juan | 2 | En revisión | [#76](https://github.com/SarAlbN1/AS-foodflow-EDA/pull/76) | Propuesta a `main.tex`: la aplica Sara |
| [HU-703](epicas/EP-07-entregables-y-sustentacion.md) | Patrones, antipatrones y trazabilidad en el documento técnico | 3 | Sara | 3 | En revisión | | Matriz Investigado/Diseñado/Implementado, antipatrones y riesgos aceptados |
| [HU-301](epicas/EP-03-notificaciones.md) | Crear una notificación ante el resultado del pago | 4 | Sara | 5 | Pendiente | | |
| [HU-302](epicas/EP-03-notificaciones.md) | Enviar una notificación mediante el proveedor externo | 4 | Sara | 5 | Pendiente | | |
| [HU-303](epicas/EP-03-notificaciones.md) | Registrar y publicar una notificación enviada | 4 | Sara | 3 | Pendiente | | |
| [HU-304](epicas/EP-03-notificaciones.md) | Registrar y publicar una notificación fallida | 4 | Sara | 5 | Pendiente | | |
| [HU-305](epicas/EP-03-notificaciones.md) | Consultar las notificaciones de un pedido | 4 | Juan | 3 | Pendiente | | |
| [HU-306](epicas/EP-03-notificaciones.md) | Mock del proveedor de notificaciones | 4 | Juan | 3 | Terminada | #62 |  |
| [HU-402](epicas/EP-04-gateway-y-contratos.md) | Enrutar la consulta de notificaciones | 4 | Juan | 3 | Terminada | #83 | Verificada contra servicios simulados; E2E pendiente de HU-305 y Docker |
| [HU-504](epicas/EP-05-frontend.md) | Visualizar el estado de la notificación | 4 | Juan | 3 | En revisión | [#90](https://github.com/SarAlbN1/AS-foodflow-EDA/pull/90) | Muestra `content` según el contrato de #89; E2E pendiente de HU-305 y Docker |
| [HU-006](epicas/EP-00-base-tecnica-y-estandares.md) | Guardas de arquitectura ejecutables | 5 | Juan | 5 | Terminada | #85 | `bash scripts/verify-architecture.sh`; la comprobación de Compose se activa con HU-607 |
| [HU-505](epicas/EP-05-frontend.md) | Visualizar el flujo integral de un pedido | 5 | Juan | 5 | Pendiente | | |
| [HU-506](epicas/EP-05-frontend.md) | Unificar la interfaz web con un diseño base | 5 | Juan | 3 | En revisión | | HU transversal añadida el 2026-09-28. Layout común, estilos centralizados, total en formato `es-CO` y fuente alojada en el proyecto; verificada en el navegador contra el gateway real |
| [HU-601](epicas/EP-06-resiliencia-y-calidad.md) | Hacer idempotentes los consumidores Kafka | 5 | Sara | 5 | Pendiente | | |
| [HU-602](epicas/EP-06-resiliencia-y-calidad.md) | Aplicar reintentos y DLQ a eventos fallidos | 5 | Sara | 5 | Pendiente | | |
| [HU-603](epicas/EP-06-resiliencia-y-calidad.md) | Implementar logs estructurados y correlacionados | 5 | Sara | 3 | Pendiente | | |
| [HU-605](epicas/EP-06-resiliencia-y-calidad.md) | Automatizar pruebas de integración del flujo Kafka | 5 | Sara | 5 | Pendiente | | |
| [HU-607](epicas/EP-06-resiliencia-y-calidad.md) | Scripts de arranque, parada y prueba de humo | 5 | Juan | 2 | Pendiente | | |
| [HU-606](epicas/EP-06-resiliencia-y-calidad.md) | Validar el flujo end-to-end contenerizado | 6 | Sara | 8 | Pendiente | | |
| [HU-608](epicas/EP-06-resiliencia-y-calidad.md) | Verificar los atributos de calidad | 6 | Juan | 5 | Pendiente | | |
| [HU-701](epicas/EP-07-entregables-y-sustentacion.md) | README, tag y release | 6 | Sara | 3 | Pendiente | | |
| [HU-704](epicas/EP-07-entregables-y-sustentacion.md) | Diagramas exportados desde sus fuentes | 6 | Juan | 2 | Pendiente | | |
| [HU-705](epicas/EP-07-entregables-y-sustentacion.md) | Lecciones aprendidas | 6 | Sara | 5 | Pendiente | | |
| [HU-706](epicas/EP-07-entregables-y-sustentacion.md) | Presentación, demo y ensayo | 6 | Juan | 5 | Pendiente | | |
| [HU-707](epicas/EP-07-entregables-y-sustentacion.md) | Segmento de arquitectura en la sustentación | 6 | Sara | 2 | Pendiente | | |
| [HU-007](epicas/EP-00-base-tecnica-y-estandares.md) | CI automático (opcional) | - | - | - | Opcional | | |
| [HU-010](epicas/EP-00-base-tecnica-y-estandares.md) | Versionado de esquemas con Flyway (opcional) | - | - | - | Opcional | | |
| [HU-011](epicas/EP-06-resiliencia-y-calidad.md) | Pruebas de integración con Testcontainers (opcional) | - | - | - | Opcional | | |
| [HU-205](epicas/EP-02-pagos.md) | Consultar el pago de un pedido | - | - | - | Opcional | | |
| [HU-503](epicas/EP-05-frontend.md) | Visualizar el resultado del pago | - | - | - | Opcional | | |
