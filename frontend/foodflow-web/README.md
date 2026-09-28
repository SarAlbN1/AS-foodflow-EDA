# frontend/foodflow-web — Aplicación Angular

> **Estado:** HU-501 en revisión: formulario para crear un pedido a través del API Gateway. Consulta del estado (HU-502), notificaciones (HU-504) y flujo integral (HU-505) los añaden sus historias.

**Responsabilidad:** Interfaz para crear pedidos y consultar su estado y sus notificaciones. Se publica con Nginx. Solo consume APIs REST del API Gateway.

**Historias que lo construyen:** HU-001 (esqueleto), HU-501, HU-502, HU-504, HU-505

**Reglas que aplican:** Nunca accede a Kafka ni a PostgreSQL; sin URLs internas de los servicios.

## Construir, ejecutar y probar

Requisitos: Node.js 24.21.0 (`.nvmrc`) y npm. Angular 22.2.0, fijado de forma exacta en `package.json`. Versiones en [versiones.md](../../docs/wiki/04-implementacion/versiones.md).

Desde `frontend/foodflow-web/`:

| Acción | Comando |
|---|---|
| Instalar dependencias (primera vez) | `npm ci` |
| Compilar | `npm run build` (salida en `dist/foodflow-web/`) |
| Pruebas unitarias (Vitest) | `npm test -- --watch=false` |
| Ejecutar en desarrollo | `npm start` y abrir http://localhost:4200/ |
| Detener | `Ctrl+C` en la terminal |

## Conexión con el API Gateway

La aplicación solo habla con el API Gateway. Su URL es el token `API_BASE_URL` (`src/app/core/api-config.ts`), `http://localhost:8080` por omisión. En desarrollo, el gateway debe admitir el origen `http://localhost:4200` por CORS (`GATEWAY_CORS_ALLOWED_ORIGINS`, HU-403). La publicación con Nginx se añade en HU-607.

## Crear un pedido (HU-501)

- Formulario con `customerReference`, `customerContact`, `notificationChannel` (solo `EMAIL`), `total` y el resultado de pago simulado (`PAY-OK` / `PAY-FAIL`). Valida en el cliente las mismas reglas que Order Service: obligatorios, correo con dominio y total mayor que cero con dos decimales como máximo.
- Cada intento de envío lleva una `Idempotency-Key`. Reintentar **el mismo envío** tras un fallo de red o un `503` reutiliza la clave; cambiar los datos o crear otro pedido genera una nueva. Un segundo clic mientras se envía no produce otra solicitud.
- Los errores en Problem Details se muestran con un texto para la persona y su `correlationId` como referencia. Solo se muestra el `detail` de un `VALIDATION_ERROR` (la lista de campos rechazados); nunca trazas ni datos internos.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
