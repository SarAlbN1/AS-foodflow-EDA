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
    <div class="ff-page-container">
      <div class="ff-page-header">
        <h2 class="ff-page-title">Consultar un pedido</h2>
        <p class="ff-page-subtitle">
          Ingresa el identificador único (UUID) para verificar su estado actual y notificaciones.
        </p>
      </div>

      <div class="ff-card">
        <form [formGroup]="formulario" (ngSubmit)="consultar()" novalidate class="ff-form">
          <div class="ff-form-group">
            <label for="orderId" class="ff-label">Identificador del pedido</label>
            <input
              id="orderId"
              formControlName="orderId"
              placeholder="3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10"
              class="ff-input"
              [class.ff-input-error]="orderId.invalid && orderId.touched"
            />
            @if (orderId.invalid && orderId.touched) {
              <p class="campo-error" data-testid="error-orderId">
                Escribe el identificador que recibiste al crear el pedido (formato UUID).
              </p>
            }
          </div>

          <div class="ff-form-actions">
            <button type="submit" class="ff-btn ff-btn-primary">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"></circle>
                <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
              </svg>
              Consultar
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: `
    .ff-page-container {
      display: flex;
      flex-direction: column;
      gap: 24px;
      max-width: 640px;
    }
    .ff-page-title {
      font-size: 1.5rem;
      font-weight: 700;
      color: var(--ff-text-main);
      margin: 0 0 6px 0;
      letter-spacing: -0.02em;
    }
    .ff-page-subtitle {
      font-size: 0.925rem;
      color: var(--ff-text-muted);
      margin: 0;
    }
    .ff-card {
      background: var(--ff-bg-surface);
      border-radius: var(--ff-radius);
      border: 1px solid var(--ff-border);
      box-shadow: var(--ff-shadow);
      padding: 28px;
    }
    .ff-form {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
    .ff-form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .ff-label {
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--ff-text-main);
    }
    .ff-input {
      width: 100%;
      font-family: inherit;
      font-size: 0.925rem;
      padding: 10px 14px;
      color: var(--ff-text-main);
      background-color: #ffffff;
      border: 1px solid var(--ff-border-muted);
      border-radius: var(--ff-radius-sm);
      transition: all 0.15s ease-in-out;
    }
    .ff-input:focus {
      outline: none;
      border-color: var(--ff-primary);
      box-shadow: 0 0 0 3px var(--ff-primary-light);
    }
    .ff-input-error {
      border-color: var(--ff-danger) !important;
      background-color: var(--ff-danger-light);
    }
    .campo-error {
      font-size: 0.825rem;
      color: var(--ff-danger-text);
      margin: 2px 0 0 0;
      font-weight: 500;
    }
    .ff-form-actions {
      display: flex;
      justify-content: flex-end;
    }
    .ff-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      font-family: inherit;
      font-weight: 600;
      font-size: 0.9rem;
      padding: 10px 18px;
      border-radius: var(--ff-radius-sm);
      border: 1px solid transparent;
      cursor: pointer;
      text-decoration: none;
      transition: all 0.15s ease-in-out;
    }
    .ff-btn-primary {
      background: var(--ff-primary);
      color: #ffffff;
      box-shadow: 0 4px 12px rgba(93, 135, 255, 0.3);
    }
    .ff-btn-primary:hover {
      background: var(--ff-primary-hover);
      box-shadow: 0 6px 16px rgba(93, 135, 255, 0.4);
    }
  `,
})
export class OrderLookup {
  private readonly router = inject(Router);

  protected readonly orderId = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(UUID)],
  });

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
