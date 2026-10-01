# FoodFlow EDA

Prototipo académico de una plataforma de pedidos de comida cuyo objetivo es **demostrar una Arquitectura Orientada a Eventos (EDA)** con un flujo funcional, persistente y verificable: crear un pedido, procesar su pago (simulado) y notificar el resultado.

El cliente **solo crea el pedido**. A partir de ahí nadie da órdenes: el pedido publica un hecho, y cada servicio reacciona por su cuenta. No hay orquestador, y los servicios no se llaman entre sí para coordinarse.

```
Angular ──> API Gateway ──> Order Service ──> Order DB
                                  │
                                  └─ OrderCreated ─────> orders.events
                                                              │
                                        Payment Service <─────┘
                                              │  └──> Payment DB
                                              │
                      PaymentApproved | PaymentRejected ─> payments.events
                                                              │
                        ┌─────────────────────────────────────┴──────────┐
                        v                                               v
                 Order Service                              Notification Service
                 PAGADO / PAGO_RECHAZADO                     Notification DB
                        │                                           │  └─> Proveedor (simulado)
                        └─ OrderStatusChanged ─> orders.events      │
                                                                    └─ NotificationSent |
                                                                       NotificationFailed
                                                                       ─> notifications.events
```

**Lo que hace interesante este diseño:** Order y Notification consumen el **mismo** evento de pago, cada uno en su grupo de consumidores. Ninguno espera al otro, y si Notification se cae, el pedido se marca como pagado igual. Un solo `correlationId` atraviesa los tres tópicos, así que el recorrido completo de un pedido se sigue con un único identificador.

## Stack

| Capa | Tecnología | Por qué |
|---|---|---|
| Interfaz | Angular + TypeScript, servida con Nginx | Único punto de contacto del cliente; nunca toca Kafka ni PostgreSQL |
| Borde | API Gateway (Spring Cloud Gateway) | Enruta, propaga el `correlationId` y aplica CORS. Oculta la ubicación de los servicios |
| Servicios | Spring Boot + Java (Order, Payment, Notification) | Un servicio por capacidad de negocio, cada uno dueño de sus datos |
| Datos | PostgreSQL, **una base por servicio**, esquema versionado con Flyway | Sin base compartida: nadie lee las tablas de otro; cada servicio migra el suyo al arrancar |
| Mensajería | Apache Kafka (modo KRaft) | Los hechos del dominio como eventos inmutables; `orderId` como clave de partición conserva el orden por pedido |
| Entorno | Docker Compose o Podman Compose | Todo el prototipo reproducible con un comando |

Las versiones exactas están fijadas en [versiones.md](docs/wiki/04-implementacion/versiones.md); el proyecto no usa `latest` en ninguna imagen.

## La API del borde

Son cuatro operaciones, todas sobre el API Gateway (`http://localhost:8080`). El contrato completo es [`contracts/api/openapi.yaml`](contracts/api/openapi.yaml); los errores siguen RFC 9457 (*Problem Details*).

| Operación | Para qué | Notas |
|---|---|---|
| `POST /orders` | Crear el pedido. Es lo único que el cliente ordena | **Exige** la cabecera `Idempotency-Key`; sin ella responde `400`. Repetir la misma clave devuelve el mismo pedido, no uno nuevo |
| `GET /orders/{id}` | Estado del pedido: `CREADO`, `PAGADO` o `PAGO_RECHAZADO` | El estado llega por consistencia eventual: justo después de crear el pedido es `CREADO` |
| `GET /orders/{id}/payment` | Resultado del pago: `APROBADO` o `RECHAZADO` | `404` mientras el pago aún no está registrado; no es un error, es que el flujo no ha llegado ahí |
| `GET /orders/{id}/notifications` | Notificaciones del pedido y su estado | El destino va enmascarado |

El token de la tarjeta decide el resultado: `PAY-OK` aprueba y `PAY-FAIL` rechaza ([ADR-10](docs/wiki/02-arquitectura/decisiones-adr.md)). Detalle en [api-rest.md](docs/wiki/03-contratos/api-rest.md).

## Documentación: la wiki del repositorio

Todo el diseño, los procesos y el backlog viven en la wiki, en [`docs/wiki/`](docs/wiki/Home.md):

| Necesitas | Ve a |
|---|---|
| Entender el producto y el alcance | [`docs/wiki/01-producto/`](docs/wiki/01-producto/vision-y-alcance.md) |
| Arquitectura, reglas y decisiones (ADR) | [`docs/wiki/02-arquitectura/`](docs/wiki/02-arquitectura/reglas-arquitectonicas.md) |
| Contratos REST, eventos y persistencia | [`docs/wiki/03-contratos/`](docs/wiki/03-contratos/api-rest.md) |
| Estructura del repo y convenciones | [`docs/wiki/04-implementacion/`](docs/wiki/04-implementacion/estructura-del-repositorio.md) |
| Git, etiquetas, PR y releases | [`docs/wiki/05-proceso/`](docs/wiki/05-proceso/README.md) |
| Historias de usuario y plan de sprints | [`docs/wiki/06-backlog/`](docs/wiki/06-backlog/README.md) |

## Estructura del repositorio

```text
foodflow-eda/
├── CLAUDE.md          # Instrucciones para asistentes de IA (referencia la wiki)
├── README.md
├── docs/
│   ├── wiki/          # FUENTE DE VERDAD: diseño, procesos de Git y backlog
│   └── informe/       # Documento técnico académico (main.tex)
├── frontend/          # Angular + Nginx
├── gateway/           # API Gateway
├── services/          # order-service, payment-service, notification-service
├── contracts/         # OpenAPI y JSON Schema de eventos
├── infrastructure/    # Compose, Kafka, PostgreSQL, Nginx
├── mocks/             # Proveedor de notificaciones simulado
├── scripts/           # Automatización
├── .github/           # Plantillas de PR e issues y el flujo de CI
└── .claude/           # Comandos y ajustes del asistente de IA
```

Detalle completo: [estructura del repositorio](docs/wiki/04-implementacion/estructura-del-repositorio.md).

## Cómo ejecutar, detener y probar localmente

Requisitos: JDK 25, Node.js 24.21.0 con npm y Docker o Podman con Compose. Las versiones fijadas están en [versiones.md](docs/wiki/04-implementacion/versiones.md). Maven no hace falta instalarlo: cada proyecto Java trae su *wrapper* (`mvnw`). El *wrapper* usa el JDK de `JAVA_HOME`, así que `JAVA_HOME` debe apuntar al JDK 25 (con un JDK anterior, la compilación falla con `release version 25 not supported`).

### Todo el prototipo con un comando (HU-607)

Solo hace falta Docker (o Podman) con Compose y Bash (en Windows, Git Bash). Desde un clon limpio:

| Acción | Comando | Qué hace |
|---|---|---|
| Levantar | `bash scripts/up.sh` | Crea `.env` desde `.env.example` si no existe, construye las imágenes y levanta frontend, API Gateway, los tres servicios, Kafka (con sus tópicos), las tres PostgreSQL y el proveedor simulado. Termina cuando todo está `healthy`; si algo no lo logra en 300 s (`UP_TIMEOUT`), falla y muestra el estado. `--sin-build` reutiliza las imágenes ya construidas. Antes de arrancar comprueba que `.env` tiene todas las variables de `.env.example` y se detiene listando las que falten o estén vacías; `--completar-env` añade las que falten con el valor de ejemplo |
| Probar | `bash scripts/smoke-test.sh` | Recorre el flujo por el gateway con `PAY-OK` y con `PAY-FAIL`: crea el pedido, repite el `POST` con la misma `Idempotency-Key`, lo consulta y comprueba que `PaymentApproved` o `PaymentRejected` llegue a `payments.events`. Sale con código distinto de 0 si algo no es lo esperado |
| Detener | `bash scripts/down.sh` | Elimina contenedores y red; los datos de las bases se conservan. `--limpiar` borra también los volúmenes, para recrear el entorno desde cero |

Con el entorno arriba: frontend en http://localhost:4200 y API Gateway en http://localhost:8080 (`/actuator/health`). Los servicios no publican puertos en el host: el cliente solo ve el gateway.

La prueba de humo también exige el estado final del pedido: `PAY-OK` termina en `PAGADO` y `PAY-FAIL` en `PAGO_RECHAZADO` (HU-104 y HU-105). Quedarse en `CREADO` o llegar a otro estado es un fallo.

Detalle de Compose, salud y solución de problemas: [`infrastructure/compose/README.md`](infrastructure/compose/README.md).

### Variables de entorno

Todas viven en un único `.env` en la raíz, que `up.sh` crea desde [`.env.example`](.env.example) si no existe. Un `.env` real nunca se versiona (`.gitignore` ya lo excluye) y las contraseñas de ejemplo se reemplazan por propias. Son 48 variables agrupadas así:

| Grupo | Ejemplos | Para qué |
|---|---|---|
| Imágenes | `POSTGRES_IMAGE`, `KAFKA_IMAGE` | Etiquetas fijas, nunca `latest` |
| Kafka | `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_HOST_PORT`, `ORDERS_TOPIC`, `PAYMENTS_TOPIC`, `NOTIFICATIONS_TOPIC` | Conexión, puerto en el host y nombres de los tópicos, que los servicios leen de configuración |
| Bases de datos | `ORDER_DB_*`, `PAYMENT_DB_*`, `NOTIFICATION_DB_*` | Nombre, usuario, contraseña, puerto y URL **por servicio**: credenciales distintas para cada base (regla 2) |
| Servicios y gateway | `*_SERVICE_PORT`, `GATEWAY_PORT`, `ORDER_SERVICE_URL`, `NOTIFICATION_SERVICE_URL`, `GATEWAY_CORS_ALLOWED_ORIGINS`, `GATEWAY_*_TIMEOUT` | Puertos, rutas del gateway, CORS y tiempos de espera |
| Proveedor simulado | `NOTIFICATION_PROVIDER_URL`, `NOTIFICATION_PROVIDER_MAX_ATTEMPTS`, `MOCK_SLOW_DELAY`, `MOCK_FLAKY_FAILURES` | Dónde está el mock y cómo se reintenta; los dos `MOCK_*` controlan los fallos que se demuestran |
| Consumidores y reintentos | `*_CONSUMER_GROUP`, `KAFKA_RETRY_*` | Un grupo de consumidores por servicio y la política de reintentos antes de la DLQ |

`up.sh` comprueba que el `.env` tenga todas las variables de la plantilla antes de arrancar y se detiene listando las que falten; `--completar-env` las añade con el valor de ejemplo. El significado de cada una está comentado en `.env.example`.

### Cada componente por separado

**Servicios y gateway (Spring Boot + Maven).** Desde la carpeta de cada proyecto (`services/order-service`, `services/payment-service`, `services/notification-service`, `gateway/api-gateway`):

| Acción | Comando (en Windows: `mvnw.cmd` en lugar de `./mvnw`) |
|---|---|
| Compilar y probar | `./mvnw verify` |
| Ejecutar | `./mvnw spring-boot:run` |
| Detener | `Ctrl+C` |

Para correr un servicio en el host contra la infraestructura de Compose, detén primero su contenedor (`docker stop foodflow-order-service`, por ejemplo) y usa `KAFKA_BOOTSTRAP_SERVERS=localhost:29092`: el `kafka:9092` de `.env` solo resuelve dentro de la red de Compose, y con él el productor falla y solo lo deja en el registro. Lo mismo con las URL de las bases: `.env` ya trae las del host (`localhost:5433`…).

**Frontend (Angular).** Desde `frontend/foodflow-web`:

| Acción | Comando |
|---|---|
| Instalar dependencias | `npm ci` |
| Compilar | `npm run build` |
| Probar | `npm test -- --watch=false` |
| Ejecutar | `npm start` (http://localhost:4200/) |
| Detener | `Ctrl+C` |

**Compilar y probar todo de una vez** (Bash o Git Bash, desde la raíz):

```bash
for p in services/order-service services/payment-service services/notification-service gateway/api-gateway; do
  (cd "$p" && ./mvnw -B verify) || exit 1
done
(cd frontend/foodflow-web && npm ci && npm run build && npm test -- --watch=false)
```

Cada componente documenta sus detalles en su propio `README.md`.

### Pruebas de integración y verificaciones

Las pruebas de integración necesitan Kafka y PostgreSQL. Por defecto usan los contenedores de Compose (`up.sh`); con el perfil `testcontainers` cada ejecución levanta los suyos, efímeros, y no hace falta el entorno:

```bash
(cd services/order-service && ./mvnw -B verify -Ptestcontainers)
```

Además del `verify` de cada proyecto, el repositorio trae comprobaciones propias:

| Comprobación | Comando |
|---|---|
| Estructura del repositorio | `bash scripts/check-structure.sh` |
| Contratos de eventos (JSON Schema) | `bash scripts/validate-events.sh` |
| Contrato REST (OpenAPI) | `bash scripts/validate-openapi.sh` |
| Reglas de arquitectura (ArchUnit) | `bash scripts/verify-architecture.sh` |
| Atributos de calidad | `bash scripts/verify-quality-attributes.sh` |

El flujo de [CI](.github/workflows/ci.yml) ejecuta en cada PR y en cada `push` a `main` lo mismo que se ejecuta en local: `verify` de los cinco proyectos Java, la tanda con `-Ptestcontainers`, el *build* y las pruebas del frontend, y las cuatro primeras verificaciones de la tabla. No usa secretos.

## Limitaciones conocidas

Son decisiones deliberadas de un prototipo académico, no descuidos. Cada una está argumentada en su ADR o en la página de alcance.

| Limitación | Consecuencia real | Dónde se decide |
|---|---|---|
| **Sin Transactional Outbox** | Un pedido puede quedar persistido sin que su evento llegue a Kafka. Se registra un `ERROR` y nadie lo reconcilia: la recuperabilidad es **Parcial** | [ADR-08](docs/wiki/02-arquitectura/decisiones-adr.md) |
| **Pago simulado** | `PAY-OK` aprueba y `PAY-FAIL` rechaza. No hay pasarela real ni integración financiera | [ADR-10](docs/wiki/02-arquitectura/decisiones-adr.md) |
| **Proveedor de notificaciones simulado** | Nada sale de la máquina. El mock imita los fallos (`*@fail.test`, `*@flaky.test`, `*@slow.test`) para poder demostrarlos | [Proveedor de notificaciones](docs/wiki/03-contratos/proveedor-notificaciones.md) |
| **Sin autenticación** | Los endpoints son públicos. Quien conozca el UUID de un pedido ve su estado y su notificación, con el destino enmascarado | [Visión y alcance](docs/wiki/01-producto/vision-y-alcance.md) |
| **Sin Circuit Breaker** | Con un único proveedor no hay riesgo de fallo en cascada que justifique la complejidad | [Visión y alcance](docs/wiki/01-producto/vision-y-alcance.md) |
| **Un solo canal (`EMAIL`)** y moneda fija (`COP`) | El canal y la moneda son constantes del prototipo, no configuración | [Contrato de eventos](docs/wiki/03-contratos/eventos.md) |
| **Kafka sin volumen** | Los tópicos se recrean en cada `up.sh`; los eventos no sobreviven a un `down`. Las tres bases sí conservan sus datos | [Compose](infrastructure/compose/README.md) |
| **Sin tracing distribuido** | La trazabilidad es el `correlationId` en logs estructurados, no un sistema de *spans* | [Visión y alcance](docs/wiki/01-producto/vision-y-alcance.md) |

Fuera de alcance por decisión: Saga, CQRS, Event Sourcing, Kubernetes, *backoffice* y analítica.

## Versión

La entrega final es el tag **`v1.0.0`** y su release en GitHub. Las notas (HU incluidas, cómo ejecutarlo, los dos escenarios de demostración, limitaciones y la verificación de los criterios de éxito) están en [notas-release-v1.0.0.md](docs/wiki/05-proceso/notas-release-v1.0.0.md). El versionado del proyecto y los pre-releases por sprint se describen en [pull-requests-y-releases.md](docs/wiki/05-proceso/pull-requests-y-releases.md).

## Cómo contribuir

Lee [`CONTRIBUTING.md`](CONTRIBUTING.md) y la carpeta [`docs/wiki/05-proceso/`](docs/wiki/05-proceso/README.md).

## Autores

Sara Albarracin y Juan Diego Rojas — Pontificia Universidad Javeriana, Arquitectura de Software, 2026.
