# frontend/foodflow-web — Aplicación Angular

> **Estado:** crear un pedido (HU-501), consultar su estado (HU-502) y ver sus notificaciones (HU-504, en revisión), siempre a través del API Gateway. El flujo integral lo añade HU-505.

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

## Consultar el estado del pedido (HU-502)

| Ruta | Pantalla |
|---|---|
| `/` | Crear un pedido; el resultado enlaza a su estado |
| `/orders` | Consultar un pedido conocido por su identificador |
| `/orders/:id` | Estado del pedido: `CREADO`, `PAGADO` o `PAGO_RECHAZADO` |

La consistencia es eventual: mientras el pedido está en `CREADO`, la pantalla vuelve a consultar `GET /orders/{id}` cada segundo durante 30 s como máximo (la meta es converger en menos de 5 s, [atributos de calidad](../../docs/wiki/02-arquitectura/atributos-de-calidad.md)) y se detiene al ver un estado final. Después queda el botón **Actualizar**. Toda consulta es `GET`: refrescar nunca crea un pedido ni repite una operación.

## Notificaciones del pedido (HU-504)

La pantalla `/orders/:id` incluye la sección **Notificaciones**, que consulta `GET /orders/{id}/notifications` a través del gateway (HU-402). Cada notificación muestra su **estado** (`PENDIENTE` «Enviando», `ENVIADA` «Enviada», `FALLIDA` «No se pudo enviar», con su motivo), el **contenido** enviado al cliente, el canal, el destino, los intentos y la última actualización.

Que todavía no haya notificaciones es normal: se crean cuando Notification Service conoce el resultado del pago. Mientras no haya ninguna, o alguna siga `PENDIENTE`, la sección se vuelve a consultar cada segundo durante 30 s como máximo; después queda el botón **Actualizar notificaciones**.

## Diseño base y referencia visual (HU-506)

El diseño visual se adaptó tomando como referencia la plantilla **Modernize Angular Free** (`modernize-angular-free-main`), bajo su licencia original MIT (Copyright (c) 2025 AdminMart).

- **Lo tomado de la plantilla:**
  - Estructura general de layout: barra superior (header), menú de navegación lateral (sidebar) responsivo y área de contenido principal.
  - Paleta de colores principal (`#5d87ff` azul primario, `#49beff` azul secundario, `#13deb9` verde éxito, `#ffa21d` amarillo advertencia/proceso, `#fa896b` rojo error/rechazo).
  - Tipografía base `Plus Jakarta Sans` (SIL Open Font License 1.1), alojada en `public/fonts/` para que la demo funcione sin internet; radios de borde de tarjetas (`12px`), bordes de controles de formulario (`8px`) y sombras suaves (`0 4px 20px rgba(0,0,0,0.05)`).
  - Estilos de badges/pills de estado y botones con foco visible.
  - Iconografía vectorial SVG limpia para navegación y estados.

- **Lo descartado:**
  - No se copiaron dependencias de `package.json` ni archivos fuente completos de la plantilla.
  - Se descartaron pantallas de autenticación, registro, gráficos de analítica (ApexCharts), tableros backoffice y datos falsos de ejemplo.
  - Se implementaron los estilos mediante CSS nativo sin instalar librerías pesadas.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)

