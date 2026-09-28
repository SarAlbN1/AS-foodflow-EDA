# Visión y alcance

[← Índice de la wiki](../Home.md)

FoodFlow es un prototipo académico de una plataforma de pedidos de comida cuyo objetivo es **demostrar una Arquitectura Orientada a Eventos (EDA)** mediante un flujo funcional, persistente y verificable.

El alcance funcional se limita a:

1. Crear un pedido.
2. Procesar el pago asociado, de forma simulada y determinista.
3. Actualizar el estado del pedido según el resultado del pago.
4. Generar y enviar una notificación con el resultado.
5. Permitir al cliente consultar el estado del pedido y sus notificaciones.

El objetivo **no** es construir una plataforma completa de delivery.

## Entidades de negocio

Exactamente tres entidades principales:

| Entidad | Responsabilidad | Estados |
|---|---|---|
| `Order` / Pedido | Solicitud del cliente. Conserva un *snapshot* del contacto y canal de notificación. | `CREADO`, `PAGADO`, `PAGO_RECHAZADO` |
| `Payment` / Pago | Resultado del pago de un pedido. | `APROBADO`, `RECHAZADO` |
| `Notification` / Notificación | Mensaje generado y su estado de entrega. | `PENDIENTE`, `ENVIADA`, `FALLIDA` |

Relaciones lógicas: un Pedido tiene un Pago y puede generar varias Notificaciones. Al haber bases separadas, se expresan mediante identificadores lógicos y eventos, **no** mediante llaves foráneas entre bases.

## Alcance y no alcance

### Dentro del alcance

- Cliente Angular para crear pedidos y consultar su estado.
- Nginx para publicar el frontend.
- API Gateway como punto de entrada REST.
- Order Service, Payment Service y Notification Service (Spring Boot + Java).
- Una base PostgreSQL independiente por servicio.
- Apache Kafka como integración asíncrona.
- Eventos: `OrderCreated`, `OrderStatusChanged`, `PaymentApproved`, `PaymentRejected`, `NotificationSent`, `NotificationFailed`.
- Simulación determinista del pago (`PAY-OK` y `PAY-FAIL`, ADR-10).
- Snapshot de contacto y canal dentro del pedido (ADR-11).
- Consumidores idempotentes mediante `eventId` y `processed_events` (ADR-09).
- Reintentos y Dead Letter Queue.
- Contratos REST documentados con OpenAPI; errores en formato RFC 9457 Problem Details; encabezado `Idempotency-Key` en `POST /orders`.
- Integración HTTPS/REST con un proveedor de notificaciones (mock para demostración).
- Trazabilidad mínima mediante `correlationId` y logs estructurados.
- Pruebas verificables de los atributos de calidad (página [Atributos de calidad verificables](../02-arquitectura/atributos-de-calidad.md)).
- Contenerización con Docker Compose o Podman Compose.
- Entrega versionada mediante tag y release.

### Fuera del alcance de implementación

Pueden aparecer en la investigación o en la evolución propuesta, pero **no se implementan**:

- Transactional Outbox (ADR-08 acepta el riesgo).
- Saga, CQRS y Event Sourcing.
- Circuit Breaker.
- Distributed Tracing completo.
- Kubernetes.
- Backoffice Operativo y Analítica y Reportes (solo aparecen como contexto en el System Landscape).
- Gestión de restaurantes, catálogo, inventario, carrito complejo, repartidores, geolocalización, facturación y promociones.
- Autenticación y autorización completas; pasarela de pago real.
- Base de datos compartida entre servicios.
- Un orquestador central del flujo.
- Comunicación REST directa entre Order, Payment y Notification para coordinar el proceso.

### Herramientas opcionales

Flyway, Testcontainers y CI automático **pueden** incorporarse si aportan valor, pero **no son dependencias obligatorias** para considerar terminado el prototipo. Ninguna HU del plan puede exigirlos como criterio de aceptación (aparecen como P2 en la página [Backlog](../06-backlog/README.md)).
