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
