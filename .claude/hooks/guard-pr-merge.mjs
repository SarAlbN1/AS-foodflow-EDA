// Guarda de integración: un asistente solo puede ejecutar `gh pr merge` sobre un PR que la
// OTRA persona aprobó en su versión actual. Si la HU es de Juan la aprueba Sara, y viceversa.
//
// Hook PreToolUse sobre Bash (.claude/settings.json). Recibe la llamada en stdin como JSON.
// Termina con 0 para dejarla pasar y con 2 para bloquearla; el motivo va a stderr y el
// asistente lo ve. Cualquier comando que no contenga `gh pr merge` pasa sin consultar nada.
//
// Qué es y qué no es: una BARANDILLA contra el descuido del asistente, no una frontera de
// seguridad. La barrera real sigue siendo la protección de `main` en GitHub. No persigue formas
// indirectas de nombrar el ejecutable (`$(which gh)`, una variable) ni `--repo`: quien escribe
// el comando es el asistente, y ninguna expresión regular cubre todas las formas de una shell.
//
// Reglas (docs/wiki/05-proceso/pull-requests-y-releases.md, CLAUDE.md §6 y §8):
//   1. Una sola integración por comando. Con varias, las banderas de una podrían validar a otra.
//   2. Nombra el PR por número y usa --squash, --subject y --body "" (sin cuerpo: el cuerpo
//      que propone GitHub arrastra los trailers de los commits). Esto es lo que GitHub no
//      comprueba y lo que la guarda aporta de verdad.
//   3. Nunca --admin (salta la protección de main) ni --auto (integraría sin volver a mirar).
//   4. El PR está abierto y GitHub lo marca MERGEABLE y APPROVED.
//   5. Hay al menos una aprobación de alguien distinto del autor sobre el commit actual del PR,
//      y nadie tiene pedidos cambios vigentes. GitHub ya descarta las aprobaciones viejas
//      (dismiss stale reviews); esta comprobación es defensa en profundidad por si esa
//      protección se desactiva, no una redundancia que se pueda quitar.

import { execFileSync } from 'node:child_process';

const bloquear = (motivo) => {
  process.stderr.write(`gh pr merge bloqueado: ${motivo}\n`);
  process.exit(2);
};

let entrada = '';
for await (const trozo of process.stdin) entrada += trozo;

let comando = '';
try {
  comando = JSON.parse(entrada)?.tool_input?.command ?? '';
} catch {
  process.exit(0);
}

const integraciones = comando.match(/\bgh(\.exe)?["']?\s+pr\s+merge\b/g) ?? [];
if (integraciones.length === 0) process.exit(0);
if (integraciones.length > 1) bloquear('una integración por comando: ejecútalas por separado.');

const numero = comando.match(/\bpr\s+merge\s+#?(\d+)\b/)?.[1];
if (!numero) bloquear('indica el número del PR (gh pr merge <n> ...).');
if (/\s--admin\b/.test(comando)) bloquear('--admin salta la protección de main; no se usa.');
if (/\s--auto\b/.test(comando)) bloquear('--auto integraría más tarde sin volver a comprobar; no se usa.');
if (!/\s(--squash|-s)\b/.test(comando)) bloquear('la integración es con squash (--squash).');
if (!/\s(--subject|-t)[\s=]/.test(comando)) bloquear('falta --subject con el título escrito a mano.');
if (!/\s(--body|-b)(\s+|=)(""|'')/.test(comando)) {
  bloquear('falta --body "" (CLAUDE.md §8: el cuerpo propuesto arrastra los trailers de los commits).');
}

const ghCandidatos = ['gh', 'C:\\Program Files\\GitHub CLI\\gh.exe'];
let pr;
for (const gh of ghCandidatos) {
  try {
    const salida = execFileSync(
      gh,
      ['pr', 'view', numero, '--json', 'author,state,mergeable,reviewDecision,headRefOid,reviews'],
      { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] },
    );
    pr = JSON.parse(salida);
    break;
  } catch {
    // Se prueba el siguiente candidato.
  }
}
if (!pr) bloquear(`no se pudo consultar el PR #${numero} con gh.`);

const autor = pr.author?.login;
if (pr.state !== 'OPEN') bloquear(`el PR #${numero} no está abierto (${pr.state}).`);
if (pr.mergeable !== 'MERGEABLE') {
  bloquear(`GitHub marca el PR #${numero} como ${pr.mergeable}; hay que actualizarlo primero.`);
}
if (pr.reviewDecision !== 'APPROVED') {
  bloquear(`el PR #${numero} no está aprobado (reviewDecision=${pr.reviewDecision}).`);
}

// Último estado de cada revisor, sin contar comentarios sueltos.
const ultima = new Map();
for (const r of [...(pr.reviews ?? [])].sort((a, b) => a.submittedAt.localeCompare(b.submittedAt))) {
  if (r.state === 'COMMENTED') continue;
  ultima.set(r.author?.login, r);
}

const cambiosPedidos = [...ultima.values()].filter((r) => r.state === 'CHANGES_REQUESTED');
if (cambiosPedidos.length > 0) {
  bloquear(`hay cambios pedidos por ${cambiosPedidos.map((r) => r.author?.login).join(', ')}.`);
}

const aprobacionValida = [...ultima.values()].some(
  (r) => r.state === 'APPROVED' && r.author?.login !== autor && r.commit?.oid === pr.headRefOid,
);
if (!aprobacionValida) {
  bloquear(
    `el PR #${numero} (autor ${autor}) necesita la aprobación de la otra persona sobre su commit ` +
      `actual ${pr.headRefOid.slice(0, 7)}; una aprobación anterior o propia no cuenta.`,
  );
}

process.exit(0);
