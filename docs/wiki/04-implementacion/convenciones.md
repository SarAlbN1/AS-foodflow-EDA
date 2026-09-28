# Convenciones de implementación

[← Índice de la wiki](../Home.md)

## Backend

Cada servicio separa al menos: API, aplicación, dominio, persistencia, mensajería y configuración. Order Service conserva los componentes del C3: `OrderController`, `OrderApplicationService`, `OrderValidator`, `OrderRepository`, `OrderEventPublisher`, `OrderEventConsumer` y `Order`. Los nombres pueden adaptarse al paquete Java, pero las responsabilidades no se mezclan. `@KafkaListener` y `KafkaTemplate` solo viven en `infrastructure/messaging`, y solo `notification-service/infrastructure/provider` realiza llamadas HTTP salientes.

## Errores HTTP

`400` entrada inválida, `404` recurso inexistente, `409` conflicto o duplicidad, `500` error no controlado, `503` dependencia no disponible. Formato Problem Details (página [API REST](../03-contratos/api-rest.md)). Sin trazas de pila.

## Mensajería, idempotencia y errores

- Idempotencia según ADR-09 y página [Persistencia](../03-contratos/persistencia.md).
- Confirmación de offset manual, solo tras el commit local.
- Reintentos del consumidor: 3 intentos, espera inicial 1 s, multiplicador 2. Sin reintento: deserialización inválida, esquema o versión no soportada.
- Tolerancia a mensajes corruptos con `ErrorHandlingDeserializer`.
- Los tópicos y sus DLQ se obtienen de configuración.

## Correlación y logs estructurados

`X-Correlation-Id` entra por el gateway (se genera si falta) y se propaga por eventos y llamadas HTTP. Los logs son **estructurados** (JSON) con al menos: `timestamp`, `level`, `service`, `correlationId`, `eventId`, `eventType`, `orderId` y `message`. El contacto del cliente es dato personal: se registra enmascarado (`a***@dominio.com`). Esta es la trazabilidad mínima del prototipo; no se implementa tracing distribuido.

## Consistencia

La consistencia entre servicios es **eventual**. El frontend tolera estados intermedios y actualiza mediante consultas periódicas o refresco explícito.

## Configuración

Toda configuración y secreto llega por variables de entorno documentadas en `.env.example`. Nunca se versionan credenciales ni un `.env` real.
