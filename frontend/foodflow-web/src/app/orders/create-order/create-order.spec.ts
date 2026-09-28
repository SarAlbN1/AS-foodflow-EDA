import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { API_BASE_URL } from '../../core/api-config';
import { Order } from '../order.model';
import { CreateOrder } from './create-order';

const GATEWAY = 'http://gateway.test';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

const CREADO: Order = {
  id: '3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10',
  customerReference: 'PED-0001',
  customerContact: 'ana@foodflow.test',
  notificationChannel: 'EMAIL',
  total: 45000,
  status: 'CREADO',
  createdAt: '2026-09-28T06:41:12.482913Z',
  updatedAt: '2026-09-28T06:41:12.482913Z',
};

describe('CreateOrder (HU-501)', () => {
  let fixture: ComponentFixture<CreateOrder>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreateOrder],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_BASE_URL, useValue: GATEWAY },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CreateOrder);
    http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const dom = () => fixture.nativeElement as HTMLElement;
  const q = (selector: string) => dom().querySelector(selector);

  function escribir(id: string, valor: string): void {
    const campo = q(`#${id}`) as HTMLInputElement | HTMLSelectElement;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
    campo.dispatchEvent(new Event('blur'));
  }

  function diligenciar(datos: Partial<Record<string, string>> = {}): void {
    const valores = {
      customerReference: 'PED-0001',
      customerContact: 'ana@foodflow.test',
      total: '45000',
      paymentToken: 'PAY-OK',
      ...datos,
    };
    for (const [id, valor] of Object.entries(valores)) {
      escribir(id, valor ?? '');
    }
  }

  async function enviar(): Promise<void> {
    (q('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function refrescar(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('CA1: el formulario tiene los cinco campos y el selector PAY-OK / PAY-FAIL', () => {
    for (const id of [
      'customerReference',
      'customerContact',
      'notificationChannel',
      'total',
      'paymentToken',
    ]) {
      expect(q(`#${id}`), id).not.toBeNull();
    }
    const opciones = Array.from((q('#paymentToken') as HTMLSelectElement).options).map(
      (o) => o.value,
    );
    expect(opciones).toEqual(['PAY-OK', 'PAY-FAIL']);
    const canales = Array.from((q('#notificationChannel') as HTMLSelectElement).options).map(
      (o) => o.value,
    );
    expect(canales).toEqual(['EMAIL']);
  });

  it('CA2: con campos vacíos no se envía nada y se marcan los obligatorios', async () => {
    await enviar();

    http.expectNone(`${GATEWAY}/orders`);
    expect(q('[data-testid="error-customerReference"]')).not.toBeNull();
    expect(q('[data-testid="error-customerContact"]')?.textContent).toContain('obligatorio');
    expect(q('[data-testid="error-total"]')?.textContent).toContain('obligatorio');
  });

  it('CA2: rechaza un correo sin formato válido y un total que no es mayor que cero', async () => {
    diligenciar({ customerContact: 'ana@foodflow', total: '0' });
    await enviar();

    http.expectNone(`${GATEWAY}/orders`);
    expect(q('[data-testid="error-customerContact"]')?.textContent).toContain('correo válido');
    expect(q('[data-testid="error-total"]')?.textContent).toContain('mayor que cero');

    diligenciar({ total: '-5' });
    await refrescar();
    expect(q('[data-testid="error-total"]')?.textContent).toContain('mayor que cero');
  });

  it('CA3 y CA4: envía POST /orders al gateway con Idempotency-Key y muestra id y estado CREADO', async () => {
    diligenciar({ paymentToken: 'PAY-FAIL' });
    await enviar();

    const peticion = http.expectOne({ method: 'POST', url: `${GATEWAY}/orders` });
    expect(peticion.request.headers.get('Idempotency-Key')).toMatch(UUID);
    expect(peticion.request.body).toEqual({
      customerReference: 'PED-0001',
      customerContact: 'ana@foodflow.test',
      notificationChannel: 'EMAIL',
      total: 45000,
      paymentToken: 'PAY-FAIL',
    });

    peticion.flush(CREADO, { status: 201, statusText: 'Created' });
    await refrescar();

    expect(q('[data-testid="pedido-id"]')?.textContent).toBe(CREADO.id);
    expect(q('[data-testid="pedido-estado"]')?.textContent).toBe('CREADO');
  });

  it('CA3: un reintento del mismo envío tras un fallo de red reutiliza la misma clave', async () => {
    diligenciar();
    await enviar();
    const primera = http.expectOne(`${GATEWAY}/orders`);
    const clave = primera.request.headers.get('Idempotency-Key');
    primera.error(new ProgressEvent('error'), { status: 0 });
    await refrescar();

    expect(q('button[type="submit"]')?.textContent).toContain('Reintentar');
    await enviar();

    const reintento = http.expectOne(`${GATEWAY}/orders`);
    expect(reintento.request.headers.get('Idempotency-Key')).toBe(clave);
    reintento.flush(CREADO, { status: 201, statusText: 'Created' });
  });

  it('CA3: cambiar los datos o crear otro pedido tras un éxito usa una clave nueva', async () => {
    diligenciar();
    await enviar();
    const primera = http.expectOne(`${GATEWAY}/orders`);
    const clave1 = primera.request.headers.get('Idempotency-Key');
    primera.flush(
      { status: 503, code: 'DEPENDENCY_UNAVAILABLE' },
      { status: 503, statusText: 'x' },
    );
    await refrescar();

    diligenciar({ total: '46000' });
    await enviar();
    const conOtrosDatos = http.expectOne(`${GATEWAY}/orders`);
    const clave2 = conOtrosDatos.request.headers.get('Idempotency-Key');
    expect(clave2).not.toBe(clave1);
    conOtrosDatos.flush(CREADO, { status: 201, statusText: 'Created' });
    await refrescar();

    (q('[data-testid="resultado"] button') as HTMLButtonElement).click();
    await refrescar();
    diligenciar({ total: '46000' });
    await enviar();
    const otroPedido = http.expectOne(`${GATEWAY}/orders`);
    expect(otroPedido.request.headers.get('Idempotency-Key')).not.toBe(clave2);
    otroPedido.flush(CREADO, { status: 201, statusText: 'Created' });
  });

  it('CA3: un segundo envío mientras el primero está en curso no produce otra solicitud', async () => {
    diligenciar();
    await enviar();
    await enviar();

    const unica = http.expectOne(`${GATEWAY}/orders`);
    unica.flush(CREADO, { status: 201, statusText: 'Created' });
  });

  it('CA5: un 400 de validación muestra los campos rechazados y la referencia', async () => {
    diligenciar();
    await enviar();
    http.expectOne(`${GATEWAY}/orders`).flush(
      {
        type: 'https://foodflow.local/problems/validation-error',
        title: 'Solicitud invalida',
        status: 400,
        detail: 'total: debe ser mayor que cero',
        code: 'VALIDATION_ERROR',
        correlationId: '11111111-1111-1111-1111-111111111111',
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await refrescar();

    const error = q('[data-testid="error"]')?.textContent ?? '';
    expect(error).toContain('total: debe ser mayor que cero');
    expect(error).toContain('11111111-1111-1111-1111-111111111111');
  });

  it('CA5: un 500 muestra un mensaje genérico sin detalles internos', async () => {
    diligenciar();
    await enviar();
    http.expectOne(`${GATEWAY}/orders`).flush(
      {
        status: 500,
        code: 'INTERNAL_ERROR',
        detail: 'java.lang.IllegalStateException at OrderRepository jdbc:postgresql://order-db',
        correlationId: '22222222-2222-2222-2222-222222222222',
      },
      { status: 500, statusText: 'Internal Server Error' },
    );
    await refrescar();

    const error = q('[data-testid="error"]')?.textContent ?? '';
    expect(error).toContain('No pudimos procesar la solicitud');
    expect(error).toContain('22222222-2222-2222-2222-222222222222');
    expect(error).not.toContain('Exception');
    expect(error).not.toContain('jdbc');
  });
});
