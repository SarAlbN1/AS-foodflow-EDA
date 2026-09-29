# Pull requests, protección y releases

[← Índice de la wiki](../Home.md)

## Pull Requests

Todo PR usa `.github/pull_request_template.md`:

```markdown
## HU
Closes #<n> — HU-###

## Qué cambia
<resumen breve>

## Criterios de aceptación
- [ ] CA1 ...
- [ ] CA2 ...

## Pruebas ejecutadas
- `comando` -> resultado

## Verificación arquitectónica
- [ ] Sin acceso a bases de otros servicios ni dependencias entre servicios
- [ ] Sin llamadas REST entre servicios para coordinar el flujo
- [ ] Eventos y contratos actualizados en `contracts/`
- [ ] OpenAPI actualizado si cambió el API
- [ ] Sin secretos ni contacto completo en logs
- [ ] Nada de lo listado como "no implementar" (página [Visión y alcance](../01-producto/vision-y-alcance.md))

## Documentación
- [ ] README, ADR o `docs/wiki/06-backlog/estado.md` actualizados cuando aplica
```

Reglas: el autor no aprueba su propio PR (lo revisa la otra persona); el PR es pequeño y corresponde a una sola HU; el título sigue el formato de commit.

## La traza no nombra herramientas de IA

El repositorio es el entregable. Se evalúa el trabajo del equipo, así que **ninguna marca de herramienta de IA aparece en commits, PR, issues, releases, wiki, informe ni comentarios de código**. La regla completa está en la sección 8 de [`CLAUDE.md`](../../../CLAUDE.md).

| Dónde | Qué no va | Qué sí |
|---|---|---|
| Mensaje de commit | `Co-Authored-By: <herramienta>`, `Generated with…` | Solo el autor del commit |
| Descripción del PR | `🤖 Generated with…`, cualquier nombre comercial | Nada; la plantilla no lo pide |
| Comentario de revisión | Nombres comerciales | «lo verifiqué», «lo comprobé» |
| Wiki, informe, README | `Claude`, `Copilot`, `ChatGPT`… | «un asistente de IA» |

**Únicas excepciones:** `CLAUDE.md` y `.claude/` conservan su nombre porque son configuración localizada por ruta. Referirse a ellos es citar un archivo, no nombrar una herramienta.

**Se comprueba al revisar.** Una firma de herramienta en un commit o en una descripción es motivo de pedir cambios, igual que cualquier otro incumplimiento del proceso.

**Al integrar con squash, el mensaje se escribe a mano.** GitHub propone por defecto un cuerpo con los mensajes de todos los commits del PR, *trailers* incluidos. Si se acepta tal cual, una firma que estaba en una rama entra igualmente en `main`. Antes de pulsar el botón: título escrito a mano con el formato de commit del proyecto y **cuerpo vacío**, o el texto que se quiera conservar sin firmas.

> **Nota sobre el historial.** Los commits ya integrados en `main` antes de esta regla conservan su firma. No se reescribe el historial: rompería las referencias de todos los PR y los enlaces de la wiki. La regla aplica de aquí en adelante.

## Propiedad del trabajo entre agentes

Cada persona trabaja con su propio asistente, y **cada quien corrige lo suyo**. La regla completa está en la sección 7 de [`CLAUDE.md`](../../../CLAUDE.md); aquí queda cómo se aplica a la revisión.

| Situación | Qué se hace | Qué **no** se hace |
|---|---|---|
| Encuentras un defecto en el PR de la otra persona | Comentar con la evidencia y pedir cambios | Arreglarlo tú y subirlo a su rama |
| Tu cambio necesita una línea en un archivo que comparte con una HU ajena | Ponerla tú, en tu rama | Dejar escrito «cuando integres esto, añade tal línea» |
| Un *merge* rompe algo que pertenece a la otra persona | Detener la integración y reportarlo en su PR o en un issue | Repararlo al vuelo en el *merge* |
| Te piden cambios en tu PR | Corregir en tu misma rama y responder con la evidencia | Cerrar el PR y abrir otro |

**Revisar sí cruza; escribir no.** Revisar, comentar, aprobar o pedir cambios en el PR de la otra persona es obligatorio. Editar sus archivos, no.

**Qué se considera «suyo».** El responsable de cada HU está en [`estado.md`](../06-backlog/estado.md). Si un archivo lo tocan dos HU de personas distintas, cada una hace su parte en su propia rama y los conflictos los resuelve quien integre en segundo lugar, **solo sobre sus propias líneas**.

**Al pedir cambios, la evidencia manda.** Un hallazgo se reporta con lo que se ejecutó y lo que salió, no con una sospecha. Y quien corrige demuestra que la corrección funciona: lo habitual es enseñar que la prueba nueva **falla** sin el arreglo.

## Integración con aprobación cruzada

Un PR se integra solo cuando **la otra persona** lo aprobó en su versión actual: los PR de Juan los aprueba Sara y los de Sara los aprueba Juan. Nadie integra sobre su propia aprobación ni sobre una aprobación anterior al último *push*.

El asistente puede ejecutar `gh pr merge` porque una guarda lo revisa antes de cada ejecución: [`.claude/hooks/guard-pr-merge.mjs`](../../../.claude/hooks/guard-pr-merge.mjs), registrada como *hook* `PreToolUse` en `.claude/settings.json`. **Es una barandilla contra el descuido, no una frontera de seguridad:** la barrera real sigue siendo la protección de `main` en GitHub.

**Qué aporta y qué ya hacía GitHub.**

| Condición que bloquea | ¿Lo cubre ya GitHub? |
|---|---|
| Más de una integración en el mismo comando | **No.** Una por comando: con varias, las banderas de una podrían validar a otra |
| Falta `--squash`, `--subject` o `--body ""` | **No.** Es lo que la guarda aporta: sin `--body ""`, el *squash* se integra con el cuerpo que propone GitHub, que arrastra los mensajes y los *trailers* de los commits ([arriba](#la-traza-no-nombra-herramientas-de-ia)) |
| Usa `--admin` o `--auto` | **No.** `--admin` salta la protección de `main`; `--auto` integraría más tarde sin volver a comprobar |
| El PR no está abierto, no es `MERGEABLE` o no está `APPROVED` | Sí; la guarda lo adelanta con un mensaje claro |
| Alguien tiene cambios pedidos vigentes | Sí |
| No hay una aprobación de alguien distinto del autor **sobre el commit actual** | Sí, con *dismiss stale reviews*. En la guarda es **defensa en profundidad**: si esa protección se desactivara, la regla seguiría en pie. No es una redundancia que se pueda quitar |

**Lo que no persigue.** Reconoce `gh` y `gh.exe` escritos en el comando, no formas indirectas de nombrar el ejecutable (`$(which gh)`, una variable), ni interpreta `--repo`: quien escribe el comando es el asistente, y ninguna expresión regular cubre todas las formas de una shell. Perseguirlo daría una guarda complicada que aparenta una garantía que no tiene.

Forma del comando: `gh pr merge <n> --squash --subject "<tipo>(<ámbito>): <descripción> [HU-###] (#<n>)" --body ""`. La guarda necesita `node` (ya instalado para el frontend) y `gh` autenticado.

## Protección de `main`, tags y releases

**Protección de `main`** (Settings, Branches): PR obligatorio, 1 aprobación, resolución de conversaciones, historial lineal (squash), sin force-push ni borrado. Los *status checks* obligatorios solo se activan si se adopta CI (opcional).

**Versionado (SemVer).**

| Momento | Tag | Tipo |
|---|---|---|
| Cierre de cada sprint | `v0.<N>.0` (por ejemplo `v0.3.0`) | Pre-release |
| Entrega final | `v1.0.0` | Release (**obligatorio**) |

```bash
git tag -a v1.0.0 -m "FoodFlow v1.0.0: prototipo EDA completo"
git push origin v1.0.0
```

**Notas de release:** HU incluidas, cómo ejecutar (`scripts/up.sh`), escenarios de demo (`PAY-OK` y `PAY-FAIL`), limitaciones conocidas (ADR-08: sin Outbox) y verificación de los criterios de éxito de la página [Criterios de éxito](../01-producto/criterios-de-exito.md).
