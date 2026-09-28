---
description: Prepara el pull request con la plantilla y las etiquetas correctas
allowed-tools: Read, Glob, Grep, Bash
---

# Preparar el PR de la rama actual

## 1. Comprobaciones previas

```bash
git branch --show-current     # no debe ser main
git status --short            # sin cambios sin confirmar
git log --oneline main..HEAD
```

Si la rama es `main`, **detente**: el trabajo va en una rama `<tipo>/<HU-###>-<slug>`.

Deduce la HU del nombre de la rama y localiza su issue:

```bash
gh issue list --state all --search "\"HU-### —\" in:title" --json number,title,labels,milestone,assignees
```

Si no hay issue, detente y pregunta; el PR necesita `Closes #<n>`.

## 2. Verificación antes de abrir

Ejecuta `/verificar` (o su equivalente) y no continúes si hay un criterio de aceptación sin verificar: decláralo en el PR en lugar de marcarlo.

## 3. Push

```bash
git push -u origin "$(git branch --show-current)"
```

Nunca `--force` contra `main`.

## 4. Cuerpo del PR

Rellena `.github/pull_request_template.md` de verdad:

- `Closes #<n>` con el número real del issue.
- Un criterio de aceptación por línea, marcado solo si está verificado.
- Las pruebas ejecutadas con el comando y su resultado real.
- El checklist arquitectónico marcado punto por punto.

Título del PR: formato de commit, `<tipo>(<ámbito>): <descripción imperativa en español> [HU-###]`.

## 5. Crear el PR

```bash
gh pr create \
  --base main \
  --title "<tipo>(<ámbito>): <descripción> [HU-###]" \
  --body-file <cuerpo.md> \
  --label "<etiquetas del issue>" --label "ai-assisted" \
  --milestone "Sprint N" \
  --assignee @me
```

Etiquetas: las mismas del issue (tipo, área, prioridad, épica) más `ai-assisted` si hubo asistencia sustancial de IA.

## 6. Después de crear

- Pide revisión a la otra persona del equipo; **el autor no aprueba su propio PR**.
- **No fusiones el PR.** La integración es squash merge, y la hace quien revisa.
- Informa la URL del PR y qué queda pendiente de verificar.
