# Diagramas

[← Índice de la wiki](../../Home.md)

Las **fuentes** de los diagramas viven aquí, versionadas. Las **imágenes exportadas** se incorporan al informe (`docs/informe/diagramas/`) en la HU-704.

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
