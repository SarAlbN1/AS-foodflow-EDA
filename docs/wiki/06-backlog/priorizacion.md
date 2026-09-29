# Priorización

[← Índice de la wiki](../Home.md)

## P0 — Imprescindible

- HU-001 Inicializar la estructura del repositorio.
- HU-002 Levantar infraestructura local reproducible.
- HU-003 Definir y versionar contratos de eventos v1.
- HU-004 Configurar tópicos Kafka del prototipo.
- HU-005 Registrar los ADR del proyecto.
- HU-008 Alinear el documento técnico con las decisiones consolidadas.
- HU-009 Configurar los estándares del repositorio.
- HU-101 Crear un pedido válido.
- HU-102 Consultar un pedido por identificador.
- HU-103 Publicar OrderCreated al crear el pedido.
- HU-104 Actualizar el pedido ante un pago aprobado.
- HU-105 Actualizar el pedido ante un pago rechazado.
- HU-107 Evitar pedidos duplicados con Idempotency-Key.
- HU-201 Consumir OrderCreated para iniciar un pago.
- HU-202 Procesar y persistir el resultado del pago.
- HU-203 Publicar PaymentApproved.
- HU-204 Publicar PaymentRejected.
- HU-301 Crear una notificación ante el resultado del pago.
- HU-302 Enviar una notificación mediante el proveedor externo.
- HU-303 Registrar y publicar una notificación enviada.
- HU-304 Registrar y publicar una notificación fallida.
- HU-306 Mock del proveedor de notificaciones.
- HU-401 Enrutar operaciones de pedidos.
- HU-404 Contrato OpenAPI y errores Problem Details.
- HU-501 Crear un pedido desde Angular.
- HU-502 Consultar y visualizar el estado del pedido.
- HU-601 Hacer idempotentes los consumidores Kafka.
- HU-602 Aplicar reintentos y DLQ a eventos fallidos.
- HU-606 Validar el flujo end-to-end contenerizado.
- HU-607 Scripts de arranque, parada y prueba de humo.
- HU-608 Verificar los atributos de calidad.
- HU-701 README, tag y release.
- HU-702 Proyección laboral en la matriz de mercado.
- HU-703 Patrones, antipatrones y trazabilidad en el documento técnico.
- HU-704 Diagramas exportados desde sus fuentes.
- HU-705 Lecciones aprendidas.
- HU-706 Presentación, demo y ensayo.
- HU-707 Segmento de arquitectura en la sustentación.

## P1 — Importante

- HU-006 Guardas de arquitectura ejecutables.
- HU-106 Publicar OrderStatusChanged al cambiar el estado del pedido.
- HU-305 Consultar las notificaciones de un pedido.
- HU-402 Enrutar la consulta de notificaciones.
- HU-403 Propagar correlationId y manejar CORS.
- HU-504 Visualizar el estado de la notificación.
- HU-505 Visualizar el flujo integral de un pedido.
- HU-506 Unificar la interfaz web con un diseño base.
- HU-603 Implementar logs estructurados y correlacionados.
- HU-604 Exponer health checks de componentes.
- HU-605 Automatizar pruebas de integración del flujo Kafka.

Cualquier mejora que no contribuya a demostrar EDA o a cumplir un entregable se pospone hasta completar P0 y P1.
