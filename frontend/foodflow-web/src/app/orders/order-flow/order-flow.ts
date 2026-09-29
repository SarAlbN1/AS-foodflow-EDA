import { Component, computed, input } from '@angular/core';

import { OrderNotification } from '../../notifications/notification.model';
import { Order } from '../order.model';
import { EstadoPaso, construirFlujo } from './flujo';

const ETIQUETAS: Record<EstadoPaso, string> = {
  completado: 'Completado',
  'en-espera': 'En curso',
  'no-disponible': 'Aún no disponible',
  rechazado: 'Rechazado',
  fallido: 'Falló',
  error: 'Sin respuesta',
};

/**
 * Flujo integral de un pedido (HU-505): pedido → pago → notificación en una sola vista.
 *
 * No consulta nada por su cuenta: recibe lo que la página de estado ya obtuvo del pedido y de
 * sus notificaciones, así que la vista avanza con la misma consulta periódica, sin peticiones
 * extra y sin acceso a bases de datos. Cada paso distingue lo que **aún no está disponible**
 * (consistencia eventual) de lo que **falló** o **fue rechazado**.
 */
@Component({
  selector: 'app-order-flow',
  templateUrl: './order-flow.html',
  styleUrl: './order-flow.css',
})
export class OrderFlow {
  readonly pedido = input<Order | null>(null);
  readonly errorPedido = input(false);
  readonly esperandoPedido = input(false);
  readonly notificaciones = input<OrderNotification[] | null>(null);
  readonly errorNotificaciones = input(false);
  readonly esperandoNotificaciones = input(false);

  protected readonly etiquetas = ETIQUETAS;

  protected readonly pasos = computed(() =>
    construirFlujo({
      pedido: this.pedido(),
      errorPedido: this.errorPedido(),
      esperandoPedido: this.esperandoPedido(),
      notificaciones: this.notificaciones(),
      errorNotificaciones: this.errorNotificaciones(),
      esperandoNotificaciones: this.esperandoNotificaciones(),
    }),
  );
}
