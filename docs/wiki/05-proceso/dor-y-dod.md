# Definition of Ready y Definition of Done

[← Índice de la wiki](../Home.md)

## DoR

Una HU está lista para entrar a un sprint cuando:

- Tiene actor, capacidad y valor explícitos.
- Sus criterios de aceptación son verificables.
- Sus contratos de entrada y salida están identificados.
- Su issue existe con etiquetas, milestone y responsable (página [Issues, etiquetas y milestones](issues-etiquetas-y-milestones.md)).
- Las dependencias externas están disponibles o pueden simularse.
- No contradice las reglas arquitectónicas ni las decisiones ADR.
- Cabe razonablemente en un sprint.

## DoD

Una HU se considera terminada únicamente cuando:

1. Cumple todos sus criterios de aceptación.
2. El código compila y las pruebas unitarias relevantes pasan.
3. Las pruebas de integración relevantes pasan cuando la HU involucra persistencia, Kafka o HTTP externo.
4. `bash scripts/verify-architecture.sh` pasa (HU-006).
5. No introduce dependencias entre servicios que violen la arquitectura.
6. No contiene secretos versionados ni registra el contacto completo en logs.
7. Los errores se manejan de forma controlada (Problem Details en HTTP; reintento o DLQ en Kafka).
8. Los logs relevantes tienen contexto suficiente para diagnóstico.
9. Los contratos OpenAPI y de eventos se actualizan cuando aplica.
10. La documentación mínima (README, ADR, `docs/wiki/06-backlog/estado.md`) se actualiza cuando aplica.
11. Se ejecuta con el entorno local documentado y puede demostrarse desde una entrada observable, no solo manipulando la base de datos.
12. El PR cumple la plantilla, fue revisado por la otra persona y se integró con squash a `main`.

La DoD **no** exige Flyway, Testcontainers ni CI automático.
