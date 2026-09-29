# ÉPICA EP-05 — Experiencia web del cliente

[← Backlog](../README.md) · [Plan de sprints](../plan-de-sprints.md) · [Índice de la wiki](../../Home.md)

**Objetivo:** permitir demostrar visualmente la creación del pedido y la evolución eventual de sus resultados.  
**Prioridad de la épica:** P0 / P1.

## HU-501 — Crear un pedido desde Angular

**Orden:** 1  
**Prioridad:** P0  
**Sprint:** 2 · **Puntos:** 5 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero diligenciar y enviar un formulario de pedido desde la aplicación web, para iniciar el flujo FoodFlow sin utilizar herramientas técnicas externas.

**Criterios de aceptación**

1. El formulario contiene `customerReference`, `customerContact`, `notificationChannel`, `total` y un selector de "resultado de pago simulado" (`PAY-OK` o `PAY-FAIL`).
2. Se validan en cliente los campos obligatorios, el formato del email y el total mayor a cero.
3. Al enviar datos válidos se invoca `POST /orders` a través del API Gateway con un `Idempotency-Key` generado por intento; un reintento del mismo envío reutiliza la misma clave.
4. Una respuesta exitosa muestra el identificador del pedido y el estado inicial `CREADO`.
5. Los errores de validación o backend (Problem Details) se muestran sin exponer detalles internos.

## HU-502 — Consultar y visualizar el estado del pedido

**Orden:** 2  
**Prioridad:** P0  
**Sprint:** 2 · **Puntos:** 2 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero visualizar el estado actual de mi pedido, para saber si está creado, pagado o rechazado.

**Criterios de aceptación**

1. La interfaz permite consultar un pedido conocido.
2. Presenta claramente `CREADO`, `PAGADO` o `PAGO_RECHAZADO`.
3. La pantalla tolera que el estado cambie de manera eventual después de la creación.
4. Puede refrescarse sin crear un pedido nuevo ni duplicar operaciones.

## HU-503 — Visualizar el resultado del pago

**Orden:** 3  
**Prioridad:** P2  
**Sprint:** — (opcional, fuera del plan) · **Puntos:** — · **Responsable:** —  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero visualizar el resultado del pago asociado al pedido, para conocer si fue aprobado o rechazado.

**Criterios de aceptación**

1. La interfaz consulta Payment mediante el API Gateway.
2. Muestra `APROBADO` o `RECHAZADO` cuando el pago existe.
3. Si el pago aún no existe por consistencia eventual, se muestra un estado de procesamiento y no un error engañoso.
4. La vista no accede directamente a Payment Service.

> **Nota (opcional, fuera del plan de sprints):** depende de un endpoint de consulta de pagos que la API mínima no incluye. El resultado del pago se muestra mediante el estado del pedido en HU-502 y HU-505 (punto abierto A-3).

## HU-504 — Visualizar el estado de la notificación

**Orden:** 4  
**Prioridad:** P1  
**Sprint:** 4 · **Puntos:** 3 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como cliente, quiero visualizar las notificaciones de mi pedido y su estado, para conocer si FoodFlow logró comunicar el resultado.

**Criterios de aceptación**

1. La interfaz obtiene las notificaciones mediante el API Gateway.
2. Muestra como mínimo canal, contenido y estado.
3. Soporta `PENDIENTE`, `ENVIADA` y `FALLIDA`.
4. La ausencia temporal de una notificación se representa como parte normal del procesamiento eventual.

## HU-505 — Visualizar el flujo integral de un pedido

**Orden:** 5  
**Prioridad:** P1  
**Sprint:** 5 · **Puntos:** 5 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como evaluador del prototipo, quiero visualizar en una misma experiencia la evolución de pedido, pago y notificación, para comprobar claramente el comportamiento end-to-end de FoodFlow.

**Criterios de aceptación**

1. A partir de un `orderId`, la vista presenta el estado del pedido (que refleja el resultado del pago) y sus notificaciones.
2. Diferencia estados “aún no disponible” de estados fallidos.
3. Permite observar la transición eventual sin necesitar acceso a las bases de datos.
4. El flujo aprobado y el flujo rechazado pueden demostrarse de manera reproducible.

## HU-506 — Unificar la interfaz web con un diseño base

**Orden:** 6  
**Prioridad:** P1  
**Sprint:** 5 · **Puntos:** 3 · **Responsable:** Juan  
**Tipo:** Enabler (transversal a HU-501, HU-502, HU-504 y HU-505)  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como desarrollador frontend, quiero una estructura visual común (layout, navegación y estilos compartidos) tomada de una plantilla de referencia, para construir y mantener las pantallas de FoodFlow de forma consistente sin repetir estilos en cada componente.

**Criterios de aceptación**

1. Las rutas `/`, `/orders` y `/orders/:id` comparten un layout común: encabezado, navegación y área de contenido.
2. Los estilos se centralizan (colores, tipografía y espaciado definidos una sola vez); la interfaz es *responsive* y accesible (etiquetas, contraste y foco visible).
3. Las pantallas de HU-501, HU-502 y HU-504 conservan su comportamiento y su contrato REST: `npm run build` y `npm test -- --watch=false` pasan y la ruta de salida del build no cambia.
4. No se incorporan pantallas, datos ni funciones fuera del alcance (autenticación, *dashboards*, analítica, datos de ejemplo). Toda dependencia nueva se registra antes en [versiones.md](../../04-implementacion/versiones.md).
5. El README del frontend indica qué se tomó de la plantilla de referencia y bajo qué licencia.
