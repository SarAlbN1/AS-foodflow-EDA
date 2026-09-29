// Guarda de integración: un asistente solo puede ejecutar `gh pr merge` sobre un PR que la
// OTRA persona aprobó en su versión actual. Si la HU es de Juan la aprueba Sara, y viceversa.
//
// Hook PreToolUse sobre Bash (.claude/settings.json). Recibe la llamada en stdin como JSON.
// Termina con 0 para dejarla pasar y con 2 para bloquearla; el motivo va a stderr y el
// asistente lo ve. Cualquier comando que no contenga `gh pr merge` pasa sin consultar nada.
//
// Qué es y qué no es: una BARANDILLA contra el descuido del asistente, no una frontera de
// seguridad. Reconoce `gh`/`gh.exe` escritos en el comando; no persigue formas indirectas de
// nombrar el ejecutable (`$(which gh)`, una variable), porque quien escribe el comando es el
// asistente y ninguna expresión regular cubre todas las formas de una shell. La barrera real
// sigue siendo la protección de `main` en GitHub.
//
// Cada `gh pr merge` del comando se valida por separado (`a && b; c` son tres comandos), con
// estas reglas (docs/wiki/05-proceso/pull-requests-y-releases.md, CLAUDE.md §6 y §8):
//   1. Nombra el PR por número y usa --squash, --subject y --body "" (sin cuerpo: el cuerpo
//      que propone GitHub arrastra los trailers de los commits). Esto es lo que GitHub no
//      comprueba y lo que la guarda aporta de verdad.
//   2. Nunca --admin (salta la protección de main) ni --auto (integraría sin volver a mirar).
//   3. El PR está abierto y GitHub lo marca MERGEABLE y APPROVED.
//   4. Hay al menos una aprobación de alguien distinto del autor sobre el commit actual del PR,
//      y nadie tiene pedidos cambios vigentes. GitHub ya descarta las aprobaciones viejas
//      (dismiss stale reviews); esta comprobación es defensa en profundidad por si esa
//      protección se desactiva, no una redundancia que se pueda quitar.
//   Si el comando lleva --repo/-R, el PR se consulta en ese repositorio.

import { execFileSync } from 'node:child_process';

const INVOCACION = /\bgh(\.exe)?["']?\s+pr\s+merge\b/;

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

if (!INVOCACION.test(comando)) process.exit(0);

// Un comando de shell puede encadenar varios. Se valida cada tramo que integra, no solo el
// primero: si no, `gh pr merge 1 ... && gh pr merge 2 --merge` heredaría la validación del 1.
// Un separador dentro de un --subject entrecomillado parte el tramo; el resto sin sus banderas
// queda bloqueado, así que el error es por exceso de celo, nunca por dejar pasar.
const tramos = comando.split(/&&|\|\||;|\||\r?\n/).filter((tramo) => INVOCACION.test(tramo));

const ghCandidatos = ['gh', 'C:\\Program Files\\GitHub CLI\\gh.exe'];

function consultarPr(numero, repositorio) {
  const argumentos = ['pr', 'view', numero, '--json', 'author,state,mergeable,reviewDecision,headRefOid,reviews'];
  if (repositorio) argumentos.push('--repo', repositorio);
  for (const gh of ghCandidatos) {
    try {
      const salida = execFileSync(gh, argumentos, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
      return JSON.parse(salida);
    } catch {
      // Se prueba el siguiente candidato.
    }
  }
  return null;
}

function validar(tramo) {
  const numero = tramo.match(/\bpr\s+merge\s+#?(\d+)\b/)?.[1];
  if (!numero) bloquear('indica el número del PR (gh pr merge <n> ...).');
  const pr = `#${numero}`;
  if (/\s--admin\b/.test(tramo)) bloquear(`${pr}: --admin salta la protección de main; no se usa.`);
  if (/\s--auto\b/.test(tramo)) bloquear(`${pr}: --auto integraría más tarde sin volver a comprobar; no se usa.`);
  if (!/\s(--squash|-s)\b/.test(tramo)) bloquear(`${pr}: la integración es con squash (--squash).`);
  if (!/\s(--subject|-t)[\s=]/.test(tramo)) bloquear(`${pr}: falta --subject con el título escrito a mano.`);
  if (!/\s(--body|-b)(\s+|=)(""|'')/.test(tramo)) {
    bloquear(`${pr}: falta --body "" (CLAUDE.md §8: el cuerpo propuesto arrastra los trailers de los commits).`);
  }

  const repositorio = tramo.match(/\s(?:--repo|-R)(?:\s+|=)["']?([^\s"']+)/)?.[1];
  const datos = consultarPr(numero, repositorio);
  if (!datos) bloquear(`no se pudo consultar el PR ${pr} con gh.`);

  const autor = datos.author?.login;
  if (datos.state !== 'OPEN') bloquear(`el PR ${pr} no está abierto (${datos.state}).`);
  if (datos.mergeable !== 'MERGEABLE') {
    bloquear(`GitHub marca el PR ${pr} como ${datos.mergeable}; hay que actualizarlo primero.`);
  }
  if (datos.reviewDecision !== 'APPROVED') {
    bloquear(`el PR ${pr} no está aprobado (reviewDecision=${datos.reviewDecision}).`);
  }

  // Último estado de cada revisor, sin contar comentarios sueltos.
  const ultima = new Map();
  for (const r of [...(datos.reviews ?? [])].sort((a, b) => a.submittedAt.localeCompare(b.submittedAt))) {
    if (r.state === 'COMMENTED') continue;
    ultima.set(r.author?.login, r);
  }

  const cambiosPedidos = [...ultima.values()].filter((r) => r.state === 'CHANGES_REQUESTED');
  if (cambiosPedidos.length > 0) {
    bloquear(`${pr}: hay cambios pedidos por ${cambiosPedidos.map((r) => r.author?.login).join(', ')}.`);
  }

  const aprobacionValida = [...ultima.values()].some(
    (r) => r.state === 'APPROVED' && r.author?.login !== autor && r.commit?.oid === datos.headRefOid,
  );
  if (!aprobacionValida) {
    bloquear(
      `el PR ${pr} (autor ${autor}) necesita la aprobación de la otra persona sobre su commit ` +
        `actual ${datos.headRefOid.slice(0, 7)}; una aprobación anterior o propia no cuenta.`,
    );
  }
}

for (const tramo of tramos) validar(tramo);
process.exit(0);
