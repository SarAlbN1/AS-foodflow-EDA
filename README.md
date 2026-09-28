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

## Cómo contribuir

Lee [`CONTRIBUTING.md`](CONTRIBUTING.md) y la carpeta [`docs/wiki/05-proceso/`](docs/wiki/05-proceso/README.md).

## Autores

Sara Albarracin y Juan Diego Rojas — Pontificia Universidad Javeriana, Arquitectura de Software, 2026.
