/**
 * Notificación tal como la devuelve `GET /orders/{id}/notifications`
 * (`contracts/api/openapi.yaml`, esquema `Notification`).
 */

export type NotificationStatus = 'PENDIENTE' | 'ENVIADA' | 'FALLIDA';

export interface OrderNotification {
  id: string;
  orderId: string;
  paymentId: string | null;
  channel: 'EMAIL';
  /** Destino siempre enmascarado por el contrato (`a***@foodflow.test`): dato personal. */
  destination: string;
  /** Texto enviado al cliente; habla del resultado del pago, no del estado del pedido (D-6). */
  content: string;
  status: NotificationStatus;
  attempts: number;
  failureCode: string | null;
  createdAt: string;
  updatedAt: string;
}
