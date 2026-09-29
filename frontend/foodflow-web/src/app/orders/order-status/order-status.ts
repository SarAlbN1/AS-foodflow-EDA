import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { ImportePipe } from '../../core/importe.pipe';
import { ErrorVisible, aErrorVisible } from '../../core/problem-details';
import {
  OrderNotifications,
  SituacionNotificaciones,
} from '../../notifications/order-notifications/order-notifications';
import { OrderFlow } from '../order-flow/order-flow';
import { OrderApiService } from '../order-api.service';
import { Order, OrderStatus } from '../order.model';

const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

/** Cada cuánto se vuelve a consultar un pedido que sigue en `CREADO`. */
export const INTERVALO_CONSULTA_MS = 1000;

/**
 * Cuánto tiempo se consulta de forma automática. La meta de consistencia es converger en menos
 * de 5 s (p95, `atributos-de-calidad.md`); 30 s deja margen sin consultar indefinidamente.
 * Pasado ese tiempo queda el botón "Actualizar".
 */
export const ESPERA_AUTOMATICA_MS = 30_000;

const ETIQUETAS: Record<OrderStatus, string> = {
  CREADO: 'Creado — procesando el pago',
  PAGADO: 'Pagado',
  PAGO_RECHAZADO: 'Pago rechazado',
};

/**
 * Estado de un pedido conocido (HU-502).
 *
 * La consistencia es eventual: tras crear el pedido, Order Service lo actualiza cuando consume
 * el resultado del pago. Por eso, mientras el pedido está en `CREADO`, la pantalla vuelve a
 * consultar cada segundo hasta ver un estado final o agotar la espera automática. Toda consulta
 * es `GET /orders/{id}`: refrescar nunca crea un pedido ni repite una operación.
 */
@Component({
  selector: 'app-order-status',
  imports: [RouterLink, OrderNotifications, OrderFlow, ImportePipe],
  templateUrl: './order-status.html',
  styleUrl: './order-status.css',
})
export class OrderStatusPage {
  /** Identificador de la ruta `orders/:id` (`withComponentInputBinding`). */
  readonly id = input.required<string>();

  private readonly api = inject(OrderApiService);

  protected readonly pedido = signal<Order | null>(null);
  protected readonly error = signal<ErrorVisible | null>(null);
  protected readonly consultando = signal(false);
  protected readonly esperandoResultado = signal(false);
  protected readonly etiquetas = ETIQUETAS;

  /** Última situación de las notificaciones, para el flujo integral (HU-505). */
  protected readonly notificaciones = signal<SituacionNotificaciones>({
    notificaciones: null,
    error: false,
    esperando: false,
  });

  private inicio = 0;
  private siguiente: ReturnType<typeof setTimeout> | undefined;
  private enCurso: Subscription | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.detener());
    // Angular reutiliza el componente al navegar de /orders/A a /orders/B: se reinicia por id.
    effect(() => {
      const id = this.id();
      untracked(() => this.iniciar(id));
    });
  }

  private iniciar(id: string): void {
    this.detener();
    this.pedido.set(null);
    this.esperandoResultado.set(false);
    this.notificaciones.set({ notificaciones: null, error: false, esperando: false });
    if (!UUID.test(id)) {
      this.error.set({
        mensaje: 'El identificador no es válido: debe tener el formato de un UUID.',
        reintentable: false,
      });
      return;
    }
    this.error.set(null);
    this.inicio = Date.now();
    this.consultar();
  }

  /** Refresco manual: una sola consulta, y reanuda la espera automática si sigue en `CREADO`. */
  protected actualizar(): void {
    if (!UUID.test(this.id())) {
      return;
    }
    this.inicio = Date.now();
    this.consultar();
  }

  private consultar(): void {
    this.detener();
    this.consultando.set(true);
    this.enCurso = this.api.consultarPedido(this.id()).subscribe({
      next: (pedido) => {
        this.pedido.set(pedido);
        this.error.set(null);
        this.consultando.set(false);
        this.programarSiguiente(pedido.status);
      },
      error: (falla: unknown) => {
        this.error.set(aErrorVisible(falla));
        this.consultando.set(false);
        this.esperandoResultado.set(false);
      },
    });
  }

  private programarSiguiente(estado: OrderStatus): void {
    const sigueEsperando =
      estado === 'CREADO' &&
      Date.now() - this.inicio + INTERVALO_CONSULTA_MS <= ESPERA_AUTOMATICA_MS;
    this.esperandoResultado.set(sigueEsperando);
    if (sigueEsperando) {
      this.siguiente = setTimeout(() => this.consultar(), INTERVALO_CONSULTA_MS);
    }
  }

  private detener(): void {
    clearTimeout(this.siguiente);
    this.siguiente = undefined;
    this.enCurso?.unsubscribe();
  }
}
