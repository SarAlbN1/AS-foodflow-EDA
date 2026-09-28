/**
 * Genera una `Idempotency-Key` (UUID v4).
 *
 * `crypto.randomUUID` solo existe en contextos seguros (HTTPS o `localhost`). Publicada con
 * Nginx por HTTP en otra máquina no estaría disponible, así que se construye el UUID con
 * `crypto.getRandomValues`, que funciona en ambos casos.
 */
export function nuevaIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // versión 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variante RFC 4122
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
