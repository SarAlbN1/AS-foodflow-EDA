import { nuevaIdempotencyKey } from './idempotency-key';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('nuevaIdempotencyKey', () => {
  it('genera UUID v4 distintos', () => {
    const a = nuevaIdempotencyKey();
    expect(a).toMatch(UUID_V4);
    expect(nuevaIdempotencyKey()).not.toBe(a);
  });

  it('funciona sin crypto.randomUUID (página servida por HTTP fuera de localhost)', () => {
    const original = crypto.randomUUID;
    try {
      Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
      expect(nuevaIdempotencyKey()).toMatch(UUID_V4);
    } finally {
      Object.defineProperty(crypto, 'randomUUID', { value: original, configurable: true });
    }
  });
});
