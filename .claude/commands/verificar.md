---
description: Ejecuta pruebas y el checklist arquitectónico sobre los cambios actuales
allowed-tools: Read, Glob, Grep, Bash
---

# Verificar los cambios actuales

No modifiques código. Solo verifica e informa.

## 1. Alcance

```bash
git status --short
git diff --stat main...HEAD
```

Identifica la HU a la que corresponde el cambio (rama o commits) y lee sus criterios de aceptación en su épica de `docs/wiki/06-backlog/epicas/`.

## 2. Estructura y arquitectura

```bash
bash scripts/check-structure.sh
bash scripts/verify-architecture.sh   # si existe; si no, avísalo y no lo des por cumplido
```

## 3. Pruebas

Ejecuta las pruebas de cada componente tocado y pega el resultado real. Si un comando falla, repórtalo tal cual; no lo maquilles.

## 4. Checklist arquitectónico (revisión manual del diff)

Para cada punto, responde **sí / no / no aplica** con la evidencia (`archivo:línea`):

1. Angular no accede a Kafka ni a PostgreSQL; solo REST al gateway.
2. Cada servicio accede únicamente a su propia base; en Compose solo recibe la URL de su base.
3. Ninguna base publica o consume eventos.
4. Sin llamadas REST entre servicios para coordinar el flujo; sin orquestador.
5. El cliente solo crea el pedido; `OrderCreated` dispara el pago en Payment Service.
6. Order y Notification reaccionan de forma independiente al resultado del pago.
7. Solo Notification Service llama al proveedor externo.
8. Sin dependencias de código, tablas, repositorios JPA ni clases de dominio compartidas entre servicios.
9. Consumidores idempotentes (`eventId` + `processed_events` en la misma transacción local).
10. Reintentos controlados y DLQ; un fallo de negocio del proveedor deja la notificación en `FALLIDA` y **no** va a DLQ.
11. Eventos inmutables con el envelope común y `aggregateId = orderId` como clave de partición.
12. Un fallo de Notification Service no impide que Order registre el resultado del pago.

Además:

- `@KafkaListener` y `KafkaTemplate` solo en `infrastructure.messaging`.
- Tópicos por configuración, sin literales dispersos.
- Sin secretos versionados ni contacto completo en logs (debe ir enmascarado).
- Nada de la lista "No implementar" de `CLAUDE.md`; no se usa `OrderUpdated`.
- Versiones tomadas de `docs/wiki/04-implementacion/versiones.md`.
- Contratos de `contracts/` y OpenAPI coherentes con el cambio.

## 5. Contradicciones entre fuentes

Si una fuente contradice a otra, aplica la jerarquía de `CLAUDE.md` (§1), **repórtalo** y propón la corrección. No lo resuelvas en silencio.

## 6. Salida

```markdown
## Verificación — <rama>

| Comprobación | Resultado | Evidencia |
|---|---|---|

### Criterios de aceptación
- [x] CA1 … (verificado con …)
- [ ] CA2 … (NO verificado: motivo)

### Bloqueantes
### Recomendaciones
```

Marca un criterio como cumplido solo si lo verificaste. Si no pudiste, dilo.
