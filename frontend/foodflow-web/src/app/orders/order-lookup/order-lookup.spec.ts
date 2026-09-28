import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { OrderLookup } from './order-lookup';

describe('OrderLookup (HU-502, CA1)', () => {
  let navegar: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderLookup],
      providers: [provideRouter([])],
    }).compileComponents();
    navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  function consultar(valor: string): HTMLElement {
    const fixture = TestBed.createComponent(OrderLookup);
    fixture.detectChanges();
    const dom = fixture.nativeElement as HTMLElement;
    const campo = dom.querySelector('#orderId') as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    dom.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    return dom;
  }

  it('navega al estado del pedido con un identificador válido', () => {
    consultar('  3F6C1E0A-6C9D-4F6F-9C4B-2A9F1D5E7B10 ');

    expect(navegar).toHaveBeenCalledWith(['/orders', '3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10']);
  });

  it('no navega y explica el formato si el identificador no es válido', () => {
    const dom = consultar('PED-0001');

    expect(navegar).not.toHaveBeenCalled();
    expect(dom.querySelector('[data-testid="error-orderId"]')).not.toBeNull();
  });
});
