# Propuesta a `main.tex`: quién dispara la notificación — D-6

`main.tex` es la fuente de verdad del diseño y **solo Sara lo edita**. Esta página propone el cambio y no lo aplica. Trae el texto LaTeX listo para reemplazar.

Decidido el **2026-09-28** por Sara y Juan: Notification Service consume **`payments.events`**, no `OrderStatusChanged`. El análisis y los motivos están en [D-6](../wiki/02-arquitectura/divergencias-informe-wiki.md). Aquí solo está el texto.

Revisado contra `main.tex` de 1043 líneas. Si el documento cambia, localiza cada frase por su contenido y no por el número de línea.

## Qué cambia y qué no

**No cambia nada** de la wiki, los contratos ni el código: la decisión confirma lo que ya dicen las reglas 10 y 13, ADR-11, [Eventos](../wiki/03-contratos/eventos.md) y los seis esquemas de `contracts/events/v1/`. En #71 Payment Service ya copia el `notificationContact` de `OrderCreated` para llevarlo al evento de pago, que es exactamente lo que esta topología necesita.

Cambian **siete frases del informe** y **dos diagramas**. Los diagramas son HU-704 (Juan): la vista `Dynamic_OrderFlow` dibuja la cadena y el C2 tiene la flecha `OrderStatusChanged → Notification`. Hay que corregir el modelo Structurizr y reexportar las imágenes.

## Reemplazos

### 1. Línea 571 — descripción del flujo

```latex
    \item Notification Service consume \texttt{PaymentApproved}/\texttt{PaymentRejected} de forma idempotente, crea una Notificación en Notification DB y utiliza el snapshot de contacto contenido en el evento de pago para construir el destino.
```

### 2. Línea 655 — propagación del resultado

```latex
    \item Payment Service publica el resultado del pago; Order Service y Notification Service lo consumen de forma independiente: el primero actualiza Order DB y publica \texttt{OrderStatusChanged}, el segundo genera la notificación sin esperar a ese evento.
```

### 3. Línea 725 — HLD, última frase

Se reemplaza solo la frase final; el resto del párrafo queda igual.

```latex
Notification Service tampoco se dispara por una llamada del Cliente: reacciona al resultado del pago, es decir a \texttt{PaymentApproved}/\texttt{PaymentRejected}.
```

### 4. Línea 727 — dirección lógica de eventos

```latex
Cada base PostgreSQL está conectada exclusivamente con su servicio propietario. Kafka se relaciona con los servicios de negocio, nunca con las bases. La dirección lógica de eventos es la siguiente: Order Service publica \texttt{OrderCreated} y \texttt{OrderStatusChanged}; Payment Service consume \texttt{OrderCreated} y publica el resultado del pago; Order Service y Notification Service consumen ese resultado de forma independiente; Notification Service publica el resultado de la notificación. \texttt{OrderStatusChanged} queda disponible para consumidores futuros y no lo consume ningún servicio del prototipo.
```

### 5. Línea 753 — responsabilidades del C2

```latex
\textbf{Order Service} gestiona Pedido y Order DB. Publica \texttt{OrderCreated}; consume \texttt{PaymentApproved}/\texttt{PaymentRejected}; tras actualizar el pedido publica \texttt{OrderStatusChanged}. \textbf{Payment Service} gestiona Pago y Payment DB, consume \texttt{OrderCreated} y publica el resultado determinista del pago. \textbf{Notification Service} gestiona Notificación y Notification DB, consume \texttt{PaymentApproved}/\texttt{PaymentRejected} en el flujo principal y publica \texttt{NotificationSent}/\texttt{NotificationFailed}.
```

### 6. Línea 808 — vista dinámica

```latex
La vista \texttt{Dynamic\_OrderFlow} representa el \textbf{happy path reproducible} del caso de uso con \texttt{PAY-OK}. El Cliente confirma el pedido; Angular invoca \texttt{POST /orders} con \texttt{Idempotency-Key}; el API Gateway enruta la solicitud a Order Service; Order Service persiste el Pedido y publica \texttt{OrderCreated}; Payment Service consume el evento, registra el Pago y publica \texttt{PaymentApproved}. A partir de ahí el flujo se abre en dos ramas independientes: Order Service actualiza el Pedido y publica \texttt{OrderStatusChanged}, y Notification Service persiste la Notificación, invoca al proveedor y publica \texttt{NotificationSent}. Ninguna de las dos espera a la otra.
```

### 7. Línea 634 — fila de ADR-11 en la tabla de decisiones

Es la frase que originó A-2: describe la cadena justo en el punto donde ADR-11 explica cómo viaja el contacto. Sin este reemplazo, la tabla de decisiones contradiría al resto del documento.

```latex
ADR-11 & Snapshot de contacto & El pedido captura canal/destino; \texttt{OrderCreated} lo transporta, Payment Service lo copia en \texttt{PaymentApproved}/\texttt{PaymentRejected} y Notification Service lo toma de ahí sin consultar otra base. \texttt{OrderStatusChanged} también lo lleva, para consumidores futuros. & Implementar \\
```

> La línea 629 (fila de ADR-06) también nombra `OrderStatusChanged`, pero solo lo enumera entre los eventos explícitos del diseño. **No hay que tocarla:** sigue siendo cierta con el abanico.

## Frase nueva recomendada

El informe no explica hoy **por qué** el flujo se abre en abanico, y es la pregunta que un evaluador hace al ver el diagrama dinámico. Conviene añadirla tras la línea 655:

```latex
Order Service y Notification Service consumen el mismo resultado de pago en grupos de consumidores distintos, en lugar de encadenarse. La razón es la ventana de escritura dual que acepta ADR-08: sin \textit{Outbox}, toda publicación posterior a un \textit{commit} puede perderse. Si la notificación dependiera de \texttt{OrderStatusChanged}, atravesaría esa ventana dos veces y un fallo de publicación en Order Service dejaría el pedido pagado y sin notificación, sin ningún mecanismo de reconciliación. Consumiendo el resultado del pago, la notificación solo depende de que Payment Service haya publicado, y un fallo de Order Service no la impide.
```

## Consecuencia sobre el contenido del mensaje

El abanico pierde una garantía que sí daba la cadena: allí, la existencia de una notificación implicaba que el pedido ya había cambiado de estado. Aquí no, porque las dos ramas son independientes.

Por eso **el mensaje se redacta sobre el resultado del pago y no sobre el estado del pedido**: «tu pago fue aprobado», nunca «tu pedido está pagado». Así la notificación no afirma nada que pueda ser falso si Order Service todavía no ha procesado su evento. Queda fijado en [Proveedor de notificaciones](../wiki/03-contratos/proveedor-notificaciones.md) y lo implementa HU-301.

Si se quiere dejarlo explícito en el informe, esta frase va tras la anterior:

```latex
Como contrapartida, la notificación puede emitirse antes de que el Pedido muestre su estado final. Por eso su contenido se redacta sobre el resultado del pago y no sobre el estado del pedido, de modo que sea cierto con independencia de cuándo Order Service aplique su propia actualización.
```

## Qué hacer con este archivo

Cuando Sara aplique los reemplazos en `main.tex`, esta página se borra y la fila D-6 desaparece de [Divergencias informe–wiki](../wiki/02-arquitectura/divergencias-informe-wiki.md), según la regla de cierre de esa página. Los diagramas los corrige HU-704.
