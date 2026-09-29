import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import { OrderNotification } from './notification.model';

/**
 * Consulta de notificaciones. Siempre a través del API Gateway (regla arquitectónica 1), que la
 * enruta a Notification Service (HU-402).
 */
@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /**
   * `GET /orders/{id}/notifications`. Una lista vacía es una respuesta normal: la notificación
   * se crea cuando se conoce el resultado del pago.
   */
  consultarNotificaciones(orderId: string): Observable<OrderNotification[]> {
    return this.http.get<OrderNotification[]>(
      `${this.baseUrl}/orders/${encodeURIComponent(orderId)}/notifications`,
    );
  }
}
