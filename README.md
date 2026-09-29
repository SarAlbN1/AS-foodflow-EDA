# FoodFlow EDA

Prototipo académico de una plataforma de pedidos de comida cuyo objetivo es **demostrar una Arquitectura Orientada a Eventos (EDA)** con un flujo funcional, persistente y verificable: crear un pedido, procesar su pago (simulado) y notificar el resultado.

> **Estado:** en construcción. Este README se completa en la HU-701 (descripción, tecnologías y pasos de despliegue).

## Stack

Angular + TypeScript (Nginx) · API Gateway · Spring Boot + Java (3 servicios) · PostgreSQL (una base por servicio) · Apache Kafka · Docker Compose o Podman Compose.

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
├── .github/           # Plantillas de PR e issues
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

La prueba de humo también mira el estado final del pedido (`PAGADO` / `PAGO_RECHAZADO`). Mientras Order Service no consuma el resultado del pago (HU-104/105/106) el pedido sigue en `CREADO`, y eso se informa como `PENDIENTE`, no como fallo; llegar al estado equivocado sí falla.

Detalle de Compose, salud y solución de problemas: [`infrastructure/compose/README.md`](infrastructure/compose/README.md).

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

## Cómo contribuir

Lee [`CONTRIBUTING.md`](CONTRIBUTING.md) y la carpeta [`docs/wiki/05-proceso/`](docs/wiki/05-proceso/README.md).

## Autores

Sara Albarracin y Juan Diego Rojas — Pontificia Universidad Javeriana, Arquitectura de Software, 2026.
