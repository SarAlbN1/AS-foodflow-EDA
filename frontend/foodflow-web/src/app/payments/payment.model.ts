/**
 * Pago tal como lo devuelve `GET /orders/{id}/payment`
 * (`contracts/api/openapi.yaml`, esquema `Payment`, HU-205).
 */
export type PaymentStatus = 'APROBADO' | 'RECHAZADO';

export interface Payment {
  id: string;
  orderId: string;
  status: PaymentStatus;
  amount: number;
  /** Referencia del intento de cobro; existe también cuando el pago se rechaza. */
  transactionReference: string;
  /** Motivo del rechazo; `null` si el pago fue aprobado. */
  reasonCode: string | null;
  createdAt: string;
  updatedAt: string;
}
