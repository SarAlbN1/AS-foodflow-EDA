# ÉPICA EP-06 — Resiliencia, observabilidad y calidad

[← Backlog](../README.md) · [Plan de sprints](../plan-de-sprints.md) · [Índice de la wiki](../../Home.md)

**Objetivo:** garantizar que el prototipo demuestre tolerancia a fallos, idempotencia, trazabilidad, recuperación parcial y atributos de calidad verificables.  
**Prioridad de la épica:** P0 / P1.

## HU-011 — Pruebas de integración con Testcontainers (opcional)

**Orden:** 9  
**Prioridad:** P2  
**Sprint:** — (opcional, fuera del plan) · **Puntos:** — · **Responsable:** Juan  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo de desarrollo, quiero ejecutar pruebas de integración contra infraestructura real y efímera, para validar consumidores y persistencia con mayor fidelidad.

**Criterios de aceptación**

1. Las pruebas de HU-605 pueden ejecutarse con Testcontainers.
2. Kafka y PostgreSQL efímeros se crean y destruyen por ejecución.
3. Su ausencia no impide dar por terminado el prototipo.

## HU-601 — Hacer idempotentes los consumidores Kafka

**Orden:** 1  
**Prioridad:** P0  
**Sprint:** 5 · **Puntos:** 5 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como operador del sistema, quiero que los consumidores toleren eventos duplicados, para evitar pagos, cambios de estado o notificaciones duplicadas ante reintentos del broker.

**Criterios de aceptación**

1. Payment, Order y Notification implementan ADR-09: tabla `processed_events` propia con `eventId` único.
2. El registro del `eventId` y el efecto de negocio se escriben en la **misma transacción local**.
3. El offset se confirma solo después del commit local; un evento ya procesado se ignora con log `INFO` y se confirma su offset.
4. Existen pruebas automáticas que entregan al menos dos veces el mismo evento a cada consumidor.
5. El resultado final es equivalente a haber procesado el evento una sola vez.

## HU-602 — Aplicar reintentos y DLQ a eventos fallidos

**Orden:** 2  
**Prioridad:** P0  
**Sprint:** 5 · **Puntos:** 5 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como operador del sistema, quiero reintentar errores transitorios y aislar eventos definitivamente fallidos en una DLQ, para que un mensaje problemático no bloquee el flujo completo.

**Criterios de aceptación**

1. Los consumidores tienen un número de reintentos finito y configurable.
2. Los errores recuperables se reintentan antes de enviarse a DLQ.
3. Al agotar los reintentos, el evento termina en una DLQ identificable.
4. El resto de eventos continúa procesándose.
5. La documentación explica cómo inspeccionar una DLQ durante la demostración.
6. Los tópicos DLQ siguen la convención `<tópico>.dlq` y los fallos de negocio del proveedor (`FALLIDA`) no se envían a DLQ (página [Comportamiento del flujo](../../02-arquitectura/comportamiento-del-flujo.md)).

## HU-603 — Implementar logs estructurados y correlacionados

**Orden:** 3  
**Prioridad:** P1  
**Sprint:** 5 · **Puntos:** 3 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como desarrollador, quiero consultar logs estructurados con `correlationId`, `eventId` y la entidad relacionada, para reconstruir el recorrido de un pedido a través de los servicios.

**Criterios de aceptación**

1. Los logs de Order, Payment y Notification son JSON e incluyen `timestamp`, `level`, `service`, `correlationId`, `orderId` y `message`.
2. Los consumidores registran además `eventId` y `eventType`.
3. Los logs no exponen contraseñas, secretos ni el contacto completo del cliente (se enmascara).
4. Con un `correlationId` es posible encontrar el recorrido del pedido en los tres servicios.
5. No se implementa tracing distribuido: esta es la trazabilidad mínima del prototipo.

## HU-604 — Exponer health checks de componentes

**Orden:** 4  
**Prioridad:** P1  
**Sprint:** 2 · **Puntos:** 2 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como operador del prototipo, quiero conocer el estado de los servicios y sus dependencias esenciales, para detectar rápidamente componentes no disponibles durante la ejecución.

**Criterios de aceptación**

1. Cada servicio Spring Boot expone un health endpoint mediante Actuator o mecanismo equivalente.
2. El estado distingue una aplicación iniciada de una dependencia esencial no disponible cuando sea posible.
3. Los health endpoints no revelan credenciales.
4. Compose puede utilizar los health checks para validar disponibilidad básica.

## HU-605 — Automatizar pruebas de integración del flujo Kafka

**Orden:** 5  
**Prioridad:** P1  
**Sprint:** 5 · **Puntos:** 5 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como equipo de desarrollo, quiero ejecutar pruebas automáticas sobre productores, consumidores y persistencia, para detectar rupturas de contratos y del flujo EDA antes de una demostración.

**Criterios de aceptación**

1. Existe una prueba que valida `OrderCreated -> Payment`.
2. Existe una prueba que valida `PaymentApproved/Rejected -> Order`.
3. Existe una prueba que valida `PaymentApproved/Rejected -> Notification`.
4. Las pruebas verifican persistencia y evento resultante, no solo que el método haya sido invocado.
5. Las pruebas pueden ejecutarse desde un entorno documentado y reproducible.
6. La herramienta es libre (Testcontainers, Kafka embebido o el entorno Compose) siempre que sea reproducible; Testcontainers es opcional (HU-011).

## HU-606 — Validar el flujo end-to-end contenerizado

**Orden:** 6  
**Prioridad:** P0  
**Sprint:** 6 · **Puntos:** 8 · **Responsable:** Sara  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como evaluador del prototipo, quiero ejecutar FoodFlow completamente contenerizado y comprobar un flujo aprobado y uno rechazado, para verificar que la arquitectura implementada corresponde con los diagramas y la descripción técnica.

**Criterios de aceptación**

1. Un comando documentado levanta frontend, gateway, servicios, Kafka, bases de datos y mock externo necesario.
2. Desde la UI puede crearse un pedido y observarse su evolución.
3. Existe un escenario reproducible con `PAY-OK` que termina en `PAGADO` + `APROBADO` + `ENVIADA`.
4. Existe un escenario reproducible con `PAY-FAIL` que termina en `PAGO_RECHAZADO` + `RECHAZADO` y genera su notificación correspondiente.
5. Es posible evidenciar los eventos publicados en los tres tópicos principales.
6. Ningún paso del flujo requiere modificar manualmente registros de base de datos.

## HU-607 — Scripts de arranque, parada y prueba de humo

**Orden:** 7  
**Prioridad:** P0  
**Sprint:** 5 · **Puntos:** 2 · **Responsable:** Juan  
**Tipo:** Enabler  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como evaluador del prototipo, quiero levantar, detener y probar FoodFlow con un solo comando cada vez, para reproducir la demostración sin pasos manuales.

**Criterios de aceptación**

1. `scripts/up.sh` levanta frontend, gateway, servicios, Kafka, bases y el mock desde un clon limpio.
2. `scripts/down.sh` detiene el entorno y permite recrearlo sin pasos no documentados.
3. `scripts/smoke-test.sh` ejecuta el flujo con `PAY-OK` y con `PAY-FAIL` y falla si el resultado no es el esperado.
4. Los tres scripts están documentados en el README.

## HU-608 — Verificar los atributos de calidad

**Orden:** 8  
**Prioridad:** P0  
**Sprint:** 6 · **Puntos:** 5 · **Responsable:** Juan  
**INVEST:** I✅ N✅ V✅ E✅ S✅ T✅

**Historia**  
Como evaluador del prototipo, quiero pruebas que comprueben los atributos de calidad definidos, para verificar que la arquitectura cumple lo que el documento afirma.

**Criterios de aceptación**

1. Existe una prueba, script o guion reproducible por cada criterio de la página [Atributos de calidad verificables](../../02-arquitectura/atributos-de-calidad.md).
2. Los umbrales numéricos propuestos se calibran con mediciones reales y se registran en `docs/wiki/04-implementacion/pruebas/`.
3. La prueba de replay demuestra que la recuperabilidad es **Parcial** (ADR-08).
4. Los resultados (fecha, entorno, valores medidos) se documentan.
5. Las pruebas no requieren herramientas opcionales de la página [Visión y alcance](../../01-producto/vision-y-alcance.md).
