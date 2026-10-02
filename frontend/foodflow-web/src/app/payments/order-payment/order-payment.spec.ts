import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api-config';
import { Payment, PaymentStatus } from '../payment.model';
import { ESPERA_PAGO_MS, INTERVALO_PAGO_MS, OrderPayment } from './order-payment';

const GATEWAY = 'http://gateway.test';
const PEDIDO = '3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10';
const URL = `${GATEWAY}/orders/${PEDIDO}/payment`;

function pago(status: PaymentStatus, extra: Partial<Payment> = {}): Payment {
  return {
    id: 'c2d3e4f5-6789-4abc-8def-0123456789ab',
    orderId: PEDIDO,
    status,
    amount: 45900,
    transactionReference: `TXN-20260928-${PEDIDO}`,
    reasonCode: status === 'RECHAZADO' ? 'PAGO_RECHAZADO_POR_TOKEN' : null,
    createdAt: '2026-09-28T06:41:14.902117Z',
    updatedAt: '2026-09-28T06:41:14.902117Z',
    ...extra,
  };
}

const SIN_PAGO = {
  type: 'https://foodflow.local/problems/not-found',
  title: 'Pago no registrado',
  status: 404,
  detail: `todavia no hay un pago registrado para el pedido ${PEDIDO}`,
  code: 'NOT_FOUND',
  correlationId: '33333333-3333-3333-3333-333333333333',
};

describe('OrderPayment (HU-503)', () => {
  let fixture: ComponentFixture<OrderPayment>;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [OrderPayment],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: GATEWAY },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrderPayment);
    fixture.componentRef.setInput('orderId', PEDIDO);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    vi.useRealTimers();
  });

  const q = (selector: string) => (fixture.nativeElement as HTMLElement).querySelector(selector);

  function responder(cuerpo: Payment): void {
    http.expectOne({ method: 'GET', url: URL }).flush(cuerpo);
    fixture.detectChanges();
  }

  function responderSinPago(): void {
    http.expectOne({ method: 'GET', url: URL }).flush(SIN_PAGO, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
  }

  async function avanzar(ms: number): Promise<void> {
    await vi.advanceTimersByTimeAsync(ms);
    fixture.detectChanges();
  }

  it('CA1 y CA2: consulta al gateway y muestra un pago APROBADO con monto y referencia', () => {
    responder(pago('APROBADO'));

    expect(q('[data-testid="pago-estado"]')?.textContent).toBe('Pago aprobado');
    expect(q('[data-testid="pago-estado-codigo"]')?.textContent).toBe('APROBADO');
    expect(q('[data-testid="pago-monto"]')?.textContent).toContain('45.900');
    expect(q('[data-testid="pago-referencia"]')?.textContent).toBe(`TXN-20260928-${PEDIDO}`);
    expect(q('[data-testid="pago-motivo"]')).toBeNull();
    expect(q('[data-testid="pago-procesando"]')).toBeNull();
    expect(q('[data-testid="pago-error"]')).toBeNull();
  });

  it('CA2: un pago RECHAZADO muestra su motivo en palabras de la persona', () => {
    responder(pago('RECHAZADO'));

    expect(q('[data-testid="pago-estado"]')?.textContent).toBe('Pago rechazado');
    expect(q('[data-testid="pago"]')?.getAttribute('data-estado')).toBe('RECHAZADO');
    expect(q('[data-testid="pago-motivo"]')?.textContent).toContain('PAY-FAIL');
  });

  it('CA3: un 404 se muestra como "procesando", no como error, y vuelve a consultar hasta que el pago existe', async () => {
    responderSinPago();

    expect(q('[data-testid="pago-procesando"]')?.textContent).toContain('Procesando el pago');
    expect(q('[data-testid="pago-error"]')).toBeNull();

    await avanzar(INTERVALO_PAGO_MS);
    responderSinPago();
    await avanzar(INTERVALO_PAGO_MS);
    responder(pago('APROBADO'));

    expect(q('[data-testid="pago-estado"]')?.textContent).toBe('Pago aprobado');
    expect(q('[data-testid="pago-procesando"]')).toBeNull();

    // Con el pago ya registrado no hay más consultas automáticas.
    await avanzar(INTERVALO_PAGO_MS * 3);
    http.expectNone(URL);
  });

  it('CA3: agotada la espera automática deja de consultar y ofrece actualizar a mano', async () => {
    responderSinPago();
    const consultas = Math.floor(ESPERA_PAGO_MS / INTERVALO_PAGO_MS);
    for (let i = 0; i < consultas; i++) {
      await avanzar(INTERVALO_PAGO_MS);
      const pendientes = http.match(URL);
      if (pendientes.length === 0) {
        break;
      }
      pendientes.forEach((p) => p.flush(SIN_PAGO, { status: 404, statusText: 'Not Found' }));
      fixture.detectChanges();
    }

    await avanzar(INTERVALO_PAGO_MS * 5);
    http.expectNone(URL);
    expect(q('[data-testid="pago-procesando"]')?.textContent).toContain('Actualizar pago');

    (q('[data-testid="pago-actualizar"]') as HTMLButtonElement).click();
    responder(pago('RECHAZADO'));
    expect(q('[data-testid="pago-estado"]')?.textContent).toBe('Pago rechazado');
  });

  it('un fallo real (503) sí se muestra como error, con su referencia', () => {
    http.expectOne({ method: 'GET', url: URL }).flush(
      { status: 503, code: 'DEPENDENCY_UNAVAILABLE', correlationId: '44444444-4444-4444-4444-444444444444' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    fixture.detectChanges();

    expect(q('[data-testid="pago-error"]')).not.toBeNull();
    expect(q('[data-testid="pago-error"]')?.textContent).toContain('44444444-4444-4444-4444-444444444444');
    expect(q('[data-testid="pago-procesando"]')).toBeNull();
  });

  it('CA4: la única URL consultada es la del gateway', () => {
    const peticion = http.expectOne(URL);
    expect(peticion.request.url.startsWith(GATEWAY)).toBe(true);
    peticion.flush(pago('APROBADO'));
  });
});
