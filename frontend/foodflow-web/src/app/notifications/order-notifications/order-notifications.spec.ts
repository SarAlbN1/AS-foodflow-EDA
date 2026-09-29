import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api-config';
import { NotificationStatus, OrderNotification } from '../notification.model';
import {
  ESPERA_NOTIFICACIONES_MS,
  INTERVALO_NOTIFICACIONES_MS,
  OrderNotifications,
} from './order-notifications';

const GATEWAY = 'http://gateway.test';
const PEDIDO = '3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10';
const URL = `${GATEWAY}/orders/${PEDIDO}/notifications`;

function notificacion(
  status: NotificationStatus,
  extra: Partial<OrderNotification> = {},
): OrderNotification {
  return {
    id: '8b1f6d24-59ac-4a1e-9f0c-6d1c2b3a4e5f',
    orderId: PEDIDO,
    paymentId: 'c2d3e4f5-6789-4abc-8def-0123456789ab',
    channel: 'EMAIL',
    destination: 'a***@foodflow.test',
    content: 'Tu pago del pedido PED-0001 fue aprobado.',
    status,
    attempts: 1,
    failureCode: null,
    createdAt: '2026-09-28T06:41:15.114002Z',
    updatedAt: '2026-09-28T06:41:15.742318Z',
    ...extra,
  };
}

describe('OrderNotifications (HU-504)', () => {
  let fixture: ComponentFixture<OrderNotifications>;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [OrderNotifications],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: GATEWAY },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrderNotifications);
    fixture.componentRef.setInput('orderId', PEDIDO);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    vi.useRealTimers();
  });

  const q = (selector: string) => (fixture.nativeElement as HTMLElement).querySelector(selector);
  const todos = (selector: string) =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll(selector));

  function responder(lista: OrderNotification[]): void {
    http.expectOne({ method: 'GET', url: URL }).flush(lista);
    fixture.detectChanges();
  }

  async function avanzar(ms: number): Promise<void> {
    await vi.advanceTimersByTimeAsync(ms);
    fixture.detectChanges();
  }

  it('CA1 y CA2: consulta al gateway y muestra canal, contenido y estado', () => {
    responder([notificacion('ENVIADA')]);

    expect(q('[data-testid="notificacion-canal"]')?.textContent).toBe('Correo electrónico');
    expect(q('[data-testid="notificacion-contenido"]')?.textContent).toBe(
      'Tu pago del pedido PED-0001 fue aprobado.',
    );
    expect(q('[data-testid="notificacion-estado"]')?.textContent).toBe('Enviada');
    expect(q('[data-testid="notificacion-estado-codigo"]')?.textContent).toBe('ENVIADA');
  });

  it('CA3: presenta PENDIENTE, ENVIADA y FALLIDA, y el motivo de un fallo', () => {
    responder([
      notificacion('PENDIENTE', { id: '1' }),
      notificacion('ENVIADA', { id: '2' }),
      notificacion('FALLIDA', { id: '3', attempts: 3, failureCode: 'PROVIDER_REJECTED' }),
    ]);

    expect(todos('[data-testid="notificacion-estado"]').map((e) => e.textContent)).toEqual([
      'Enviando',
      'Enviada',
      'No se pudo enviar',
    ]);
    expect(q('[data-testid="notificacion-motivo"]')?.textContent).toBe('PROVIDER_REJECTED');
    fixture.destroy(); // queda una PENDIENTE: se detiene la espera automatica
  });

  it('CA4: sin notificaciones es un estado normal, y aparece sola cuando se crea', async () => {
    responder([]);
    expect(q('[data-testid="sin-notificaciones"]')?.textContent).toContain(
      'Todavía no hay notificaciones',
    );
    expect(q('[data-testid="notificaciones-error"]')).toBeNull();

    await avanzar(INTERVALO_NOTIFICACIONES_MS);
    responder([notificacion('ENVIADA')]);

    expect(q('[data-testid="notificacion-estado-codigo"]')?.textContent).toBe('ENVIADA');
    await avanzar(10 * INTERVALO_NOTIFICACIONES_MS);
    http.expectNone(URL);
  });

  it('CA4: mientras una notificación sigue PENDIENTE vuelve a consultar hasta el estado final', async () => {
    responder([notificacion('PENDIENTE')]);
    expect(q('[data-testid="notificacion-en-curso"]')).not.toBeNull();

    await avanzar(INTERVALO_NOTIFICACIONES_MS);
    responder([notificacion('FALLIDA', { attempts: 3, failureCode: 'PROVIDER_REJECTED' })]);

    expect(q('[data-testid="notificacion-en-curso"]')).toBeNull();
    await avanzar(10 * INTERVALO_NOTIFICACIONES_MS);
    http.expectNone(URL);
  });

  it('deja de consultar sola tras la espera automática, y "Actualizar" hace un GET', async () => {
    responder([]);
    for (
      let t = INTERVALO_NOTIFICACIONES_MS;
      t <= ESPERA_NOTIFICACIONES_MS;
      t += INTERVALO_NOTIFICACIONES_MS
    ) {
      await avanzar(INTERVALO_NOTIFICACIONES_MS);
      http.match(URL).forEach((p) => p.flush([]));
      fixture.detectChanges();
    }
    await avanzar(10 * INTERVALO_NOTIFICACIONES_MS);
    http.expectNone(URL);

    (q('[data-testid="notificaciones-actualizar"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const manual = http.expectOne({ method: 'GET', url: URL });
    manual.flush([notificacion('ENVIADA')]);
    fixture.detectChanges();
    http.expectNone({ method: 'POST' });
  });

  it('un error del gateway se muestra sin detalles internos', () => {
    http
      .expectOne(URL)
      .flush(
        {
          status: 503,
          code: 'DEPENDENCY_UNAVAILABLE',
          correlationId: '55555555-5555-5555-5555-555555555555',
        },
        { status: 503, statusText: 'Service Unavailable' },
      );
    fixture.detectChanges();

    const error = q('[data-testid="notificaciones-error"]')?.textContent ?? '';
    expect(error).toContain('no está disponible');
    expect(error).toContain('55555555-5555-5555-5555-555555555555');
  });

  it('HU-505: informa de cada consulta a la página, sin peticiones adicionales', () => {
    const avisos: unknown[] = [];
    fixture.componentInstance.situacion.subscribe((s) => avisos.push(s));

    http.expectOne(URL).flush([notificacion('PENDIENTE')]);
    fixture.detectChanges();
    expect(avisos.at(-1)).toEqual({ notificaciones: [notificacion('PENDIENTE')], error: false, esperando: true });

    vi.advanceTimersByTime(INTERVALO_NOTIFICACIONES_MS);
    http.expectOne(URL).flush({ status: 503 }, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    expect(avisos.at(-1)).toMatchObject({ error: true, esperando: false });
  });
});
