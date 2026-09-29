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

## Pruebas de integración del flujo EDA (HU-605)

Tres pruebas, una por tramo, contra **Kafka y PostgreSQL reales**. Cada una publica el evento de entrada con un productor real, deja que lo consuma el `@KafkaListener` del propio servicio y comprueba **las dos salidas**: la fila persistida y el evento resultante en el tópico. Verificar que un método fue invocado no cuenta (criterio 4).

| Tramo | Prueba | Comprueba |
|---|---|---|
| `OrderCreated → Payment` | `payment-service` · `OrderCreatedFlowIntegrationTests` | Pago `APROBADO`/`RECHAZADO` en Payment DB + `PaymentApproved`/`PaymentRejected` en `payments.events` |
| `PaymentApproved/Rejected → Order` | `order-service` · `PaymentResultFlowIntegrationTests` | Pedido `PAGADO`/`PAGO_RECHAZADO` en Order DB + `OrderStatusChanged` en `orders.events` |
| `PaymentApproved/Rejected → Notification` | `notification-service` · `PaymentResultFlowIntegrationTests` | Notificación `ENVIADA` en Notification DB + `NotificationSent` en `notifications.events`, y que una reentrega no duplica |

**Cómo se ejecutan** (criterio 5), con el entorno de `scripts/up.sh` o el Compose de infraestructura:

```bash
set -a && . ./.env && set +a
export KAFKA_BOOTSTRAP_SERVERS="localhost:${KAFKA_HOST_PORT:-29092}"
export ORDER_DB_URL="jdbc:postgresql://localhost:${ORDER_DB_HOST_PORT:-5433}/${ORDER_DB_NAME:-orderdb}"
export PAYMENT_DB_URL="jdbc:postgresql://localhost:${PAYMENT_DB_HOST_PORT:-5434}/${PAYMENT_DB_NAME:-paymentdb}"
export NOTIFICATION_DB_URL="jdbc:postgresql://localhost:${NOTIFICATION_DB_HOST_PORT:-5435}/${NOTIFICATION_DB_NAME:-notificationdb}"
export NOTIFICATION_PROVIDER_URL="http://localhost:${NOTIFICATION_PROVIDER_HOST_PORT:-8090}"
cd services/<servicio> && ./mvnw verify
```

Sin las variables, las tres se omiten solas y `./mvnw verify` sigue funcionando sin infraestructura. **No usan Testcontainers**, que es opcional (HU-011): la herramienta es el propio entorno Compose, que además es el que se demuestra.

**Dos decisiones que las hacen fiables:**

- **Grupo de consumidores propio de cada ejecución y lectura desde el final del tópico.** Leer desde el principio arrastra eventos históricos y huérfanos que retrasan la prueba al retener sus particiones durante los reintentos. ADR-09 evita duplicar los efectos ya procesados, pero no evita ese costo ni hace que esos eventos pertenezcan al escenario bajo prueba.
- **Se espera a que el consumidor tenga particiones asignadas antes de publicar.** Sin eso el evento saldría antes de que hubiera nadie escuchando y la prueba fallaría por una carrera, no por el flujo.

**Detener los servicios antes de ejecutarlas.** Si un `order-service` suelto está corriendo contra la misma base, es él quien aplica la transición y publica el evento, y la prueba pasa sin ejercitar su propia instancia. Se descubrió así: la prueba pasaba con los servicios levantados y fallaba sin ellos.
