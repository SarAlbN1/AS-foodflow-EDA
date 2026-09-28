import { InjectionToken } from '@angular/core';

/**
 * URL base del API Gateway, el único backend que conoce la aplicación (regla arquitectónica 1).
 * Nunca apunta a un servicio interno.
 *
 * Por omisión es el gateway local (`GATEWAY_PORT` 8080, contrato `contracts/api/openapi.yaml`).
 * La publicación con Nginx (HU-607) la sobrescribe en `app.config.ts`.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => 'http://localhost:8080',
});
