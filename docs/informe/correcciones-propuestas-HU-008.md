# Correcciones propuestas a `main.tex` — HU-008

`main.tex` es la fuente de verdad del diseño y **solo Sara lo edita**: esta página propone los cambios, no los aplica ([`CLAUDE.md`](../../CLAUDE.md) §1).

Revisado el **2026-09-28** contra `main.tex` de 1043 líneas. Las líneas corresponden a esa versión; si el documento se edita, conviene localizar el texto en lugar de confiar en el número.

## Estado de los criterios de HU-008 frente a esta versión del informe

| Criterio | Estado | Evidencia |
|---|---|---|
| CA1 — El Cliente solo crea el pedido; el pago se inicia al consumir `OrderCreated` | **Cumple** | Flujo end-to-end (paso 5), HLD (línea 725: «Payment Service no recibe una orden de pago directa del Cliente: reacciona a `OrderCreated`») y ADR-07 |
| CA2 — Portada y §1 declaran REST/JSON sobre HTTPS y eventos JSON sobre Kafka; REST y API Gateway investigados con el mismo formato | **Cumple** | Portada: «Protocolo de integración en el borde: REST/JSON sobre HTTPS» y «Integración asíncrona interna: Apache Kafka + eventos JSON». §1.3 «Protocolo de Integración: REST/JSON sobre HTTPS y API Gateway» (línea 319) |
| CA3 — `OrderStatusChanged` reemplaza a `OrderUpdated` | **Cumple** | `grep -c OrderUpdated main.tex` → `0`; los diagramas C2, C3 y dinámico usan `OrderStatusChanged` |
| CA4 — La matriz de tácticas incorpora ADR-08 a ADR-11 y la recuperabilidad figura como **Parcial** | **Cumple** | **Aplicado**: la recuperabilidad dice `\textbf{Parcial}` y una frase de cierre conecta la matriz con los ADR de §3 |
| CA5 — La matriz de calidad incluye la columna de criterio verificable | **Cumple** | Columna «Indicadores o criterios de verificación» (línea 371) y §3 «Criterios de verificación del prototipo» (línea 658) |
| CA6 — Backoffice y Analítica fuera de alcance y con relaciones punteadas; «único actor» limitado al prototipo | **Cumple** | §4 System Landscape (línea 840): «sistemas de contexto fuera del alcance de implementación del prototipo»; el diagrama los dibuja punteados |
| CA7 — Las referencias definidas y no citadas se citan o se eliminan | **Cumple** | **Aplicado**: `spring-kafka-retry` se cita en el párrafo de robustez mínima |

No se pudo comprobar que el documento **compila**: en esta máquina no hay ningún motor LaTeX instalado (`pdflatex`, `xelatex`, `lualatex`, `latexmk` y `tectonic` no existen). Ninguna afirmación de este archivo implica que el PDF se genere sin errores.

## Propuesta 1 — CA4: tácticas con su ADR y recuperabilidad explícita · **APLICADA**

La matriz de tácticas (§2.3, líneas 414-446) es deliberadamente general en esta versión: no menciona ADR concretos, y la trazabilidad táctica → decisión vive en la tabla de ADR de §3 (líneas 620-640), que sí incluye ADR-01 a ADR-12 y marca ADR-08 como *Riesgo aceptado*. El criterio se cumple **en sustancia** pero no en la letra, por dos detalles:

**1.1 La matriz de calidad describe la recuperabilidad sin nombrarla `Parcial`.** Línea 386, texto actual:

> Recuperabilidad & Alta para eventos publicados, condicionada por la integración & Kafka facilita replay de eventos ya persistidos en el log. Sin un patrón como Transactional Outbox puede existir una ventana entre el commit en la base y la publicación en el broker. & …

Propuesto (cambia solo la segunda columna):

> Recuperabilidad & **Parcial: alta para eventos publicados, no cubierta para eventos nunca publicados** & …

Motivo: es el término que usan ADR-08 y la wiki, y evita que el lector interprete «Alta» como garantía de no perder eventos. El resto de la fila ya dice lo correcto.

**1.2 Añadir la columna de ADR a la matriz de tácticas**, o bien una frase de cierre después de la matriz (línea 446) que la conecte con §3:

> Las tácticas anteriores se concretan en FoodFlow mediante los ADR de la sección 3: desacoplamiento temporal (ADR-01), base propietaria (ADR-03), orden por `orderId` (ADR-04), reintentos y DLQ (ADR-05), escritura dual aceptada sin Outbox (ADR-08), idempotencia de consumidores (ADR-09), pago determinista (ADR-10), snapshot de contacto (ADR-11) y contrato HTTP (ADR-12).

Una frase es suficiente: mantiene la matriz como análisis general y da la trazabilidad que pide el criterio.

**1.3 Si prefieres no tocar el informe**, la alternativa es ajustar el criterio 4 de HU-008 en el backlog para que describa esta estructura (matriz general en §2 + tabla de ADR en §3). El criterio se redactó contra la versión anterior del documento, donde las dos cosas vivían en la misma tabla. Decide una de las dos vías; la wiki no debe quedar pidiendo algo que el informe organiza de otra forma.

## Propuesta 2 — CA7: referencia definida y no citada · **APLICADA**

`spring-kafka-retry` está en la bibliografía y **no se cita en el texto**. Las otras 49 referencias están citadas y no hay ninguna cita sin definir.

Dos salidas, cualquiera cierra el criterio:

- **Citarla** donde el documento habla de reintentos y DLQ, por ejemplo al final del párrafo de robustez mínima (línea 575) o en ADR-05: `… y, si persisten, el evento se envía a DLQ \cite{spring-kafka-retry}.`
- **Eliminar** el `\bibitem{spring-kafka-retry}` de la sección de referencias.

Recomendación: citarla, porque el mecanismo de reintentos de Spring Kafka es justo el que implementa esa táctica y la referencia aporta respaldo.

> **Las propuestas 3 a 5 no son criterios de HU-008.** Son mejoras detectadas al revisar el documento. Los nombres de campo se deciden mediante revisión cruzada en el PR o la HU que aplique cada cambio, no en esta propuesta.

## Propuesta 3 — Nombres de campos que ya viven en contratos y código

Cada una de estas cuatro es un **nombre**, no una decisión de diseño, y hoy el informe y los contratos versionados dicen cosas distintas. Aquí se conserva el cambio propuesto al informe; cualquier aplicación requiere revisión cruzada en su PR:

| Dónde | Actual | Propuesto | Líneas |
|---|---|---|---|
| Envelope de eventos | `schemaVersion` | `eventVersion` | 435, 462, 652, 702 |
| Campo común del envelope | `orderId` en la raíz | `aggregateId` (igual al `orderId`), con `orderId` dentro del `payload` | 652, 702 |
| Token de pago simulado | `paymentTestToken` | `paymentToken` | 565, 767, 788, 854 |
| Snapshot de contacto | «email/teléfono y canal preferido» | «correo electrónico y canal `EMAIL`» | 854 |

La última evita prometer un canal telefónico: el prototipo implementa solo `EMAIL`, tal como la propia sección de alcance del informe acota.

## Propuesta 4 — Dos afirmaciones de comportamiento que hoy no se sostienen

**4.1 `processed_events`.** El informe menciona la tabla sin sus columnas; el esquema real (HU-002) es `event_id`, `consumer`, `processed_at`. Si el documento enumera columnas en §4 Modelo de Datos, conviene que incluya `consumer`: sin ella, dos consumidores distintos del mismo evento se bloquearían entre sí.

**4.2 Clave de idempotencia del proveedor** (línea 572): «se envía `notificationId` como clave de idempotencia al adaptador/mock del proveedor». El contrato del proveedor y el mock ya implementado (HU-306) reciben `{channel, destination, content, correlationId}` y no leen ninguna clave, así que hoy un reintento **sí** puede producir un segundo envío. Hay que añadir la clave al contrato del mock (y a HU-302) o retirar la frase. Registrado como D-10.

## Propuesta 5 — Que el informe muestre sus diagramas

Las nueve imágenes están versionadas en `docs/wiki/02-arquitectura/diagramas/`, pero los `\diagramplaceholder` del informe las buscan en `diagramas/…` relativo a `docs/informe/`, carpeta que no existe. Mientras siga así el PDF se compila con marcos vacíos.

La forma más simple sin duplicar archivos es añadir la ruta de búsqueda de gráficos en el preámbulo:

```latex
\graphicspath{{./}{diagramas/}{../wiki/02-arquitectura/diagramas/}}
```

Alternativa: copiar (o enlazar) las exportaciones en `docs/informe/diagramas/`, que es lo que HU-704 tiene previsto.

## Qué hacer con este archivo

Cuando apliques los cambios, esta página se actualiza en el mismo PR: cada propuesta aplicada se retira y el criterio correspondiente pasa a **Cumple**. Si decides no aplicar alguna, se anota la decisión y por qué, para que no vuelva a proponerse.
