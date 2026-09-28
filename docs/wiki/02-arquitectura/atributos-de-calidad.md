# Atributos de calidad verificables

[← Índice de la wiki](../Home.md)

Cada atributo tiene un criterio verificable del prototipo. Los umbrales numéricos son **propuestos** y se calibran en HU-608 (punto abierto A-5).

| Atributo | Soporte | Criterio verificable | Prueba |
|---|---|---|---|
| Rendimiento | Alto | `POST /orders` responde con p95 menor a 500 ms con 20 solicitudes concurrentes en el entorno local, sin esperar el pago | Script de carga |
| Consistencia (eventual) | Limitado | El pedido converge a `PAGADO` o `PAGO_RECHAZADO` en menos de 5 s (p95) con 20 pedidos | Script E2E |
| Disponibilidad | Alto | Con Notification Service detenido, los pedidos alcanzan su estado final; al reiniciarlo se procesan las notificaciones pendientes | Prueba manual guiada |
| Idempotencia | Alto | El mismo evento entregado dos veces produce 1 pago, 1 transición y 1 notificación | Prueba automática |
| Recuperabilidad | **Parcial** | Reprocesar `payments.events` con un grupo nuevo reconstruye el estado sin duplicados. Los eventos que nunca llegaron a Kafka no se recuperan (ADR-08) | Prueba de replay |
| Desacoplamiento | Alto | Un consumidor adicional recibe `OrderCreated` sin modificar Order Service | Demostración |
| Trazabilidad | Limitado | Con un `correlationId` se localizan los logs de los tres servicios y los eventos del pedido | Prueba automática |
| Testabilidad | Limitado | Los flujos aprobado y rechazado se ejecutan con un solo comando | `smoke-test.sh` |
| Escalabilidad | Alto | Con 2 réplicas de Payment Service en el mismo grupo, las particiones se reparten entre ambas | Verificación con `kafka-consumer-groups` |
| Seguridad | Neutro | Ningún log ni endpoint expone secretos ni el contacto completo | Revisión y prueba |
