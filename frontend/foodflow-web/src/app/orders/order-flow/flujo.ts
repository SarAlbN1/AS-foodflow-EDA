import { OrderNotification } from '../../notifications/notification.model';
import { Order } from '../order.model';

/**
 * Estado de un paso del flujo (HU-505). La distinción que importa es la del criterio 2:
 * - `en-espera` y `no-disponible` son **aún no disponible**: el sistema es eventual y el dato
 *   todavía no ha llegado; no hay nada roto.
 * - `rechazado` es un resultado de negocio: el pago se procesó bien y fue rechazado (`PAY-FAIL`).
 * - `fallido` es un fallo del flujo (la notificación no se pudo entregar).
 * - `error` es que la consulta misma no obtuvo respuesta; no dice nada del flujo.
 */
export type EstadoPaso = 'completado' | 'en-espera' | 'no-disponible' | 'rechazado' | 'fallido' | 'error';

export interface PasoFlujo {
  clave: 'pedido' | 'pago' | 'notificacion';
  titulo: string;
  estado: EstadoPaso;
  detalle: string;
}

/** Lo que la página sabe en un momento dado, tal como lo obtuvo por el API Gateway. */
export interface SituacionFlujo {
  pedido: Order | null;
  errorPedido: boolean;
  /** `true` mientras la página sigue consultando el pedido de forma automática. */
  esperandoPedido: boolean;
  /** `null` mientras no hay respuesta de la consulta de notificaciones. */
  notificaciones: OrderNotification[] | null;
  errorNotificaciones: boolean;
  esperandoNotificaciones: boolean;
}

/**
 * Traduce la situación a los tres pasos del flujo: pedido → pago → notificación.
 *
 * Es una función pura: no consulta nada. Todo lo que sabe viene de `GET /orders/{id}` y
 * `GET /orders/{id}/notifications`, sin acceso a bases de datos (criterio 3).
 */
export function construirFlujo(s: SituacionFlujo): PasoFlujo[] {
  return [pasoPedido(s), pasoPago(s), pasoNotificacion(s)];
}

function pasoPedido(s: SituacionFlujo): PasoFlujo {
  const titulo = 'Pedido registrado';
  if (s.pedido) {
    return { clave: 'pedido', titulo, estado: 'completado', detalle: 'Order Service lo guardó y publicó OrderCreated.' };
  }
  if (s.errorPedido) {
    return { clave: 'pedido', titulo, estado: 'error', detalle: 'No se pudo consultar el pedido.' };
  }
  return { clave: 'pedido', titulo, estado: 'en-espera', detalle: 'Consultando el pedido…' };
}

function pasoPago(s: SituacionFlujo): PasoFlujo {
  const titulo = 'Resultado del pago';
  if (!s.pedido) {
    return { clave: 'pago', titulo, estado: 'no-disponible', detalle: 'Se conocerá cuando el pedido esté registrado.' };
  }
  switch (s.pedido.status) {
    case 'PAGADO':
      return { clave: 'pago', titulo, estado: 'completado', detalle: 'Payment Service aprobó el pago y el pedido quedó PAGADO.' };
    case 'PAGO_RECHAZADO':
      return {
        clave: 'pago',
        titulo,
        estado: 'rechazado',
        detalle: 'Payment Service rechazó el pago y el pedido quedó PAGO_RECHAZADO. El flujo terminó bien, con un rechazo.',
      };
    default:
      return s.esperandoPedido
        ? { clave: 'pago', titulo, estado: 'en-espera', detalle: 'Payment Service está procesando el pago; la pantalla se actualiza sola.' }
        : {
            clave: 'pago',
            titulo,
            estado: 'no-disponible',
            detalle: 'Aún sin resultado tras la espera automática. Pulsa "Actualizar" para consultar de nuevo.',
          };
  }
}

function pasoNotificacion(s: SituacionFlujo): PasoFlujo {
  const titulo = 'Notificación al cliente';
  if (!s.pedido) {
    return { clave: 'notificacion', titulo, estado: 'no-disponible', detalle: 'Se crea cuando se conozca el resultado del pago.' };
  }
  if (s.errorNotificaciones) {
    return { clave: 'notificacion', titulo, estado: 'error', detalle: 'No se pudo consultar a Notification Service.' };
  }
  if (s.notificaciones === null) {
    return { clave: 'notificacion', titulo, estado: 'en-espera', detalle: 'Consultando notificaciones…' };
  }
  const ultima = s.notificaciones[0];
  if (!ultima) {
    if (s.pedido.status === 'CREADO') {
      return { clave: 'notificacion', titulo, estado: 'no-disponible', detalle: 'Se crea cuando se conozca el resultado del pago.' };
    }
    return s.esperandoNotificaciones
      ? {
          clave: 'notificacion',
          titulo,
          estado: 'en-espera',
          detalle: 'El pago ya tiene resultado; Notification Service aún no registra la notificación.',
        }
      : { clave: 'notificacion', titulo, estado: 'no-disponible', detalle: 'Aún sin notificación. Pulsa "Actualizar notificaciones".' };
  }
  switch (ultima.status) {
    case 'ENVIADA':
      return { clave: 'notificacion', titulo, estado: 'completado', detalle: `Entregada al proveedor (${ultima.attempts} intento(s)).` };
    case 'FALLIDA':
      return {
        clave: 'notificacion',
        titulo,
        estado: 'fallido',
        detalle: `No se pudo entregar tras ${ultima.attempts} intento(s)${ultima.failureCode ? ` (${ultima.failureCode})` : ''}.`,
      };
    default:
      return { clave: 'notificacion', titulo, estado: 'en-espera', detalle: 'Registrada; se está entregando al proveedor.' };
  }
}
