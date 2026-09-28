# Estructura del repositorio

[← Índice de la wiki](../Home.md)

## Regla de oro: dónde vive cada cosa

| Necesitas… | Vive en | Nota |
|---|---|---|
| Diseño, decisiones, políticas de Git, backlog | `docs/wiki/` | **Fuente de verdad.** Cambia por PR |
| Instrucciones para asistentes de IA | `CLAUDE.md` | Corto; **referencia** la wiki, no la duplica |
| Contratos ejecutables (OpenAPI, JSON Schema) | `contracts/` | Los describe la wiki; son la verdad técnica del contrato |
| Documento técnico académico | `docs/informe/` | Refleja la wiki; no decide diseño |
| Código | `frontend/`, `gateway/`, `services/`, `mocks/` | |
| Ejecución local y configuración operativa | `infrastructure/`, `scripts/` | |

## Árbol

```text
foodflow-eda/
├── CLAUDE.md                          # IA: reglas críticas + tabla de "qué leer" que apunta a la wiki
├── README.md                          # Presentación del proyecto y enlaces
├── CONTRIBUTING.md                    # Resumen de las políticas de Git
├── LICENSE                            # Por definir (punto abierto A-7)
├── .gitignore  .gitattributes  .editorconfig  .env.example
│
├── .claude/                           # Configuración del asistente de IA
│   ├── settings.json
│   └── commands/                      # /hu  /verificar  /pr
├── .github/
│   ├── pull_request_template.md
│   ├── ISSUE_TEMPLATE/                # story, bug, adr, config
│   └── workflows/                     # Solo si se adopta CI (opcional)
│
├── docs/
│   ├── wiki/                          # ★ FUENTE DE VERDAD
│   │   ├── Home.md
│   │   ├── 01-producto/               # visión y alcance, stack y protocolos, criterios de éxito
│   │   ├── 02-arquitectura/           # estilo, reglas, ADR, comportamiento, calidad, trazabilidad, puntos abiertos
│   │   │   ├── adr/                   # ADR-01 … ADR-12 (plantilla e índice)
│   │   │   └── diagramas/             # fuentes (workspace.dsl, .mmd, .puml, .dbml) y exportaciones
│   │   ├── 03-contratos/              # API REST, eventos, proveedor (mock), persistencia
│   │   ├── 04-implementacion/         # estructura, convenciones, versiones, runbook de la demo
│   │   ├── 05-proceso/                # Git, etiquetas, PR, releases, DoR/DoD, IA, bootstrap
│   │   └── 06-backlog/                # épicas con sus HU, priorización, plan, estado, backlog.tsv
│   └── informe/                       # main.tex y figuras (entregable académico)
│
├── frontend/foodflow-web/             # Angular + Nginx
├── gateway/api-gateway/               # API Gateway
├── services/
│   ├── order-service/
│   ├── payment-service/
│   └── notification-service/
├── contracts/
│   ├── events/v1/                     # *.schema.json de los 6 eventos
│   └── api/openapi.yaml
├── infrastructure/
│   ├── compose/docker-compose.yml
│   ├── kafka/                         # topics.md y scripts/
│   ├── postgres/{order-db,payment-db,notification-db}/
│   └── nginx/
├── mocks/notification-provider/
└── scripts/                           # up, down, smoke-test, verify-architecture, setup-*, create-issues, check-structure
```

Cada carpeta de código incluye un `README.md` que declara su responsabilidad, las HU que la construyen y las reglas que le aplican.

## Organización interna de cada servicio

| Paquete | Contenido |
|---|---|
| `api` | Controladores y DTO HTTP |
| `application` | Casos de uso |
| `domain` | Entidades, estados y reglas de dominio |
| `validation` | Validadores (Order Service) |
| `infrastructure/persistence` | Repositorios y mapeos de base de datos |
| `infrastructure/messaging` | Productores y consumidores Kafka |
| `infrastructure/provider` | Cliente HTTP del proveedor (solo Notification Service) |
| `config` | Configuración |

Order Service conserva los componentes del C3: `OrderController`, `OrderApplicationService`, `OrderValidator`, `OrderRepository`, `OrderEventPublisher`, `OrderEventConsumer` y `Order`.

## Responsabilidad de cada carpeta raíz

| Carpeta | Responsabilidad |
|---|---|
| `frontend/` | Interfaz Angular y Nginx. |
| `gateway/` | Entrada REST; enruta sin reglas de negocio. |
| `services/` | Los tres dominios. |
| `contracts/` | Contratos públicos HTTP y de eventos; documentación de interoperabilidad, no lógica compartida. |
| `infrastructure/` | Kafka, PostgreSQL, Compose y configuración operativa local. |
| `mocks/` | Sustitutos de dependencias externas solo para desarrollo, demo y pruebas. |
| `docs/` | Wiki (diseño, procesos, backlog) e informe académico. |
| `scripts/` | Automatización de ejecución, verificación y preparación del repositorio. |

## Regla de dependencias

```text
Frontend -> API Gateway -> Servicio propietario -> Base propietaria
                                   |
                                   v
                                 Kafka
                                   |
                                   v
                            Otros servicios
```

No hay dependencias de código entre los microservicios. Si dos servicios necesitan el mismo dato, lo intercambian mediante un contrato de evento o de API, nunca importando clases del otro.

## Cómo se verifica

`bash scripts/check-structure.sh` comprueba que existen las rutas mínimas de esta estructura.
