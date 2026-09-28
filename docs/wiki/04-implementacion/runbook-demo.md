# Runbook de la demostración

[← Índice de la wiki](../Home.md)

> **Estado:** plantilla. Lo completa la HU-706 cuando el flujo esté implementado.

## Cuándo usarlo
Antes y durante la sustentación, para reproducir el flujo completo.

## Prerrequisitos
Docker o Podman con Compose; el repositorio clonado; `.env` creado desde `.env.example`.

## Pasos
1. Levantar el entorno (`scripts/up.sh`).
2. Escenario aprobado: crear un pedido con `PAY-OK`; observar `CREADO` → `PAGADO` y la notificación `ENVIADA`.
3. Escenario rechazado: crear un pedido con `PAY-FAIL`; observar `PAGO_RECHAZADO` y su notificación.
4. Mostrar los eventos en `orders.events`, `payments.events` y `notifications.events`.
5. Fallo del proveedor: usar un destino `*@fail.test`; observar `FALLIDA` y `NotificationFailed`.
6. Inspeccionar una DLQ.

## Restablecer el entorno
Detener con `scripts/down.sh` y volver a levantar.
