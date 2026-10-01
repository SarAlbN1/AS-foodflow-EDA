# Propuesta: la consulta del pago en el informe (HU-205)

[← Informe técnico](README.md)

> `main.tex` lo edita únicamente Sara (`CLAUDE.md` §1). Esta página **propone** el cambio; no lo aplica.

## Por qué

#122 aprobó el cambio de alcance y #131 integró `GET /orders/{id}/payment`. El informe sigue describiendo el contrato HTTP sin esa operación, así que hoy el código y el informe no coinciden en dos sitios. La operación es **opcional**: amplía el contrato mínimo sin cambiarlo, así que los dos reemplazos la añaden marcándola como tal y no tocan las tres operaciones mínimas.

## Reemplazo 1: contrato HTTP (§3, líneas 586–593)

**Texto actual:**

```latex
El contrato HTTP mínimo del prototipo es el siguiente:

\begin{itemize}[leftmargin=*]
    \item \texttt{POST /orders}: crea el pedido. ...
    \item \texttt{GET /orders/\{id\}}: consulta el estado actual del pedido.
    \item \texttt{GET /orders/\{id\}/notifications}: consulta las notificaciones asociadas al pedido; el API Gateway enruta esta operación a Notification Service.
    \item Los errores HTTP se representan mediante RFC 9457 y el contrato se documenta con OpenAPI.
\end{itemize}
```

**Texto propuesto** (se añade un párrafo después de la lista; la lista no cambia):

```latex
Además del contrato mínimo, el prototipo expone una operación \textbf{opcional} de solo lectura: \texttt{GET /orders/\{id\}/payment} consulta el resultado del pago (estado, monto y referencia de transacción). El API Gateway la enruta a Payment Service, que solo lee Payment DB. Si el pago todavía no existe por consistencia eventual, responde \texttt{404} con \texttt{code: NOT\_FOUND} en lugar de inventar un resultado; el cliente lo interpreta como «en procesamiento» mientras el pedido siga en \texttt{CREADO}. Esta operación no coordina el flujo: el pago se sigue resolviendo por eventos.
```

## Reemplazo 2: descripción del C2 (línea 835)

**Texto actual:**

```latex
Las consultas \texttt{GET /orders/\{id\}} también se dirigen a Order Service y \texttt{GET /orders/\{id\}/notifications} se enruta a Notification Service.
```

**Texto propuesto:**

```latex
Las consultas \texttt{GET /orders/\{id\}} también se dirigen a Order Service, \texttt{GET /orders/\{id\}/notifications} se enruta a Notification Service y la consulta opcional \texttt{GET /orders/\{id\}/payment}, a Payment Service.
```

La frase siguiente («los endpoints GET no disparan el flujo asíncrono») sigue siendo cierta y no cambia.

## Lo que no cambia

- Las líneas 813 y 823 («no se representa una llamada directa al pago»): siguen siendo correctas. El cliente **no paga** por API; solo puede **consultar** el resultado una vez procesado.
- ADR-12 (contrato REST): la operación se documenta en el mismo OpenAPI y con los mismos Problem Details.

## Efecto en los diagramas

La fuente `foodflow-structurizr-v9.dsl` no tiene la relación `apiGateway -> paymentService`, así que el **C2 exportado no la dibuja**. Si se aplica el reemplazo 2, conviene añadir a la fuente:

```
apiGateway -> paymentService "Consulta el pago del pedido (opcional)" "REST/JSON sobre HTTPS"
```

y reexportar `c4-c2-contenedores.png`. Es un cambio de HU-704 (Juan); se hace en un PR aparte si se aplica esta propuesta.
