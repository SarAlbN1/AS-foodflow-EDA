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
└── .claude/           # Comandos y ajustes de Claude Code
```

Detalle completo: [estructura del repositorio](docs/wiki/04-implementacion/estructura-del-repositorio.md).

## Cómo ejecutar, detener y probar localmente

Requisitos: JDK 25, Node.js 24.21.0 con npm y Docker o Podman con Compose. Las versiones fijadas están en [versiones.md](docs/wiki/04-implementacion/versiones.md). Maven no hace falta instalarlo: cada proyecto Java trae su *wrapper* (`mvnw`).

> **Estado actual:** los proyectos son esqueletos sin funcionalidad de negocio (HU-001). A partir de la HU-607, `scripts/up.sh`, `scripts/down.sh` y `scripts/smoke-test.sh` levantarán, detendrán y probarán la solución completa.

**Infraestructura (Kafka y las tres PostgreSQL).** Desde la raíz del repositorio:

```bash
cp .env.example .env                                                                # primera vez; cambia las contraseñas
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml up -d   # levantar
docker compose --env-file .env -f infrastructure/compose/docker-compose.yml down    # detener
```

Detalle y comprobación de salud: [`infrastructure/compose/README.md`](infrastructure/compose/README.md).

**Servicios y gateway (Spring Boot + Maven).** Desde la carpeta de cada proyecto (`services/order-service`, `services/payment-service`, `services/notification-service`, `gateway/api-gateway`):

| Acción | Comando (en Windows: `mvnw.cmd` en lugar de `./mvnw`) |
|---|---|
| Compilar y probar | `./mvnw verify` |
| Ejecutar | `./mvnw spring-boot:run` |
| Detener | `Ctrl+C` |

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
