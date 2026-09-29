import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Order } from '../order.model';
import { OrderFlow } from './order-flow';

function pedido(status: Order['status']): Order {
  return {
    id: '3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10',
    customerReference: 'PED-0505',
    customerContact: 'ana@foodflow.test',
    notificationChannel: 'EMAIL',
    total: 45000,
    status,
    createdAt: '2026-09-29T06:00:00Z',
    updatedAt: '2026-09-29T06:00:01Z',
  };
}

describe('OrderFlow (HU-505)', () => {
  let fixture: ComponentFixture<OrderFlow>;

  const q = (sel: string) => (fixture.nativeElement as HTMLElement).querySelector(sel);

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [OrderFlow] }).compileComponents();
    fixture = TestBed.createComponent(OrderFlow);
  });

  it('dibuja los tres pasos con su estado y una etiqueta legible', () => {
    fixture.componentRef.setInput('pedido', pedido('PAGO_RECHAZADO'));
    fixture.componentRef.setInput('notificaciones', []);
    fixture.detectChanges();

    expect(q('[data-testid="paso-pedido"]')?.getAttribute('data-estado')).toBe('completado');
    expect(q('[data-testid="paso-pago"]')?.getAttribute('data-estado')).toBe('rechazado');
    expect(q('[data-testid="paso-pago"] [data-testid="paso-estado"]')?.textContent?.trim()).toBe('Rechazado');
    expect(q('[data-testid="paso-notificacion"]')?.getAttribute('data-estado')).toBe('no-disponible');
    expect(q('[data-testid="paso-notificacion"] [data-testid="paso-estado"]')?.textContent?.trim()).toBe(
      'Aún no disponible',
    );
  });

  it('CA3: avanza cuando cambian los datos, sin consultar nada por su cuenta', () => {
    fixture.componentRef.setInput('pedido', pedido('CREADO'));
    fixture.componentRef.setInput('esperandoPedido', true);
    fixture.detectChanges();
    expect(q('[data-testid="paso-pago"]')?.getAttribute('data-estado')).toBe('en-espera');

    fixture.componentRef.setInput('pedido', pedido('PAGADO'));
    fixture.componentRef.setInput('esperandoPedido', false);
    fixture.detectChanges();
    expect(q('[data-testid="paso-pago"]')?.getAttribute('data-estado')).toBe('completado');
  });

  it('es accesible: lista ordenada con título y región viva', () => {
    fixture.detectChanges();
    expect(q('[data-testid="flujo"]')?.getAttribute('aria-labelledby')).toBe('flujo-titulo');
    expect(q('ol')?.getAttribute('aria-live')).toBe('polite');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('ol > li').length).toBe(3);
  });
});
