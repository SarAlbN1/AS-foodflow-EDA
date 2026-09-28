#!/usr/bin/env bash
# Crea los issues de un sprint (o de todos) a partir de docs/wiki/06-backlog/backlog.tsv
# Uso: SARA_GH=<usuario> JUAN_GH=<usuario> bash scripts/create-issues.sh [1..6|all]     (DRY_RUN=1 para simular)
# Requisito: las etiquetas y los milestones ya existen (scripts/setup-github.sh).
set -euo pipefail
cd "$(dirname "$0")/.."

SPRINT="${1:-1}"
TSV="docs/wiki/06-backlog/backlog.tsv"

run() { if [ "${DRY_RUN:-0}" = "1" ]; then printf '[dry-run] '; printf '%q ' "$@"; echo; else "$@"; fi; }
assignee_of() { case "$1" in Sara) echo "${SARA_GH:-}" ;; Juan) echo "${JUAN_GH:-}" ;; *) echo "" ;; esac; }

count=0
while IFS=$'\t' read -r hu epic title prio sprint points owner labels branch file; do
  if [ "$SPRINT" != "all" ] && [ "$sprint" != "$SPRINT" ]; then continue; fi

  full_title="$hu — $title"

  if [ "${DRY_RUN:-0}" != "1" ]; then
    existing="$(gh issue list --state all --search "\"$hu —\" in:title" --json number --jq 'length')"
    if [ "$existing" != "0" ]; then echo "Ya existe: $full_title"; continue; fi
    body="$(awk -v id="$hu" '$0 ~ "^## " id " —" {p=1; next} p && /^## HU-/ {exit} p' "$file" | sed -E 's/\[([^]]*)\]\([^)]*\.md[^)]*\)/\1/g')"
    body="> Fuente de verdad: \`$file\`
> Rama sugerida: \`$branch\`
> Puntos: $points · Responsable: $owner

$body"
  else
    body="(cuerpo omitido en dry-run)"
  fi

  args=(gh issue create --title "$full_title" --body "$body" --label "$labels")
  if [ "$sprint" != "-" ]; then args+=(--milestone "Sprint $sprint"); fi
  a="$(assignee_of "$owner")"
  if [ -n "$a" ]; then args+=(--assignee "$a"); fi

  run "${args[@]}"
  count=$((count + 1))
done < <(tail -n +2 "$TSV")

echo "Issues procesados: $count (sprint: $SPRINT)"
