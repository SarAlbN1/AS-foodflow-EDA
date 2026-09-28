import { HttpErrorResponse } from '@angular/common/http';

/**
 * Error RFC 9457 con las extensiones del proyecto (`contracts/api/openapi.yaml`, `ProblemDetail`).
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?:
    | 'VALIDATION_ERROR'
    | 'NOT_FOUND'
    | 'IDEMPOTENCY_CONFLICT'
    | 'INTERNAL_ERROR'
    | 'DEPENDENCY_UNAVAILABLE';
  correlationId?: string;
}

/** Lo que la interfaz muestra de un error: un texto para la persona y la referencia de soporte. */
export interface ErrorVisible {
  mensaje: string;
  /** `correlationId` para reportar el problema; nunca contiene datos internos. */
  referencia?: string;
  /** Si tiene sentido reintentar el mismo envío (fallo transitorio o sin respuesta). */
  reintentable: boolean;
}

const GENERICO = 'No pudimos procesar la solicitud. Intenta de nuevo en unos momentos.';

/**
 * Traduce un error HTTP a un mensaje para la persona sin exponer detalles internos:
 * solo se muestra el `detail` de un `VALIDATION_ERROR`, que el contrato define como la lista
 * de campos rechazados. Del resto se muestra un texto fijo por `code`; nunca trazas, URLs
 * internas ni el cuerpo crudo.
 */
export function aErrorVisible(error: unknown): ErrorVisible {
  if (!(error instanceof HttpErrorResponse)) {
    return { mensaje: GENERICO, reintentable: true };
  }
  if (error.status === 0) {
    return {
      mensaje: 'No hay conexión con el servidor. Revisa tu conexión e intenta de nuevo.',
      reintentable: true,
    };
  }

  const problema = esProblema(error.error) ? error.error : undefined;
  const referencia = problema?.correlationId;

  switch (problema?.code) {
    case 'VALIDATION_ERROR':
      return {
        mensaje: `Revisa los datos del pedido: ${problema.detail ?? 'hay campos inválidos'}.`,
        referencia,
        reintentable: false,
      };
    case 'IDEMPOTENCY_CONFLICT':
      return {
        mensaje: 'Este envío ya se usó con otros datos. Vuelve a enviar el formulario.',
        referencia,
        reintentable: false,
      };
    case 'NOT_FOUND':
      return { mensaje: 'No encontramos el recurso solicitado.', referencia, reintentable: false };
    case 'DEPENDENCY_UNAVAILABLE':
      return {
        mensaje: 'El servicio de pedidos no está disponible en este momento. Intenta de nuevo.',
        referencia,
        reintentable: true,
      };
    default:
      return { mensaje: GENERICO, referencia, reintentable: error.status >= 500 };
  }
}

function esProblema(cuerpo: unknown): cuerpo is ProblemDetail {
  return typeof cuerpo === 'object' && cuerpo !== null && 'status' in cuerpo;
}
