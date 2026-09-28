# Git: ramas y commits

[← Índice de la wiki](../Home.md)

## Ramas

Modelo de una sola rama estable con ramas cortas (*trunk-based*).

| Elemento | Regla |
|---|---|
| Rama estable | `main`: siempre compila y se ejecuta con `scripts/up.sh` |
| Ramas de trabajo | `<tipo>/<HU-###>-<descripción-en-kebab-case>` |
| Tipos | `feat`, `fix`, `docs`, `test`, `refactor`, `chore` |
| Sin HU asociada | `chore/<descripción>` (solo para tareas menores de mantenimiento) |
| Ejemplos | `feat/HU-101-crear-pedido`, `fix/HU-104-transicion-invalida`, `docs/HU-005-adr-08-09-10-11` |
| Relación | Una HU, una rama, un PR |
| Vida | Corta; se integra en pocos días y se elimina tras el merge |
| Integración | Squash merge, con mensaje siguiendo la página [Git: ramas y commits](git-ramas-y-commits.md) |
| Prohibido | Commits directos a `main`, force-push a `main`, ramas de larga duración por servicio |

No se usan ramas `develop` ni `release/*`: las versiones se marcan con tags (página [Pull requests, protección y releases](pull-requests-y-releases.md)).

## Commits

Formato: `<tipo>(<ámbito>): <descripción imperativa en español> [HU-###]`

- **Tipos:** `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`.
- **Ámbitos:** `order`, `payment`, `notification`, `gateway`, `web`, `contracts`, `infra`, `mocks`, `docs`, `repo`.
- Ejemplos: `feat(order): publica OrderCreated tras persistir [HU-103]`, `docs(repo): registra ADR-08 [HU-005]`.
- Un commit expresa un cambio coherente. No se mezclan servicios distintos en un mismo commit salvo cambios de contrato.
