# Reglas arquitectónicas

[← Índice de la wiki](../Home.md)

## Reglas arquitectónicas obligatorias

1. Angular nunca accede directamente a Kafka ni a PostgreSQL.
2. Angular consume únicamente APIs HTTP expuestas por el API Gateway.
3. Order Service es el único propietario de Order DB.
4. Payment Service es el único propietario de Payment DB.
5. Notification Service es el único propietario de Notification DB.
6. Ninguna base PostgreSQL publica ni consume eventos Kafka, y Kafka nunca escribe directamente en PostgreSQL.
7. La comunicación asíncrona ocurre desde los servicios de aplicación hacia Kafka.
8. Order Service no llama por REST a Payment Service para iniciar el pago.
9. Payment Service inicia su procesamiento al consumir `OrderCreated`; el cliente **solo crea el pedido**.
10. Order Service y Notification Service reaccionan de forma independiente al resultado del pago.
11. Notification Service es el único servicio que llama al proveedor externo.
12. Los servicios no comparten tablas, repositorios JPA, modelos de persistencia ni clases de dominio.
13. Un fallo de Notification Service no impide que Order Service registre el resultado del pago.
14. Los consumidores son idempotentes.
15. Los errores transitorios usan reintentos controlados; los eventos no procesables terminan en DLQ.
16. Los eventos representan hechos ocurridos y no se modifican tras publicarse.
17. Todo evento usa los nombres y el envelope de la página [API REST](../03-contratos/api-rest.md).

## Decisión sobre el pago (ADR-10)

El cliente crea el pedido; `OrderCreated` dispara el pago. El resultado es determinista: `PAY-OK` produce `PaymentApproved` y `PAY-FAIL` produce `PaymentRejected`. Payment Service no consulta a Order Service, y no existe llamada `Order -> Payment` ni orquestador.
