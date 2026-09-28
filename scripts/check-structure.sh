#!/usr/bin/env bash
# Verifica que existe la estructura mínima del repositorio definida en la wiki.
# Uso: bash scripts/check-structure.sh
set -uo pipefail
cd "$(dirname "$0")/.."

required=(
  CLAUDE.md README.md CONTRIBUTING.md .gitignore .gitattributes .editorconfig .env.example
  .claude/settings.json .claude/commands/hu.md .claude/commands/verificar.md .claude/commands/pr.md
  .github/pull_request_template.md .github/ISSUE_TEMPLATE/story.yml .github/ISSUE_TEMPLATE/bug.yml
  .github/ISSUE_TEMPLATE/adr.yml .github/ISSUE_TEMPLATE/config.yml
  docs/wiki/Home.md
  docs/wiki/01-producto/vision-y-alcance.md docs/wiki/01-producto/stack-y-protocolos.md docs/wiki/01-producto/criterios-de-exito.md
  docs/wiki/02-arquitectura/estilo-y-flujo.md docs/wiki/02-arquitectura/reglas-arquitectonicas.md
  docs/wiki/02-arquitectura/decisiones-adr.md docs/wiki/02-arquitectura/comportamiento-del-flujo.md
  docs/wiki/02-arquitectura/atributos-de-calidad.md docs/wiki/02-arquitectura/trazabilidad.md
  docs/wiki/02-arquitectura/puntos-abiertos.md docs/wiki/02-arquitectura/adr/README.md
  docs/wiki/02-arquitectura/diagramas/README.md
  docs/wiki/03-contratos/api-rest.md docs/wiki/03-contratos/eventos.md
  docs/wiki/03-contratos/proveedor-notificaciones.md docs/wiki/03-contratos/persistencia.md
  docs/wiki/04-implementacion/estructura-del-repositorio.md docs/wiki/04-implementacion/convenciones.md
  docs/wiki/04-implementacion/versiones.md docs/wiki/04-implementacion/runbook-demo.md
  docs/wiki/05-proceso/README.md docs/wiki/05-proceso/git-ramas-y-commits.md
  docs/wiki/05-proceso/issues-etiquetas-y-milestones.md docs/wiki/05-proceso/pull-requests-y-releases.md
  docs/wiki/05-proceso/documentacion.md docs/wiki/05-proceso/dor-y-dod.md
  docs/wiki/05-proceso/trabajo-con-ia.md docs/wiki/05-proceso/bootstrap-repositorio.md
  docs/wiki/06-backlog/README.md docs/wiki/06-backlog/priorizacion.md docs/wiki/06-backlog/plan-de-sprints.md
  docs/wiki/06-backlog/estado.md docs/wiki/06-backlog/backlog.tsv
  docs/wiki/06-backlog/epicas/EP-00-base-tecnica-y-estandares.md
  docs/wiki/06-backlog/epicas/EP-07-entregables-y-sustentacion.md
  docs/informe/README.md
  frontend/foodflow-web/README.md gateway/api-gateway/README.md
  services/order-service/README.md services/payment-service/README.md services/notification-service/README.md
  contracts/events/v1/README.md contracts/api/README.md
  infrastructure/compose/README.md infrastructure/kafka/topics.md
  infrastructure/postgres/order-db/README.md infrastructure/postgres/payment-db/README.md
  infrastructure/postgres/notification-db/README.md infrastructure/nginx/README.md
  mocks/notification-provider/README.md
  scripts/setup-labels.sh scripts/setup-github.sh scripts/create-issues.sh scripts/check-structure.sh
)

missing=0
for p in "${required[@]}"; do
  if [ ! -e "$p" ]; then echo "FALTA: $p"; missing=$((missing + 1)); fi
done

if [ "$missing" -eq 0 ]; then echo "Estructura correcta (${#required[@]} rutas verificadas)."; else echo "Faltan $missing rutas."; exit 1; fi
