# Bootstrap del repositorio

[← Índice del proceso](README.md)

Runbook para **publicar y configurar** el repositorio por primera vez. Lo ejecuta Claude Code (o una persona) desde la raíz del repo, que ya contiene la estructura, la wiki, las plantillas y los scripts.

> **Alcance del bootstrap:** publicar y configurar. **No** crea proyectos Spring Boot ni Angular, ni código de negocio: eso es la HU-001 y las siguientes.

## Variables

| Variable | Significado |
|---|---|
| `OWNER` | Usuario u organización de GitHub que será dueña del repo |
| `REPO` | Nombre del repositorio (`foodflow-eda`) |
| `SARA_GH` | Usuario de GitHub de Sara |
| `JUAN_GH` | Usuario de GitHub de Juan |
| `VISIBILITY` | `public` (por defecto; el repositorio debe ser público en la entrega) |

Si alguna falta, **pregunta**; no la inventes.

## Pasos

### 1. Verificar el entorno
```bash
git --version && gh --version
gh auth status
git config user.name && git config user.email
```
Si `gh` no está autenticado, detente e indica `gh auth login`. Para el Project de GitHub hace falta el permiso `project` (`gh auth refresh -s project`); si no está, continúa y trata el paso 8 como opcional.

### 2. Validar la estructura
```bash
chmod +x scripts/*.sh
bash scripts/check-structure.sh      # debe imprimir "Estructura correcta"
```

### 3. Incorporar el informe
Si existe `main.tex` (en el directorio padre o en la ruta que indique Sara), cópialo a `docs/informe/main.tex`. Si no existe, deja `docs/informe/README.md` y avisa en el reporte.

### 4. Commit inicial (excepción de bootstrap: único commit directo a `main`)
```bash
git init -b main
git add -A
git commit -m "chore(repo): estructura inicial, wiki y estándares [HU-009]"
```

### 5. Publicar
```bash
gh repo create "$OWNER/$REPO" --"$VISIBILITY" --source=. --remote=origin --push \
  --description "Prototipo académico de arquitectura orientada a eventos (EDA): Angular, Spring Boot, PostgreSQL y Kafka"
```

### 6. Configurar GitHub
```bash
DRY_RUN=1 JUAN_GH="$JUAN_GH" bash scripts/setup-github.sh    # revisa qué hará
JUAN_GH="$JUAN_GH" bash scripts/setup-github.sh              # aplica
```
Esto configura: solo squash merge y borrado de ramas al fusionar; etiquetas (respetando las 10 por defecto); milestones `Sprint 1` a `Sprint 6`; invitación a Juan; y, **al final**, la protección de `main`.

> Si el repositorio es privado en un plan gratuito, la protección de ramas no está disponible y la llamada falla: repórtalo, no lo ocultes.
> Tras la protección, cualquier corrección va por rama y PR (`chore/bootstrap-<slug>`), no por commit directo.

### 7. Crear los issues del Sprint 1
```bash
DRY_RUN=1 SARA_GH="$SARA_GH" JUAN_GH="$JUAN_GH" bash scripts/create-issues.sh 1
SARA_GH="$SARA_GH" JUAN_GH="$JUAN_GH" bash scripts/create-issues.sh 1
```
Los issues de los demás sprints ya están creados: el 2026-09-28 se ejecutó `bash scripts/create-issues.sh all` (#18 a #60). El script salta las HU que ya tienen issue, así que si se añade una HU a `backlog.tsv` basta con volver a ejecutarlo con `all`.

### 8. Project de GitHub (mejor esfuerzo)
```bash
gh project create --owner "$OWNER" --title "FoodFlow EDA"
# con el número devuelto (N):
gh project field-create N --owner "$OWNER" --name "Points" --data-type NUMBER
gh project link N --owner "$OWNER" --repo "$OWNER/$REPO"
```
Columnas: `Todo`, `En curso`, `En revisión`, `Hecho`. Si falla por permisos, no insistas: documenta el paso pendiente en el reporte.

### 9. Verificación final
```bash
gh repo view --json url,visibility,defaultBranchRef
gh label list --limit 100
gh api "repos/{owner}/{repo}/milestones" --jq '.[].title'
gh issue list --milestone "Sprint 1" --limit 50
gh api "repos/{owner}/{repo}/branches/main/protection" --jq '.required_pull_request_reviews.required_approving_review_count'
```

## Qué NO hacer

- No crees proyectos Spring Boot, Angular ni código de negocio.
- No modifiques las etiquetas por defecto de GitHub.
- No cambies contenido de la wiki salvo para corregir un error del bootstrap (y por PR).
- No inventes usuarios, versiones ni contraseñas.

## Relación con las historias

| HU | Efecto del bootstrap |
|---|---|
| HU-001 | Cumple el criterio 1 (estructura de carpetas) y el 4 (sin secretos). Quedan los proyectos que compilan y el README de ejecución. |
| HU-009 | Cumple los criterios 1 a 5 y el 6 (issues del Sprint 1). Queda verificar el checklist y completar el Project si falló. |

## Reporte final (formato)

```markdown
## Bootstrap — Resultado
- URL del repositorio: …
- Visibilidad: …
- Etiquetas: N creadas (10 por defecto intactas)
- Milestones: Sprint 1–6
- Protección de main: sí/no (motivo)
- Colaborador Juan: invitación enviada sí/no
- Issues Sprint 1: N creados
- Project: creado / pendiente (motivo)
- Pendientes o problemas: …
```

## Primeros pasos de Juan

1. Acepta la invitación al repositorio y clónalo.
2. Lee `README.md`, `CLAUDE.md` y la [Home de la wiki](../Home.md). Abre Claude Code en la carpeta.
3. Tus issues del Sprint 1: **HU-001** (5 pts), HU-004 (2), HU-009 (2), HU-401 (3) y HU-102 (2).
4. **Empieza por HU-001**: es la ruta crítica. Crea el esqueleto compilable de los proyectos (Angular, gateway y los tres servicios) para que Sara pueda construir sobre ellos.
   ```bash
   git checkout -b chore/HU-001-inicializar-estructura-repositorio
   ```
5. Abre el PR con la plantilla; Sara lo revisa. Después continúa con HU-009 (verificar el checklist) y HU-004.
6. HU-401 y HU-102 dependen de que Sara complete HU-101; coordinen el orden.
