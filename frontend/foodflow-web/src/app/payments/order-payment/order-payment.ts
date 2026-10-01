import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';

import { ErrorVisible, aErrorVisible } from '../../core/problem-details';
import { ImportePipe } from '../../core/importe.pipe';
import { PaymentApiService } from '../payment-api.service';
import { Payment, PaymentStatus } from '../payment.model';

/** Cada cuánto se vuelve a consultar mientras el pago todavía no existe. */
export const INTERVALO_PAGO_MS = 1000;

/** Cuánto tiempo se consulta de forma automática; después queda el botón "Actualizar pago". */
export const ESPERA_PAGO_MS = 30_000;

const ETIQUETAS: Record<PaymentStatus, string> = {
  APROBADO: 'Pago aprobado',
  RECHAZADO: 'Pago rechazado',
};

/** Motivos de rechazo de Payment Service, en palabras de la persona. Uno desconocido se muestra tal cual. */
const MOTIVOS: Record<string, string> = {
  PAGO_RECHAZADO_POR_TOKEN: 'El método de pago de prueba indicó un rechazo (PAY-FAIL).',
};

/**
 * Resultado del pago de un pedido (HU-503).
 *
 * El pago no existe hasta que Payment Service consume `OrderCreated`, así que la API responde
 * `404 NOT_FOUND` durante el procesamiento eventual. Eso se muestra como «procesando», nunca como
 * un error (criterio 3), y se vuelve a consultar cada segundo hasta que exista o se agote la
 * espera automática. Toda consulta va por el API Gateway (criterios 1 y 4).
 */
@Component({
  selector: 'app-order-payment',
  imports: [ImportePipe],
  templateUrl: './order-payment.html',
  styleUrl: './order-payment.css',
})
export class OrderPayment {
  /** Identificador del pedido, ya validado por la página que contiene este componente. */
  readonly orderId = input.required<string>();

  private readonly api = inject(PaymentApiService);

  protected readonly pago = signal<Payment | null>(null);
  /** `true` mientras la API responde que todavía no hay pago (404): procesamiento eventual. */
  protected readonly procesando = signal(false);
  protected readonly error = signal<ErrorVisible | null>(null);
  protected readonly consultando = signal(false);
  protected readonly esperando = signal(false);
  protected readonly etiquetas = ETIQUETAS;

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

  protected motivo(codigo: string): string {
    return MOTIVOS[codigo] ?? codigo;
  }

  /** Refresco manual: una consulta, y reanuda la espera automática si el pago aún no existe. */
  protected actualizar(): void {
    this.inicio = Date.now();
    this.consultar();
  }

  private iniciar(): void {
    this.detener();
    this.pago.set(null);
    this.procesando.set(false);
    this.error.set(null);
    this.inicio = Date.now();
    this.consultar();
  }

  private consultar(): void {
    this.detener();
    this.consultando.set(true);
    this.enCurso = this.api.consultarPago(this.orderId()).subscribe({
      next: (pago) => {
        this.pago.set(pago);
        this.procesando.set(false);
        this.error.set(null);
        this.consultando.set(false);
        this.esperando.set(false);
      },
      error: (falla: unknown) => {
        this.consultando.set(false);
        if (falla instanceof HttpErrorResponse && falla.status === 404) {
          // Aún no hay pago: es el procesamiento eventual, no un fallo (criterio 3).
          this.procesando.set(true);
          this.error.set(null);
          this.programarSiguiente();
          return;
        }
        this.error.set(aErrorVisible(falla));
        this.esperando.set(false);
      },
    });
  }

  private programarSiguiente(): void {
    const sigue = Date.now() - this.inicio + INTERVALO_PAGO_MS <= ESPERA_PAGO_MS;
    this.esperando.set(sigue);
    if (sigue) {
      this.siguiente = setTimeout(() => this.consultar(), INTERVALO_PAGO_MS);
    }
  }

  private detener(): void {
    clearTimeout(this.siguiente);
    this.siguiente = undefined;
    this.enCurso?.unsubscribe();
  }
}
