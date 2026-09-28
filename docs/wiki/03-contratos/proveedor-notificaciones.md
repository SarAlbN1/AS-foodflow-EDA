# Proveedor de notificaciones (mock)

[← Índice de la wiki](../Home.md)

## Proveedor de notificaciones (mock)

`POST /v1/messages` con `{channel, destination, content, correlationId}`. Responde `202` con `{providerReference}`. Para demostrar fallos sin tocar código, el comportamiento depende del destino:

| Destino | Comportamiento |
|---|---|
| Email normal | Éxito |
| `*@fail.test` | `503` siempre |
| `*@flaky.test` | Falla los 2 primeros intentos por destino y luego responde éxito |
| `*@slow.test` | Responde después del tiempo de lectura (prueba de timeout) |

Timeouts: conexión 2 s, lectura 3 s. Reintentos: 3 intentos con espera de 500 ms que se duplica. No hay Circuit Breaker.
