#!/usr/bin/env bash
# Configura el repositorio en GitHub: ajustes de merge, etiquetas, milestones, colaborador y protección de main.
# Requisitos: gh autenticado y ejecutado dentro del repositorio ya publicado.
# Uso: JUAN_GH=<usuario> bash scripts/setup-github.sh        (DRY_RUN=1 para simular)
set -euo pipefail
cd "$(dirname "$0")/.."

run() { if [ "${DRY_RUN:-0}" = "1" ]; then printf '[dry-run] '; printf '%q ' "$@"; echo; else "$@"; fi; }

echo "== 1. Ajustes del repositorio (solo squash merge, borrar rama al fusionar) =="
run gh repo edit --enable-squash-merge --enable-merge-commit=false --enable-rebase-merge=false --delete-branch-on-merge

echo "== 2. Etiquetas =="
bash scripts/setup-labels.sh

echo "== 3. Milestones Sprint 1 a Sprint 6 =="
for n in 1 2 3 4 5 6; do
  if [ "${DRY_RUN:-0}" != "1" ] && gh api "repos/{owner}/{repo}/milestones?state=all" --jq ".[] | select(.title==\"Sprint $n\") | .number" | grep -q .; then
    echo "Sprint $n ya existe"
  else
    run gh api "repos/{owner}/{repo}/milestones" -f title="Sprint $n" -f state=open
  fi
done

echo "== 4. Colaborador =="
if [ -n "${JUAN_GH:-}" ]; then
  run gh api -X PUT "repos/{owner}/{repo}/collaborators/${JUAN_GH}" -f permission=push
else
  echo "JUAN_GH no definido: invita a Juan manualmente (Settings > Collaborators)."
fi

echo "== 5. Protección de main (al final, tras el primer push) =="
run gh api -X PUT "repos/{owner}/{repo}/branches/main/protection" --input - <<'JSON'
{
  "required_status_checks": null,
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON

echo "Listo. A partir de ahora todo cambio a main entra por PR."
