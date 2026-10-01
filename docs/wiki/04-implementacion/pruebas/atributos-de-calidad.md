# Atributos de calidad medidos (HU-608)

[← Pruebas ejecutadas](README.md) · [Criterios](../../02-arquitectura/atributos-de-calidad.md) · [Índice de la wiki](../../Home.md)

Los umbrales de la página [Atributos de calidad verificables](../../02-arquitectura/atributos-de-calidad.md)
eran **propuestos** (punto abierto A-5). Esta página los calibra con mediciones
reales y deja, por cada criterio, el comando que las repite.

## Cómo se reproduce

```bash
bash scripts/up.sh                              # entorno completo en contenedores
bash scripts/verify-quality-attributes.sh       # las nueve pruebas, en orden
```

Opciones útiles:

| Opción | Para qué |
|---|---|
| `--solo rendimiento,seguridad` | Ejecuta solo las pruebas indicadas (`--listar` las enumera) |
| `--sin-disruptivos` | Omite disponibilidad, escalabilidad y replay, las tres que detienen contenedores |
| `CARGA_PEDIDOS=50 bash scripts/...` | Cambia el tamaño de la carga concurrente |

El script no usa ninguna herramienta opcional de la página
[Visión y alcance](../../01-producto/vision-y-alcance.md) (criterio 5): ni
Testcontainers, ni generadores de carga externos, ni `jq`. Solo `curl`, `docker`
y el propio Compose, que es lo que se demuestra. Deja el entorno como lo
encontró: reinicia lo que detiene y retira la réplica que levanta.

## Entorno de la medición

| | |
|---|---|
| Fecha | 2026-09-30, 13:24 `-05` (ejecución registrada abajo) |
| Rama | `feat/HU-608-verificar-atributos-calidad`, sobre `f50b9d3` |
| Host | macOS (Darwin 25.6.0), Docker 29.7.2 |
| Entorno | Los once contenedores de `infrastructure/compose/docker-compose.yml` |
| Imágenes | `foodflow/*:0.0.1`, construidas el 2026-09-29 a las 17:00 `-05` |
| Tópicos | `orders.events`, `payments.events`, `notifications.events`, 3 particiones cada uno |
| Stack | Java 25, Spring Boot 4.1.1, Kafka 4.3.1 (KRaft), PostgreSQL 18.6 |

**Las bases no partían vacías.** El entorno acumulaba pedidos y eventos de las
sesiones anteriores, y eso es deliberado en las pruebas de idempotencia y de
replay: el replay de un tópico recién creado no demuestra nada.

**Salvedad sobre el formato de los logs.** Las imágenes medidas se construyeron
antes de que se integrara el *logging* estructurado de HU-603: escriben el
patrón de consola con `correlationId=<valor>` en vez de una línea JSON con
`"correlationId":"<valor>"`. La prueba de trazabilidad acepta los dos formatos,
así que el criterio queda verificado igual, pero **la medición del formato JSON
está pendiente de repetirse** tras un `bash scripts/up.sh` que reconstruya las
imágenes. Es lo único de esta página que depende de esa reconstrucción: el
flujo, los tiempos y los demás criterios no cambian con el formato del log.

## Resultado por atributo

| Atributo | Umbral o criterio | Valor medido | Veredicto |
|---|---|---|---|
| Rendimiento | p95 de `POST /orders` < 500 ms con 20 solicitudes concurrentes | **p95 48 ms**, 20/20 con `201` | Cumple |
| Consistencia (eventual) | p95 de convergencia a estado final < 5 s con 20 pedidos | **p95 598 ms**, 20/20 convergidos | Cumple |
| Disponibilidad | Los pedidos alcanzan su estado final sin Notification Service; al reiniciarlo se procesa lo pendiente | `PAGADO` en **25 ms** con el servicio detenido; la consulta degrada con `503 DEPENDENCY_UNAVAILABLE`; al reiniciar, notificación `ENVIADA` | Cumple |
| Idempotencia | El mismo evento entregado dos veces produce 1 pago, 1 transición y 1 notificación | 1 pago, 1 notificación, 1 `OrderStatusChanged`; `updated_at` sin moverse | Cumple |
| Recuperabilidad | Replay sin duplicados; lo no publicado no se recupera (ADR-08) | Replay completo: estados idénticos, 164 → 164 `OrderStatusChanged`, ningún `updated_at` movido. Pedido creado con el broker caído: **0 eventos** en `orders.events`, estado `CREADO` | **Parcial**, confirmada |
| Desacoplamiento | Un consumidor adicional recibe `OrderCreated` sin modificar Order Service | El grupo `hu608-observador-…` recibió el evento del pedido creado durante la prueba | Cumple |
| Trazabilidad | Un `correlationId` localiza logs de los tres servicios y los eventos del pedido | 10 líneas de log (4 order · 2 payment · 4 notification) y 4 eventos (2 `orders.events` · 1 `payments.events` · 1 `notifications.events`) | Cumple |
| Testabilidad | Los dos flujos con un solo comando | `smoke-test.sh`: `RESULTADO: OK` en **17 s** | Cumple |
| Escalabilidad | Con 2 réplicas en el mismo grupo, las particiones se reparten | 2 miembros en `payment-service.orders`, particiones **2 y 1**; con las dos réplicas el pedido llegó a `PAGADO` en 37 ms | Cumple |
| Seguridad | Ni secretos ni contacto completo en registros; destino enmascarado | 0 líneas con el contacto de prueba y 0 con un secreto del `.env` en los cuatro contenedores; destino `h***@foodflow.test` | Cumple con salvedad (ver hallazgo 3) |

**Variabilidad entre ejecuciones.** Las tres corridas completas del mismo día
dieron p95 de `POST /orders` entre **48 y 131 ms** y p95 de convergencia entre
**598 y 1 092 ms**. La diferencia la explica la carga de la máquina, no el
sistema: la corrida más lenta compartía CPU con el drenado de un replay. Incluso
el peor caso queda a menos de un tercio del umbral de rendimiento y a un quinto
del de consistencia.

## Qué hace cada prueba

### Rendimiento y consistencia eventual

Una sola carga sirve a los dos criterios. Se lanzan `CARGA_PEDIDOS` (20)
`POST /orders` **concurrentes** y se mide dos cosas por pedido: lo que tarda la
respuesta HTTP —que no espera al pago— y lo que tarda el pedido en converger a
`PAGADO` o `PAGO_RECHAZADO`, sondeando `GET /orders/{id}`. El percentil se
calcula por rango más cercano sobre las 20 muestras.

### Disponibilidad

Se detiene Notification Service, se crea un pedido y se comprueba que llega a
`PAGADO` igual (regla 12). Mientras está caído, la consulta de notificaciones
degrada con `503 DEPENDENCY_UNAVAILABLE` en formato Problem Details, sin
afectar al pago. Al reiniciarlo, el evento que quedó en `payments.events` se
consume y la notificación aparece `ENVIADA`: el trabajo pendiente no se pierde.

### Idempotencia

**La segunda entrega es real, no simulada.** Se toma del tópico el
`PaymentApproved` del pedido y se vuelve a publicar tal cual —mismo `eventId`,
misma clave de partición— con el productor de consola de Kafka. Después se
comparan cuatro cosas contra la foto previa: pagos del pedido, notificaciones
del pedido, `OrderStatusChanged` publicados y `updated_at` de la fila. La marca
de tiempo es la comprobación estricta: sin ella, «no se reaplicó» y «se
reaplicó al mismo valor» serían indistinguibles.

En los registros de los servicios se ve el otro lado de ADR-09:

```text
Evento ya procesado, no se vuelve a aplicar.      (order-service)
Evento ya procesado, no se crea otra notificacion. (notification-service)
```

### Recuperabilidad — las dos mitades de «Parcial»

1. **Lo que sí se reconstruye.** Se detiene Order Service, se reinician a
   `earliest` los offsets de su grupo (`kafka-consumer-groups --reset-offsets`)
   y se vuelve a levantar: reprocesa `payments.events` entero. Estados,
   `updated_at` y número de `OrderStatusChanged` quedan idénticos. Es ADR-09
   sosteniendo el replay.
2. **Lo que no.** Se detiene Kafka, se crea un pedido —responde `201`, porque la
   publicación ocurre después del *commit* y no bloquea la respuesta (ADR-08)— y
   se reinicia Order Service antes de devolver el broker, de modo que el
   productor pierde lo que tenía en memoria. Con Kafka de vuelta, el pedido
   sigue en la base y su `OrderCreated` no existe en ningún tópico: **ningún
   replay lo recupera y el sistema no tiene forma de detectarlo**. Eso es,
   exactamente, la recuperabilidad *Parcial* que declara ADR-08.

### Desacoplamiento

Un `kafka-console-consumer` con un grupo nuevo (`hu608-observador-…`) se
suscribe a `orders.events`, se crea un pedido y el consumidor recibe su
`OrderCreated`. No se tocó una línea de Order Service.

### Trazabilidad

Se toma el `correlationId` del `OrderCreated` de un pedido y se busca en los
registros de los tres servicios y en los tres tópicos. Es correlación por logs,
no *tracing* distribuido: lo que se verifica es que un solo identificador basta
para reconstruir el recorrido.

### Testabilidad

`bash scripts/smoke-test.sh`, que recorre `PAY-OK` y `PAY-FAIL` de punta a
punta y termina distinto de cero si algo no cuadra.

### Escalabilidad

`--scale payment-service=2` no sirve: el servicio fija `container_name`. La
réplica se levanta con
[`infrastructure/compose/docker-compose.escalado.yml`](../../../../infrastructure/compose/docker-compose.escalado.yml),
que hereda del servicio original imagen, variables y red —incluido su grupo de
consumidores— y solo le cambia el nombre. Con las dos instancias en el mismo
grupo, `kafka-consumer-groups --describe` muestra las 3 particiones de
`orders.events` repartidas entre ambas. Al terminar, la réplica se retira.

### Seguridad

Tres comprobaciones: que ningún servicio registre el contacto completo, que
ningún registro contenga el valor de un secreto del `.env` y que el destino que
publica `GET /orders/{id}/notifications` salga enmascarado.

## Hallazgos de la calibración

**1. Los umbrales propuestos se conservan, con holgura medida.** Los valores
reales quedan muy por debajo de lo que exigía la página de criterios. Se dejan
como estaban: son umbrales de aceptación para una demostración en un portátil,
no objetivos de rendimiento, y bajarlos a la medida de esta máquina haría
fallar la prueba en cualquier equipo más lento sin que nada estuviera mal.

**2. Un replay completo cuesta minutos, no segundos, y la causa es la política
de reintentos.** `payments.events` arrastra eventos huérfanos de sesiones
anteriores —pagos de pedidos que ya no existen en Order DB—. Cada uno produce
`OrderNotFoundException`, que el manejador de HU-602 trata como error
*recuperable*: 3 intentos con espera 1 s, 2 s y 4 s antes de mandarlo a la DLQ,
es decir unos 7 s por evento huérfano. Drenar el tópico tras reiniciar los
offsets a `earliest` tardó **191 s** para ~100 eventos pendientes.

Nada de esto rompe ningún criterio —el estado final es correcto y no hay
duplicados—, pero deja dos consecuencias prácticas:

- El script espera hasta `ESPERA_REPLAY` (600 s) a que el grupo llegue a
  retraso 0, y ejecuta el replay **al final**, porque mientras drena cualquier
  otra medición mide la cola y no el flujo. Se comprobó midiendo:
  `smoke-test.sh` tarda **16 s** con el entorno en reposo y tardó **972 s**
  lanzado justo después de un replay.
- **Propuesta para una HU futura, no aplicada aquí:** un `OrderNotFound` sobre
  un evento antiguo no mejora por repetirse; encaja mejor como evento no
  procesable —a la DLQ sin reintentar— que como error transitorio. Cambiar esa
  clasificación es alcance de HU-602 y de ADR-05, no de HU-608.

**3. `GET /orders/{id}` devuelve `customerContact` completo.** El criterio de
seguridad decía «ningún log ni *endpoint* expone secretos ni el contacto
completo», pero el contrato REST (`contracts/api/openapi.yaml`) declara
`customerContact` **obligatorio** en `OrderResponse`, y la página
[API REST](../../03-contratos/api-rest.md) precisa que el enmascarado aplica a
los registros. Manda el contrato, que está por encima del resto de la wiki en
la jerarquía de fuentes. El criterio se reescribió para decir lo que de verdad
se verifica: registros sin contacto completo ni secretos, y destino de la
notificación enmascarado en el *endpoint* que lo publica. El cambio es de
redacción; el comportamiento no se tocó.

**4. Las imágenes medidas no traen el *logging* estructurado de HU-603.** Se
construyeron minutos antes de que se integrara `#113`, así que escriben el patrón
de consola en vez de JSON. La trazabilidad se verificó igual porque el
`correlationId` está en las dos formas.

**El formato JSON del código sí quedó comprobado**, ejecutando el servicio desde
las fuentes con `./mvnw verify` contra el mismo entorno. Una línea real de esa
corrida:

```json
{"timestamp":"2026-09-30T20:54:30.686243-05:00","@version":"1","message":"Pedido PAGADO orderId=aafa7f49-… paymentId=3189872f-… eventId=40b0487b-… correlationId=9ad9f115-…","logger_name":"com.foodflow.order.application.OrderPaymentService","thread_name":"main","level":"INFO","level_value":20000,"service":"order-service"}
```

Lo que queda pendiente es verlo **dentro del contenedor**, y eso depende de una
reconstrucción que hoy no es posible: el *daemon* no alcanza el registry
(`DeadlineExceeded` resolviendo `eclipse-temurin:25-jdk`, que ya no está en el
store local), así que `bash scripts/up.sh` falla en el *build*. Se repite cuando
el registry vuelva a estar accesible:

```bash
bash scripts/up.sh
bash scripts/verify-quality-attributes.sh --solo trazabilidad
```

**5. Dos hallazgos de infraestructura, reportados aparte.** `scripts/up.sh
--sin-build` falla en bash 3.2 por expandir un array vacío con `set -u`
(issue #126, es de HU-607). Y tras un reinicio del *daemon* los contenedores
quedan en la red anterior y los servicios no resuelven `order-db`: se arregla con
`docker compose down` y `up -d --wait`, no con un `start`. Conviene tenerlo a mano
antes de la demostración.

## Salida completa de la ejecución

```text
FoodFlow · HU-608 · verificación de atributos de calidad
Fecha:    2026-09-30 13:24:10 -05
Gateway:  http://localhost:8080
Pruebas:  rendimiento disponibilidad idempotencia desacoplamiento trazabilidad testabilidad seguridad escalabilidad replay

== Rendimiento y consistencia eventual — 20 pedidos concurrentes
  medida    p95 de POST /orders: 48 ms (umbral 500 ms, 20/20 creados)
  ok        Rendimiento: p95 por debajo del umbral sin esperar el pago
  medida    p95 de convergencia a estado final: 598 ms (20/20 convergidos, umbral 5000 ms)
  ok        Consistencia eventual: todos los pedidos convergen dentro del umbral

== Disponibilidad — Notification Service detenido
  ok        Notification Service detenido
  ok        El pedido alcanzó PAGADO en 25 ms sin Notification Service (regla 12)
  medida    convergencia sin Notification Service: 25 ms
  ok        La consulta de notificaciones degrada con 503 DEPENDENCY_UNAVAILABLE, sin afectar al pago
  ok        Al reiniciarlo procesó la notificación pendiente (estado ENVIADA)

== Idempotencia — reentrega real de PaymentApproved
  ok        Evento reentregado con el mismo eventId y la misma clave de partición
  medida    pagos 1 -> 1 · notificaciones 1 -> 1 · OrderStatusChanged 1 -> 1
  ok        Un solo pago
  ok        Una sola notificación
  ok        Ninguna transición nueva
  ok        updated_at del pedido no se movió: 2026-09-30 18:24:20.014096+00

== Desacoplamiento — consumidor adicional sobre orders.events
  ok        El grupo hu608-observador-743a3c02 recibió OrderCreated del pedido bd355c5b-f7af-4096-ab5b-03ce043f7939 sin tocar Order Service

== Trazabilidad — un correlationId a través de tres servicios y tres tópicos
  medida    correlationId del pedido d9ba13ce-4e31-4a98-94c8-fb670016a52c: 12087b4b-8868-45e5-a96b-8d9eed610cae
  ok        foodflow-order-service: 4 líneas con el correlationId
  ok        foodflow-payment-service: 2 líneas con el correlationId
  ok        foodflow-notification-service: 4 líneas con el correlationId
  medida    eventos con ese correlationId: orders.events=2 · payments.events=1 · notifications.events=1
  ok        El correlationId aparece en los tres tópicos

== Testabilidad — scripts/smoke-test.sh
  ok        smoke-test.sh recorrió PAY-OK y PAY-FAIL en 17s

== Seguridad — secretos y datos de contacto
  ok        Ningún servicio registra el contacto completo (ContactMasker, HU-603)
  ok        Ningún log contiene el valor de un secreto del .env
  medida    destino publicado por GET /orders/{id}/notifications: h***@foodflow.test
  ok        El destino de la notificación sale enmascarado
  aviso     GET /orders/{id} devuelve customerContact completo, como exige OrderResponse en el contrato

== Escalabilidad — reparto de particiones con 2 réplicas de Payment Service
  ok        Segunda réplica levantada (foodflow-payment-service-2)
  medida    miembros del grupo payment-service.orders: 2 · particiones: ...6cd13dca=2 particiones ...5c234f93=1 particiones 
  ok        Las particiones de orders.events se reparten entre las dos réplicas
  ok        Con dos réplicas el pedido llegó a PAGADO en 37 ms
  ok        Réplica retirada; el entorno queda como estaba

== Recuperabilidad — replay de payments.events con offsets a earliest
  ok        Offsets del grupo order-service.payments reiniciados a earliest en 3 particiones
  medida    estados: [CREADO=9 PAGADO=110 PAGO_RECHAZADO=9 ] -> [CREADO=9 PAGADO=110 PAGO_RECHAZADO=9 ]
  medida    OrderStatusChanged en orders.events: 164 -> 164
  ok        El replay no cambió ningún estado
  ok        El replay no publicó transiciones nuevas
  ok        Ningún updated_at se movió (2026-09-30 18:26:13.113494+00)

== Recuperabilidad — el evento que nunca llegó al broker no se recupera
  ok        El pedido 5f06d199-2e6d-4e47-88a0-06e8ec5c4ad5 quedó persistido con el broker caído (201)
  medida    eventos de 5f06d199-2e6d-4e47-88a0-06e8ec5c4ad5 en orders.events: 0 · estado del pedido: CREADO
  ok        Recuperabilidad Parcial demostrada: el pedido existe, su OrderCreated no, y ningún replay lo reconstruye (ADR-08)

== Resumen
  Rendimiento                p95 48 ms (n=20)                                                 umbral 500 ms
  Consistencia (eventual)    p95 598 ms (n=20)                                                umbral 5000 ms
  Disponibilidad             pedido a PAGADO en 25 ms sin Notification Service; notificación recuperada al reiniciar cumple
  Idempotencia               1 pago, 1 notificación, 1 transición; updated_at intacto       cumple
  Desacoplamiento            consumidor nuevo (hu608-observador-743a3c02) recibe OrderCreated sin cambios en Order Service cumple
  Trazabilidad               10 líneas de log y 4 eventos con el mismo correlationId         cumple
  Testabilidad               smoke-test.sh: RESULTADO OK en 17s                               cumple
  Seguridad                  sin secretos ni contacto en logs; destino enmascarado; customerContact completo en OrderResponse por contrato cumple con salvedad
  Escalabilidad              2 miembros en payment-service.orders; particiones ...6cd13dca=2 particiones ...5c234f93=1 particiones  cumple
  Recuperabilidad            replay sin duplicados; pedido 5f06d199-2e6d-4e47-88a0-06e8ec5c4ad5 persistido sin evento y no recuperable Parcial, como declara ADR-08

RESULTADO: OK
```

