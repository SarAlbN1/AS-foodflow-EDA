import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';

import { nuevaIdempotencyKey } from '../../core/idempotency-key';
import { ImportePipe } from '../../core/importe.pipe';
import { ErrorVisible, aErrorVisible } from '../../core/problem-details';
import { OrderApiService } from '../order-api.service';
import { CreateOrderRequest, Order } from '../order.model';

/** Mismo formato de correo que valida Order Service (`OrderValidator`): arroba y dominio con punto. */
const EMAIL = /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/;

/** Máximo de `NUMERIC(12,2)` en Order DB y del esquema `Importe` del contrato. */
const TOTAL_MAXIMO = 9_999_999_999.99;

/**
 * Total mayor que cero, con dos decimales como máximo y dentro de `NUMERIC(12,2)`.
 *
 * Los decimales se comprueban redondeando a 2 y comparando con el valor, no multiplicando por
 * 100: en coma flotante `8.2 * 100` da `819.9999999999999`, y una comparación exacta (o con una
 * tolerancia fija, que falla con importes grandes como `1234567.89`) rechazaría totales válidos.
 */
function totalValido(control: AbstractControl): ValidationErrors | null {
  const valor = control.value as number | null;
  if (valor === null || valor === undefined || Number.isNaN(valor)) {
    return null; // lo reporta Validators.required
  }
  if (valor <= 0) {
    return { mayorQueCero: true };
  }
  if (Number(valor.toFixed(2)) !== valor) {
    return { decimales: true };
  }
  if (valor > TOTAL_MAXIMO) {
    return { maximo: true };
  }
  return null;
}

/**
 * Formulario de creación de pedido (HU-501). El cliente **solo crea el pedido**: el pago lo
 * inicia Payment Service al consumir `OrderCreated`, así que la respuesta siempre llega en
 * `CREADO` (regla arquitectónica 5).
 *
 * Idempotencia (ADR-12): cada intento de envío lleva una `Idempotency-Key`. Si el mismo envío
 * se reintenta (mismos datos, sin éxito previo) se reutiliza la clave, de modo que un doble
 * clic o un reintento tras un fallo de red no crea dos pedidos. Cambiar los datos o crear otro
 * pedido tras un éxito genera una clave nueva.
 */
@Component({
  selector: 'app-create-order',
  imports: [ReactiveFormsModule, RouterLink, ImportePipe],
  templateUrl: './create-order.html',
  styleUrl: './create-order.css',
})
export class CreateOrder {
  private readonly api = inject(OrderApiService);

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    customerReference: ['', [Validators.required, Validators.maxLength(60)]],
    customerContact: [
      '',
      [Validators.required, Validators.maxLength(255), Validators.pattern(EMAIL)],
    ],
    notificationChannel: ['EMAIL' as const, Validators.required],
    total: [null as unknown as number, [Validators.required, totalValido]],
    paymentToken: ['PAY-OK' as CreateOrderRequest['paymentToken'], Validators.required],
  });

  protected readonly enviando = signal(false);
  protected readonly creado = signal<Order | null>(null);
  protected readonly error = signal<ErrorVisible | null>(null);

  /** Intento en curso: su clave se reutiliza mientras el cuerpo sea el mismo y no haya éxito. */
  private intento: { clave: string; cuerpo: string } | null = null;

  protected enviar(): void {
    if (this.enviando()) {
      return; // un segundo clic mientras se envía no produce otra solicitud
    }
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    const pedido = this.formulario.getRawValue() as CreateOrderRequest;
    const cuerpo = JSON.stringify(pedido);
    if (this.intento?.cuerpo !== cuerpo) {
      this.intento = { clave: nuevaIdempotencyKey(), cuerpo };
    }

    this.enviando.set(true);
    this.error.set(null);
    this.api.crearPedido(pedido, this.intento.clave).subscribe({
      next: (orden) => {
        this.intento = null; // el siguiente pedido es un intento nuevo
        this.creado.set(orden);
        this.enviando.set(false);
      },
      error: (falla: unknown) => {
        const visible = aErrorVisible(falla);
        if (!visible.reintentable) {
          this.intento = null; // reenviar lo rechazado no es un reintento del mismo envío
        }
        this.error.set(visible);
        this.enviando.set(false);
      },
    });
  }

  /** Vuelve al formulario para crear otro pedido. */
  protected nuevoPedido(): void {
    this.creado.set(null);
    this.error.set(null);
    this.formulario.reset();
  }

  protected invalido(campo: keyof typeof this.formulario.controls): boolean {
    const control = this.formulario.controls[campo];
    return control.invalid && (control.touched || control.dirty);
  }

  protected tieneError(campo: keyof typeof this.formulario.controls, error: string): boolean {
    return this.formulario.controls[campo].hasError(error);
  }
}
