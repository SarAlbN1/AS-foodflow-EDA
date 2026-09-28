---
description: Implementa una historia de usuario siguiendo el flujo del repositorio
argument-hint: HU-###
allowed-tools: Read, Glob, Grep, Edit, Write, Bash
---

# Implementar $1

Implementa **únicamente** la historia `$1`. No toques otras HU.

## 1. Contexto obligatorio antes de escribir nada

Lee, en este orden:

1. `CLAUDE.md` (reglas arquitectónicas y jerarquía de fuentes).
2. La épica de `$1` en `docs/wiki/06-backlog/epicas/` — localízala en `docs/wiki/06-backlog/backlog.tsv` (columnas `file` y `branch`).
3. `docs/wiki/02-arquitectura/reglas-arquitectonicas.md`.
4. Las páginas que indique la tabla "Qué leer según la tarea" de `CLAUDE.md` para el tipo de cambio.

Comprueba que el issue de `$1` existe:

```bash
gh issue list --state all --search "\"$1 —\" in:title" --json number,title,labels,milestone
```

Si no existe, **detente** y propón crearlo con etiquetas, milestone y responsable; no lo inventes.

## 2. Declara el plan antes de implementar

Escribe explícitamente, y espera confirmación solo si algo es ambiguo:

- Los criterios de aceptación numerados de `$1`.
- El servicio propietario de los datos implicados.
- Los contratos afectados (`contracts/api/openapi.yaml`, `contracts/events/v1/*.schema.json`, tópicos, tablas).
- La rama que vas a crear (la de la columna `branch` del backlog).

## 3. Rama

```bash
git checkout main && git pull --ff-only origin main
git checkout -b <tipo>/$1-<slug>
```

Nunca commits directos a `main`.

## 4. Implementación

Orden: **dominio y aplicación primero**, luego adaptadores HTTP, Kafka y base de datos, luego pruebas.

Respeta sin excepción:

- Cada servicio solo accede a su propia base; sin dependencias de código entre servicios.
- Coordinación solo por eventos Kafka; sin REST entre servicios ni orquestador.
- Consumidores idempotentes (`eventId` + `processed_events`, misma transacción local).
- Tópicos y nombres de eventos desde configuración, no literales dispersos.
- Versiones solo desde `docs/wiki/04-implementacion/versiones.md`.
- Nada de la lista "No implementar" de `CLAUDE.md`.

Commits: `<tipo>(<ámbito>): <descripción imperativa en español> [$1]`.

## 5. Verificación

```bash
bash scripts/check-structure.sh
bash scripts/verify-architecture.sh   # si existe
```

Ejecuta las pruebas del componente afectado. **Verifica cada criterio de aceptación uno por uno** y anota con qué comando o evidencia lo compruebas.

## 6. Documentación

Actualiza cuando corresponda: contratos, OpenAPI, ADR, README del componente y `docs/wiki/06-backlog/estado.md`.

## 7. PR

Usa `.github/pull_request_template.md`, con `Closes #<n>`, las etiquetas del issue más `ai-assisted`, y el milestone del sprint. **No fusiones el PR.**

## 8. Reporte

Entrega el reporte del formato de `docs/wiki/05-proceso/trabajo-con-ia.md`:

```markdown
## $1 — Resultado

### Implementado
### Archivos principales modificados
### Criterios de aceptación
### Pruebas ejecutadas
### Decisiones / notas
```

No marques `$1` como terminada si algún criterio no está verificado: declara cuál y por qué.

## Ante ambigüedad

Si la decisión afecta un contrato, un tópico, un estado o el alcance: **detente y pregunta**. Si es local (nombre de una clase privada), elige lo más simple coherente con EDA y anótalo en "Decisiones / notas". Nunca resuelvas una contradicción entre fuentes en silencio.
