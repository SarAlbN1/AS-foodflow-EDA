import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';

import { ErrorVisible, aErrorVisible } from '../../core/problem-details';
import { NotificationApiService } from '../notification-api.service';
import { NotificationStatus, OrderNotification } from '../notification.model';

/** Cada cuánto se vuelve a consultar mientras no haya una notificación en estado final. */
export const INTERVALO_NOTIFICACIONES_MS = 1000;

/** Cuánto tiempo se consulta de forma automática; después queda el botón "Actualizar". */
export const ESPERA_NOTIFICACIONES_MS = 30_000;

const ETIQUETAS: Record<NotificationStatus, string> = {
  PENDIENTE: 'Enviando',
  ENVIADA: 'Enviada',
  FALLIDA: 'No se pudo enviar',
};

const CANALES: Record<OrderNotification['channel'], string> = {
  EMAIL: 'Correo electrónico',
};

/**
 * Notificaciones de un pedido (HU-504).
 *
 * La notificación no existe hasta que Notification Service consume el resultado del pago, así
 * que una lista vacía es parte normal del procesamiento eventual y no un error. Mientras no haya
 * ninguna, o alguna siga `PENDIENTE`, se vuelve a consultar cada segundo hasta un estado final o
 * hasta agotar la espera automática. Toda consulta es `GET` a través del API Gateway.
 */
@Component({
  selector: 'app-order-notifications',
  templateUrl: './order-notifications.html',
  styleUrl: './order-notifications.css',
})
export class OrderNotifications {
  /** Identificador del pedido, ya validado por la página que contiene este componente. */
  readonly orderId = input.required<string>();

  private readonly api = inject(NotificationApiService);

  protected readonly notificaciones = signal<OrderNotification[] | null>(null);
  protected readonly error = signal<ErrorVisible | null>(null);
  protected readonly consultando = signal(false);
  protected readonly esperando = signal(false);
  protected readonly etiquetas = ETIQUETAS;
  protected readonly canales = CANALES;

  private inicio = 0;
  private siguiente: ReturnType<typeof setTimeout> | undefined;
  private enCurso: Subscription | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.detener());
    effect(() => {
      this.orderId();
      untracked(() => this.iniciar());
    });
  }

  /** Refresco manual: una consulta, y reanuda la espera automática si hace falta. */
  protected actualizar(): void {
    this.inicio = Date.now();
    this.consultar();
  }

  private iniciar(): void {
    this.detener();
    this.notificaciones.set(null);
    this.error.set(null);
    this.inicio = Date.now();
    this.consultar();
  }

  private consultar(): void {
    this.detener();
    this.consultando.set(true);
    this.enCurso = this.api.consultarNotificaciones(this.orderId()).subscribe({
      next: (lista) => {
        this.notificaciones.set(lista);
        this.error.set(null);
        this.consultando.set(false);
        this.programarSiguiente(lista);
      },
      error: (falla: unknown) => {
        this.error.set(aErrorVisible(falla));
        this.consultando.set(false);
        this.esperando.set(false);
      },
    });
  }

  private programarSiguiente(lista: OrderNotification[]): void {
    const sinFinal = lista.length === 0 || lista.some((n) => n.status === 'PENDIENTE');
    const sigue =
      sinFinal &&
      Date.now() - this.inicio + INTERVALO_NOTIFICACIONES_MS <= ESPERA_NOTIFICACIONES_MS;
    this.esperando.set(sigue);
    if (sigue) {
      this.siguiente = setTimeout(() => this.consultar(), INTERVALO_NOTIFICACIONES_MS);
    }
  }

  private detener(): void {
    clearTimeout(this.siguiente);
    this.siguiente = undefined;
    this.enCurso?.unsubscribe();
  }
}
