/**
 * Tipos de la API de pedidos, tal como los define `contracts/api/openapi.yaml`.
 * Si el contrato cambia, estos tipos cambian en el mismo PR.
 */

export type NotificationChannel = 'EMAIL';

/** Resultado de pago simulado (ADR-10). */
export type PaymentToken = 'PAY-OK' | 'PAY-FAIL';

export type OrderStatus = 'CREADO' | 'PAGADO' | 'PAGO_RECHAZADO';

/** Cuerpo de `POST /orders`. */
export interface CreateOrderRequest {
  customerReference: string;
  customerContact: string;
  notificationChannel: NotificationChannel;
  total: number;
  paymentToken: PaymentToken;
}

/** Representación del pedido en `POST /orders` y `GET /orders/{id}`. */
export interface Order {
  id: string;
  customerReference: string;
  customerContact: string;
  notificationChannel: NotificationChannel;
  total: number;
  status: OrderStatus;
  createdAt: string;
  updatedAt: string;
}
