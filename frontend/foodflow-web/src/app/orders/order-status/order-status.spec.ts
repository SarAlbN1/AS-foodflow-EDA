import { Component, input } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { API_BASE_URL } from '../../core/api-config';
import { OrderNotifications } from '../../notifications/order-notifications/order-notifications';
import { OrderPayment } from '../../payments/order-payment/order-payment';
import { Order, OrderStatus } from '../order.model';
import { ESPERA_AUTOMATICA_MS, INTERVALO_CONSULTA_MS, OrderStatusPage } from './order-status';

/** Sustituye a las notificaciones (HU-504): estas pruebas cubren solo el estado del pedido. */
@Component({ selector: 'app-order-notifications', template: '' })
class NotificacionesVacias {
  readonly orderId = input.required<string>();
}

/** Sustituye al pago (HU-503), que tiene sus propias pruebas en `order-payment.spec.ts`. */
@Component({ selector: 'app-order-payment', template: '' })
class PagoVacio {
  readonly orderId = input.required<string>();
}

const GATEWAY = 'http://gateway.test';
const ID = '3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10';
const OTRO_ID = '8b1f6d24-59ac-4a1e-9f0c-6d1c2b3a4e5f';

function pedido(status: OrderStatus, id = ID): Order {
  return {
    id,
    customerReference: 'PED-0001',
    customerContact: 'ana@foodflow.test',
    notificationChannel: 'EMAIL',
    total: 45000,
    status,
    createdAt: '2026-09-28T06:41:12.482913Z',
    updatedAt: '2026-09-28T06:41:14.903221Z',
  };
}

describe('OrderStatusPage (HU-502)', () => {
  let fixture: ComponentFixture<OrderStatusPage>;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [OrderStatusPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_BASE_URL, useValue: GATEWAY },
      ],
    })
      .overrideComponent(OrderStatusPage, {
        remove: { imports: [OrderNotifications, OrderPayment] },
        add: { imports: [NotificacionesVacias, PagoVacio] },
      })
      .compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  const q = (selector: string) => (fixture.nativeElement as HTMLElement).querySelector(selector);

  function abrir(id = ID): void {
    fixture = TestBed.createComponent(OrderStatusPage);
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
  }

  /** Responde la consulta pendiente al gateway. */
  function responder(respuesta: Order, id = ID): void {
    const peticion = http.expectOne({ method: 'GET', url: `${GATEWAY}/orders/${id}` });
    peticion.flush(respuesta);
    fixture.detectChanges();
  }

  async function avanzar(ms: number): Promise<void> {
    await vi.advanceTimersByTimeAsync(ms);
    fixture.detectChanges();
  }

  it('CA1 y CA2: consulta el pedido por GET y presenta PAGADO', () => {
    abrir();
    responder(pedido('PAGADO'));

    expect(q('[data-testid="estado-codigo"]')?.textContent).toBe('PAGADO');
    expect(q('[data-testid="estado"]')?.textContent).toContain('Pagado');
    expect(q('[data-testid="esperando"]')).toBeNull();
  });

  it('CA2: presenta PAGO_RECHAZADO y CREADO con su etiqueta', () => {
    abrir();
    responder(pedido('PAGO_RECHAZADO'));
    expect(q('[data-testid="estado"]')?.textContent).toContain('Pago rechazado');

    abrir(OTRO_ID);
    responder(pedido('CREADO', OTRO_ID), OTRO_ID);
    expect(q('[data-testid="estado"]')?.textContent).toContain('Creado');
    fixture.destroy();
  });

  it('CA3: mientras está en CREADO vuelve a consultar y se detiene al llegar al estado final', async () => {
    abrir();
    responder(pedido('CREADO'));
    expect(q('[data-testid="esperando"]')).not.toBeNull();

    await avanzar(INTERVALO_CONSULTA_MS);
    responder(pedido('CREADO'));

    await avanzar(INTERVALO_CONSULTA_MS);
    responder(pedido('PAGO_RECHAZADO'));
    expect(q('[data-testid="estado-codigo"]')?.textContent).toBe('PAGO_RECHAZADO');
    expect(q('[data-testid="esperando"]')).toBeNull();

    await avanzar(10 * INTERVALO_CONSULTA_MS);
    http.expectNone(`${GATEWAY}/orders/${ID}`);
  });

  it('CA3: si el pago no llega, deja de consultar solo tras la espera automática', async () => {
    abrir();
    responder(pedido('CREADO'));

    let consultas = 1;
    for (let t = INTERVALO_CONSULTA_MS; t <= ESPERA_AUTOMATICA_MS; t += INTERVALO_CONSULTA_MS) {
      await avanzar(INTERVALO_CONSULTA_MS);
      const pendientes = http.match(`${GATEWAY}/orders/${ID}`);
      pendientes.forEach((p) => p.flush(pedido('CREADO')));
      consultas += pendientes.length;
      fixture.detectChanges();
    }

    await avanzar(10 * INTERVALO_CONSULTA_MS);
    http.expectNone(`${GATEWAY}/orders/${ID}`);
    expect(consultas).toBeLessThanOrEqual(ESPERA_AUTOMATICA_MS / INTERVALO_CONSULTA_MS + 1);
    expect(q('[data-testid="sin-resultado"]')).not.toBeNull();
  });

  it('CA4: "Actualizar" solo hace GET: nunca crea un pedido ni repite otra operación', async () => {
    abrir();
    responder(pedido('PAGADO'));

    (q('[data-testid="actualizar"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    responder(pedido('PAGADO'));

    http.expectNone({ method: 'POST' });
    expect(q('[data-testid="estado-codigo"]')?.textContent).toBe('PAGADO');
  });

  it('un pedido inexistente muestra el 404 como mensaje para la persona', () => {
    abrir();
    http.expectOne(`${GATEWAY}/orders/${ID}`).flush(
      {
        type: 'https://foodflow.local/problems/not-found',
        status: 404,
        detail: `no existe un pedido con id ${ID}`,
        code: 'NOT_FOUND',
        correlationId: '33333333-3333-3333-3333-333333333333',
      },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    const error = q('[data-testid="error"]')?.textContent ?? '';
    expect(error).toContain('No encontramos');
    expect(error).toContain('33333333-3333-3333-3333-333333333333');
  });

  it('un identificador que no es UUID no llega al gateway', () => {
    abrir('no-es-un-uuid');

    http.expectNone(() => true);
    expect(q('[data-testid="error"]')?.textContent).toContain('no es válido');
  });

  it('al cambiar de pedido deja de consultar el anterior', async () => {
    abrir();
    responder(pedido('CREADO'));

    fixture.componentRef.setInput('id', OTRO_ID);
    fixture.detectChanges();
    responder(pedido('PAGADO', OTRO_ID), OTRO_ID);

    await avanzar(5 * INTERVALO_CONSULTA_MS);
    http.expectNone(`${GATEWAY}/orders/${ID}`);
    expect(q('[data-testid="estado-codigo"]')?.textContent).toBe('PAGADO');
  });
});
