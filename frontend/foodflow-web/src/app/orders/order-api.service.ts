import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import { CreateOrderRequest, Order } from './order.model';

/**
 * Cliente HTTP de pedidos. Solo usa operaciones documentadas en `contracts/api/openapi.yaml`
 * y siempre a través del API Gateway (regla arquitectónica 1).
 */
@Injectable({ providedIn: 'root' })
export class OrderApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /**
   * `POST /orders`. La `Idempotency-Key` la decide quien llama: la misma clave para reintentar
   * el mismo envío, una nueva para un pedido distinto (ADR-12).
   */
  crearPedido(pedido: CreateOrderRequest, idempotencyKey: string): Observable<Order> {
    return this.http.post<Order>(`${this.baseUrl}/orders`, pedido, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  /** `GET /orders/{id}`: el estado persistido más reciente. Nunca crea ni modifica nada. */
  consultarPedido(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.baseUrl}/orders/${encodeURIComponent(id)}`);
  }
}
