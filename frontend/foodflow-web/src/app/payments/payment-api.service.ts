import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import { Payment } from './payment.model';

/**
 * Consulta del pago de un pedido (HU-503). Siempre a través del API Gateway (regla
 * arquitectónica 1), que la enruta a Payment Service (HU-205): la vista nunca llama al servicio
 * directamente.
 */
@Injectable({ providedIn: 'root' })
export class PaymentApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /**
   * `GET /orders/{id}/payment`. Un `404` con `code: NOT_FOUND` no es un fallo: significa que el
   * pago todavía no se ha procesado (consistencia eventual). Quien consume este método decide.
   */
  consultarPago(orderId: string): Observable<Payment> {
    return this.http.get<Payment>(`${this.baseUrl}/orders/${encodeURIComponent(orderId)}/payment`);
  }
}
