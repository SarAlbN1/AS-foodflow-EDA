import { NotificationStatus, OrderNotification } from '../../notifications/notification.model';
import { Order, OrderStatus } from '../order.model';
import { PasoFlujo, SituacionFlujo, construirFlujo } from './flujo';

const PEDIDO = '3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10';

function pedido(status: OrderStatus): Order {
  return {
    id: PEDIDO,
    customerReference: 'PED-0505',
    customerContact: 'ana@foodflow.test',
    notificationChannel: 'EMAIL',
    total: 45000,
    status,
    createdAt: '2026-09-29T06:00:00Z',
    updatedAt: '2026-09-29T06:00:01Z',
  };
}

function notificacion(status: NotificationStatus, extra: Partial<OrderNotification> = {}): OrderNotification {
  return {
    id: '8b1f6d24-59ac-4a1e-9f0c-6d1c2b3a4e5f',
    orderId: PEDIDO,
    paymentId: 'c2d3e4f5-6789-4abc-8def-0123456789ab',
    channel: 'EMAIL',
    destination: 'a***@foodflow.test',
    content: 'Tu pago de 45.000,00 COP fue aprobado.',
    status,
    attempts: 1,
    failureCode: null,
    createdAt: '2026-09-29T06:00:02Z',
    updatedAt: '2026-09-29T06:00:02Z',
    ...extra,
  };
}

function situacion(extra: Partial<SituacionFlujo>): SituacionFlujo {
  return {
    pedido: null,
    errorPedido: false,
    esperandoPedido: false,
    notificaciones: null,
    errorNotificaciones: false,
    esperandoNotificaciones: false,
    ...extra,
  };
}

function estados(pasos: PasoFlujo[]): string[] {
  return pasos.map((p) => `${p.clave}:${p.estado}`);
}

describe('construirFlujo (HU-505)', () => {
  it('CA1: siempre presenta los tres pasos, en orden pedido → pago → notificación', () => {
    expect(construirFlujo(situacion({})).map((p) => p.clave)).toEqual(['pedido', 'pago', 'notificacion']);
  });

  it('CA3: recién creado, el pago va en curso y la notificación aún no está disponible', () => {
    const pasos = construirFlujo(
      situacion({ pedido: pedido('CREADO'), esperandoPedido: true, notificaciones: [], esperandoNotificaciones: true }),
    );
    expect(estados(pasos)).toEqual(['pedido:completado', 'pago:en-espera', 'notificacion:no-disponible']);
  });

  it('CA4 (PAY-OK): pedido PAGADO y notificación enviada completan el flujo', () => {
    const pasos = construirFlujo(situacion({ pedido: pedido('PAGADO'), notificaciones: [notificacion('ENVIADA')] }));
    expect(estados(pasos)).toEqual(['pedido:completado', 'pago:completado', 'notificacion:completado']);
  });

  it('CA4 (PAY-FAIL): el rechazo es un resultado, no un fallo; la notificación del rechazo se sigue enviando', () => {
    const pasos = construirFlujo(situacion({ pedido: pedido('PAGO_RECHAZADO'), notificaciones: [notificacion('PENDIENTE')] }));
    expect(estados(pasos)).toEqual(['pedido:completado', 'pago:rechazado', 'notificacion:en-espera']);
    expect(pasos[1].detalle).toContain('terminó bien');
  });

  it('CA2: una notificación FALLIDA es un fallo, con su motivo e intentos', () => {
    const pasos = construirFlujo(
      situacion({
        pedido: pedido('PAGADO'),
        notificaciones: [notificacion('FALLIDA', { attempts: 3, failureCode: 'PROVEEDOR_NO_DISPONIBLE' })],
      }),
    );
    expect(pasos[2].estado).toBe('fallido');
    expect(pasos[2].detalle).toContain('3 intento(s)');
    expect(pasos[2].detalle).toContain('PROVEEDOR_NO_DISPONIBLE');
  });

  it('CA2: con el pago resuelto y sin notificación aún, es "en curso", no un fallo', () => {
    const pasos = construirFlujo(
      situacion({ pedido: pedido('PAGADO'), notificaciones: [], esperandoNotificaciones: true }),
    );
    expect(pasos[2].estado).toBe('en-espera');
  });

  it('CA2: agotada la espera sin notificación, queda "aún no disponible", tampoco un fallo', () => {
    const pasos = construirFlujo(situacion({ pedido: pedido('PAGADO'), notificaciones: [], esperandoNotificaciones: false }));
    expect(pasos[2].estado).toBe('no-disponible');
  });

  it('CA2: agotada la espera con el pedido en CREADO, el pago queda "aún no disponible"', () => {
    const pasos = construirFlujo(situacion({ pedido: pedido('CREADO'), esperandoPedido: false, notificaciones: [] }));
    expect(pasos[1].estado).toBe('no-disponible');
  });

  it('CA2: si no responde la consulta de notificaciones es "sin respuesta", distinto de fallido', () => {
    const pasos = construirFlujo(situacion({ pedido: pedido('PAGADO'), errorNotificaciones: true }));
    expect(pasos[2].estado).toBe('error');
  });

  it('si no responde la consulta del pedido, los pasos siguientes aún no están disponibles', () => {
    expect(estados(construirFlujo(situacion({ errorPedido: true })))).toEqual([
      'pedido:error',
      'pago:no-disponible',
      'notificacion:no-disponible',
    ]);
  });

  it('usa la notificación más reciente (la API las devuelve de la más nueva a la más antigua)', () => {
    const pasos = construirFlujo(
      situacion({ pedido: pedido('PAGADO'), notificaciones: [notificacion('ENVIADA'), notificacion('FALLIDA')] }),
    );
    expect(pasos[2].estado).toBe('completado');
  });
});
