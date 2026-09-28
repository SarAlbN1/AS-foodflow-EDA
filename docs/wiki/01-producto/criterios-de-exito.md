# Criterios de éxito

[← Índice de la wiki](../Home.md)

FoodFlow cumple el objetivo del prototipo cuando puede demostrarse que:

1. El cliente crea un pedido desde Angular (sin realizar el pago directamente).
2. La solicitud entra por el API Gateway con su `Idempotency-Key`.
3. Order Service persiste el pedido y su snapshot de contacto en Order DB.
4. Order Service publica `OrderCreated`.
5. Payment Service consume el evento sin llamada directa desde Order Service.
6. Con `PAY-OK` el pago se aprueba y con `PAY-FAIL` se rechaza, de forma reproducible.
7. Payment Service persiste el pago y publica `PaymentApproved` o `PaymentRejected`.
8. Order Service consume el resultado, actualiza Order DB y publica `OrderStatusChanged`.
9. Notification Service consume el mismo resultado de forma independiente y persiste la notificación.
10. Notification Service invoca al proveedor (mock) y publica `NotificationSent` o `NotificationFailed`.
11. El frontend permite observar el resultado eventual y las notificaciones.
12. Los eventos duplicados no duplican efectos (`processed_events`).
13. Los errores recuperables se reintentan y los no procesables terminan en DLQ.
14. Un `correlationId` permite rastrear el flujo en los logs estructurados de los tres servicios.
15. El API está documentado con OpenAPI y sus errores usan Problem Details.
16. Los atributos de calidad se verifican con pruebas reproducibles.
17. Todo el prototipo se levanta de forma reproducible con contenedores.
18. El código conserva la separación mostrada en HLD, C2, C3, dinámico y despliegue.
19. Existe el tag y release `v1.0.0` y el repositorio es público.
