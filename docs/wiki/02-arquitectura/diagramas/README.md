# Diagramas

[← Índice de la wiki](../../Home.md)

Las **imágenes exportadas** del informe técnico viven aquí, versionadas, y las páginas de la wiki las muestran donde hablan de arquitectura. Las **fuentes** están en [`fuentes/`](fuentes/) (HU-704). Las imágenes que cita `main.tex` se copian además a `docs/informe/diagramas/` y `docs/informe/figuras/`, que es donde las busca el informe.

| Imagen | Vista | Dónde se muestra en la wiki |
|---|---|---|
| `hld-foodflow.png` | Alto nivel (capas) | [Estilo y flujo](../estilo-y-flujo.md) |
| `c4-c1-contexto.png` | C1 Contexto | [Estilo y flujo](../estilo-y-flujo.md) |
| `c4-c2-contenedores.png` | C2 Contenedores | [Reglas arquitectónicas](../reglas-arquitectonicas.md) |
| `c4-c3-order-service.png` | C3 Componentes de Order Service | [Convenciones](../../04-implementacion/convenciones.md) |
| `c4-code-order-application-service.png` | Nivel de código | [Convenciones](../../04-implementacion/convenciones.md) |
| `c4-dynamic-order-flow.png` | Vista dinámica del flujo | [Estilo y flujo](../estilo-y-flujo.md) |
| `c4-deployment-development.png` | Despliegue en desarrollo (vertical; el que usa el informe) | [Runbook de la demostración](../../04-implementacion/runbook-demo.md) |
| `c4-deployment-development-horizontal.png` | Despliegue en desarrollo, de izquierda a derecha | Solo para la presentación de la sustentación; el informe no lo usa |
| `system-landscape-foodflow.png` | System Landscape | [Visión y alcance](../../01-producto/vision-y-alcance.md) |
| `modelo-datos-foodflow.png` | Modelo de datos | [Persistencia](../../03-contratos/persistencia.md) |

Una imagen que contradiga al informe se corrige regenerándola desde su fuente, nunca editando la wiki para que cuadre con la imagen.

| Diagrama | Herramienta | Fuente |
|---|---|---|
| C1, C2, C3 (Order Service), System Landscape, vista dinámica y despliegue (vertical y horizontal) | Structurizr DSL | [`foodflow-structurizr-v9.dsl`](fuentes/foodflow-structurizr-v9.dsl) (una sola fuente; vistas `Dynamic_OrderFlow`, `Deployment_Development` y `Deployment_Development_Horizontal`) |
| Nivel de código de Order Service | PlantUML | [`order-service-code-v3.puml`](fuentes/order-service-code-v3.puml) |
| Modelo de datos | DBML (dbdiagram.io) | [`foodflow-modelo-datos-v9.dbml`](fuentes/foodflow-modelo-datos-v9.dbml) |
| Gráficas de mercado laboral | Mermaid (`xychart-beta`) | `mercado-laboral.mmd`, `ofertas-mercado-laboral.mmd`, `vacantes_junior_colombia.mmd`, `vacantes_senior_colombia.mmd`, `salarios_junior_colombia.mmd` y `salarios_senior_colombia.mmd`, en [`fuentes/`](fuentes/) |
| HLD | Elaborado a mano | Sin fuente versionada, por decisión del equipo |

La vista dinámica y el despliegue salen de Structurizr, como indican las leyendas del informe, y no de Mermaid o PlantUML como decía la primera versión de esta tabla.

## Convenciones

- Los elementos **fuera de alcance** (Backoffice, Analítica) usan borde punteado.
- Los diagramas usan `OrderStatusChanged` (no `OrderUpdated`) y reflejan `PAY-OK` / `PAY-FAIL`.
- Ninguna base de datos se conecta a Kafka en ningún diagrama.
- Notification Service consume `PaymentApproved`/`PaymentRejected` en paralelo con Order Service (abanico), no `OrderStatusChanged`.
- Cada cambio de diseño que altere un diagrama actualiza su fuente en el mismo PR.
