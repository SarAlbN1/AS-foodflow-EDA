import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

/**
 * Consulta de un pedido conocido por su identificador (HU-502, criterio 1). Solo navega a
 * `orders/:id`; la consulta la hace `OrderStatusPage`.
 */
@Component({
  selector: 'app-order-lookup',
  imports: [ReactiveFormsModule],
  template: `
    <form [formGroup]="formulario" (ngSubmit)="consultar()" novalidate>
      <h2>Consultar un pedido</h2>
      <label for="orderId">Identificador del pedido</label>
      <input
        id="orderId"
        formControlName="orderId"
        placeholder="3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10"
      />
      @if (orderId.invalid && orderId.touched) {
        <p class="campo-error" data-testid="error-orderId">
          Escribe el identificador que recibiste al crear el pedido (formato UUID).
        </p>
      }
      <button type="submit">Consultar</button>
    </form>
  `,
  styles: `
    form {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
      max-width: 28rem;
    }
    label {
      font-weight: 600;
    }
    input,
    button {
      font: inherit;
      padding: 0.45rem 0.6rem;
    }
    button {
      margin-top: 0.6rem;
      cursor: pointer;
    }
    .campo-error {
      color: #b00020;
      margin: 0;
    }
  `,
})
export class OrderLookup {
  private readonly router = inject(Router);

  protected readonly orderId = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(UUID)],
  });

  // (ngSubmit) lo emite la directiva [formGroup]; un [formControl] suelto no la activa.
  protected readonly formulario = new FormGroup({ orderId: this.orderId });

  protected consultar(): void {
    this.orderId.setValue(this.orderId.value.trim());
    if (this.orderId.invalid) {
      this.orderId.markAsTouched();
      return;
    }
    void this.router.navigate(['/orders', this.orderId.value.toLowerCase()]);
  }
}
