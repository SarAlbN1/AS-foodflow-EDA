# Estándares de documentación

[← Índice de la wiki](../Home.md)

## Estándares de documentación

| Documento | Contenido mínimo |
|---|---|
| `README.md` | Qué es y por qué existe; arranque en menos de 5 minutos; tecnologías; despliegue paso a paso; variables de entorno; cómo probar; estructura; enlaces a ADR y contratos; limitaciones conocidas; autores |
| `docs/wiki/02-arquitectura/adr/ADR-NN-*.md` | Estado, fecha, contexto, decisión, alternativas, consecuencias y acciones (formato de ADR estándar). Un cambio de decisión crea un ADR nuevo que reemplaza al anterior |
| `contracts/api/openapi.yaml` | Cada operación con ejemplos de solicitud y respuesta y códigos de error |
| `docs/wiki/04-implementacion/runbook-demo.md` | Cuándo usarlo, prerrequisitos, pasos de la demo, verificación de eventos y DLQ, y cómo restablecer el entorno |
| `docs/wiki/04-implementacion/pruebas/*.md` | Fecha, entorno y valores medidos de cada verificación, con el comando que la reproduce |
| `CONTRIBUTING.md` | Resumen de esta página [Proceso de trabajo](README.md) |

Principios: escribir para quien lee, empezar por lo más útil, mostrar comandos, enlazar en lugar de duplicar y mantener la documentación vigente.
