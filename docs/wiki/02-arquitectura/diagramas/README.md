# Diagramas

[← Índice de la wiki](../../Home.md)

Las **imágenes exportadas** del informe técnico viven aquí, versionadas, y las páginas de la wiki las muestran donde hablan de arquitectura. Las **fuentes** (`workspace.dsl`, `.mmd`, `.puml`, `.dbml`) las versiona HU-704.

| Imagen | Vista | Dónde se muestra en la wiki |
|---|---|---|
| `hld-foodflow.png` | Alto nivel (capas) | [Estilo y flujo](../estilo-y-flujo.md) |
| `c4-c1-contexto.png` | C1 Contexto | [Estilo y flujo](../estilo-y-flujo.md) |
| `c4-c2-contenedores.png` | C2 Contenedores | [Reglas arquitectónicas](../reglas-arquitectonicas.md) |
| `c4-c3-order-service.png` | C3 Componentes de Order Service | [Convenciones](../../04-implementacion/convenciones.md) |
| `c4-code-order-application-service.png` | Nivel de código | [Convenciones](../../04-implementacion/convenciones.md) |
| `c4-dynamic-order-flow.png` | Vista dinámica del flujo | [Estilo y flujo](../estilo-y-flujo.md) |
| `c4-deployment-development.png` | Despliegue en desarrollo | [Runbook de la demostración](../../04-implementacion/runbook-demo.md) |
| `system-landscape-foodflow.png` | System Landscape | [Visión y alcance](../../01-producto/vision-y-alcance.md) |
| `modelo-datos-foodflow.png` | Modelo de datos | [Persistencia](../../03-contratos/persistencia.md) |

Una imagen que contradiga al informe se corrige regenerándola desde su fuente, nunca editando la wiki para que cuadre con la imagen.

| Diagrama | Herramienta | Fuente esperada |
|---|---|---|
| C1 Contexto, C2 Contenedores, C3 Componentes (Order Service), System Landscape | Structurizr DSL | `workspace.dsl` (una sola fuente para las cuatro vistas) |
| Dinámico (flujo principal) | Mermaid | `foodflow-dinamico.mmd` |
| Despliegue | PlantUML | `foodflow-deployment.puml` |
| Modelo de datos | DBML (dbdiagram.io) | `foodflow-modelo-datos.dbml` |
| HLD | A definir | Ver informe |

## Convenciones

- Los elementos **fuera de alcance** (Backoffice, Analítica) usan borde punteado.
- Los diagramas usan `OrderStatusChanged` (no `OrderUpdated`) y reflejan `PAY-OK` / `PAY-FAIL`.
- Ninguna base de datos se conecta a Kafka en ningún diagrama.
- Cada cambio de diseño que altere un diagrama actualiza su fuente en el mismo PR.
