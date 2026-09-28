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

## Asistencia de IA
- [ ] Este PR tuvo contribución sustancial de un asistente de IA (añadir `ai-assisted`)
```

Reglas: el autor no aprueba su propio PR (lo revisa la otra persona); el PR es pequeño y corresponde a una sola HU; el título sigue el formato de commit.

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
