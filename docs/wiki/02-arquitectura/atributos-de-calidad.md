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

## Idempotencia de los consumidores (HU-601)

Los tres servicios implementan ADR-09: tabla `processed_events` propia con `event_id` como clave primaria, escrita en la **misma transacción local** que el efecto de negocio. Si el registro falla, el efecto tampoco queda.

| Servicio | Consumidor | Efecto que protege |
|---|---|---|
| Order | `order-service.payments` | El cambio de estado del pedido y su `OrderStatusChanged` |
| Payment | `payment-service.orders` | El cobro y su evento de resultado |
| Notification | `notification-service.payments` | La notificación y su envío al proveedor |

Un evento ya registrado se ignora con `INFO` y su offset se confirma: una reentrega no es un error.

**El criterio 4 se cumple entregando el evento dos veces de verdad**, no simulando la segunda entrega. Las tres pruebas están en las suites de flujo de HU-605, que publican en Kafka real y comprueban que el efecto no se repite:

| Servicio | Prueba | Qué afirma |
|---|---|---|
| Payment | `laReentregaNoCobraDosVeces` | Un solo pago para el pedido, con el mismo `paymentId` |
| Order | `laReentregaNoVuelveAAplicar` | El estado no cambia y **`updated_at` no se mueve** |
| Notification | `laReentregaNoDuplicaEnElFlujoReal` | Una sola notificación para el pedido |

La de Order es la más estricta: comprobar solo el estado no distinguiría «no se reaplicó» de «se reaplicó al mismo valor». La marca de tiempo sí.

**Payment tiene dos guardas y las dos hacen falta.** `payments.order_id` es único y responde a «¿este pedido ya tiene pago?», que es lo que evita el doble cobro; `processed_events` responde a «¿este evento ya se procesó?», que es lo que ADR-09 pide y lo que distingue una reentrega de un evento nuevo sobre el mismo pedido.

## Reintentos y DLQ (HU-602)

Cada servicio registra su propio `DefaultErrorHandler` en `infrastructure.messaging`, con `ExponentialBackOff` y un `DeadLetterPublishingRecoverer` hacia `<tópico>.dlq`.

| Situación | Qué pasa |
|---|---|
| Error transitorio (la base no responde, por ejemplo) | **3 intentos**, espera inicial 1 s que se duplica. Agotados, el evento va a `<tópico>.dlq` |
| Evento no procesable (envelope ilegible, versión no soportada, payload fuera de contrato) | **A la DLQ sin reintentar**: no mejora por repetirlo y retrasaría al resto de la partición |
| Evento de otro tipo en un tópico compartido | Se ignora con `DEBUG` y se confirma. **No** es un error y no va a DLQ |
| Fallo de negocio del proveedor | La notificación queda `FALLIDA` y el offset se confirma. **No** lanza, así que nunca llega al manejador (regla 10) |

**Qué reemplaza.** Sin este manejador actuaba el de Spring Kafka por omisión, con `FixedBackOff(0, 9)`: **diez intentos seguidos sin espera** y, agotados, confirmaba el offset y seguía. Un corte de unos segundos bastaba para perder el evento sin rastro. El síntoma está documentado en HU-606: un evento huérfano atascaba la partición y hacía fallar la prueba de humo.

**Los consumidores ya no se tragan el evento.** Antes capturaban `UnsupportedEventException`, la registraban y confirmaban el offset. Ahora la dejan subir: el manejador la publica en la DLQ y el offset se confirma después, cuando el mensaje ya está a salvo.

### Cómo inspeccionar una DLQ en la demostración

```bash
docker exec foodflow-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic orders.events.dlq --from-beginning --timeout-ms 5000
```

Cambiando el tópico se ven las otras dos: `payments.events.dlq` y `notifications.events.dlq`. El mensaje conserva su clave y su partición, así que los eventos de un pedido siguen juntos también en la DLQ, y Spring añade cabeceras con el tópico original, el offset y la excepción.

Para provocar uno a propósito, basta publicar un evento con una versión que no existe:

```bash
docker exec foodflow-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic orders.events
> {"eventId":"...","eventType":"OrderCreated","eventVersion":99, ...}
```

Lo comprueba automáticamente `DeadLetterQueueIntegrationTests` en payment-service, que además verifica el criterio 4: el evento bueno publicado **detrás** del roto se procesa igual.

### Configuración

| Variable | Por defecto |
|---|---|
| `KAFKA_RETRY_MAX_ATTEMPTS` | `3` |
| `KAFKA_RETRY_INITIAL_INTERVAL` | `1s` |
| `KAFKA_RETRY_MULTIPLIER` | `2` |

## Validación end-to-end contenerizada (HU-606)

Todo el prototipo en contenedores, sin un solo componente corriendo en el host, y los dos escenarios de ADR-10 recorridos de punta a punta.

**Un comando levanta los once contenedores** (criterio 1):

```bash
bash scripts/up.sh              # o --completar-env si el .env es anterior a variables nuevas
bash scripts/smoke-test.sh
bash scripts/down.sh
```

### Los dos escenarios, verificados

| | `PAY-OK` | `PAY-FAIL` |
|---|---|---|
| `orders.status` | `PAGADO` | `PAGO_RECHAZADO` |
| `payments.status` | `APROBADO` | `RECHAZADO` / `PAGO_RECHAZADO_POR_TOKEN` |
| `notifications.status` | `ENVIADA`, 1 intento | `ENVIADA`, 1 intento |
| Texto al cliente | «Tu pago de 45.000,00 COP fue aprobado.» | «Tu pago de 45.000,00 COP fue rechazado. No se realizó ningún cobro.» |

**Ningún paso toca la base a mano** (criterio 6): todo entra por `POST /orders` en el gateway y el resto ocurre por eventos.

### Los eventos en los tres tópicos (criterio 5)

```
orders.events         OrderCreated       af36c7cb   correlationId=b7d63300
                      OrderStatusChanged af36c7cb   correlationId=b7d63300
                      OrderCreated       76c3d241   correlationId=05105da3
                      OrderStatusChanged 76c3d241   correlationId=05105da3
payments.events       PaymentApproved    af36c7cb   correlationId=b7d63300
                      PaymentRejected    76c3d241   correlationId=05105da3
notifications.events  NotificationSent   af36c7cb   correlationId=b7d63300
                      NotificationSent   76c3d241   correlationId=05105da3
```

**El mismo `correlationId` atraviesa los tres tópicos** por pedido. Es la traza completa de la coreografía: ocho hechos, tres servicios, ningún orquestador.

### La interfaz (criterio 2)

El frontend contenerizado sirve la aplicación y su *fallback* de rutas, y el recorrido que hace el navegador funciona por el gateway:

```
GET  http://localhost:4200/                     -> 200
GET  http://localhost:4200/orders/{id}          -> 200   (fallback de Nginx)
OPTIONS http://localhost:8080/orders            -> 200   (preflight CORS desde :4200)
GET  http://localhost:8080/orders/{id}          -> SMOKE-PAY-OK  PAGADO
GET  http://localhost:8080/orders/{id}/notifications -> ENVIADA | s***@foodflow.test
```

El destino sale enmascarado también aquí.

### Lo que hay que saber para que sea reproducible

**El entorno tiene que partir con los tópicos vacíos.** La primera ejecución falló con el pedido de `PAY-OK` quedándose en `CREADO`, y la causa no era el flujo: `payments.events` arrastraba eventos de ejecuciones anteriores cuyos pedidos ya no existían en Order DB. El consumidor arranca en `earliest`, cada evento huérfano produce `OrderNotFoundException`, y hasta HU-602 actúa el `DefaultErrorHandler` con `FixedBackOff(0, 9)`: **diez reintentos inmediatos por evento**, y solo después confirma el offset y sigue. Mientras desatasca ese historial, el evento nuevo no se procesa a tiempo.

`scripts/down.sh` deja Kafka vacío —no monta volumen— y conserva las tres bases, así que `down` + `up` basta. Con el entorno limpio, `smoke-test.sh` da `RESULTADO: OK`.

Ese atasco es exactamente el hueco que cierra **HU-602** con reintentos espaciados y DLQ: un evento no procesable saldría de la partición en vez de bloquearla.
