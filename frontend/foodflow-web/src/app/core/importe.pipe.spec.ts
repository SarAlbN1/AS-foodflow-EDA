import { ImportePipe } from './importe.pipe';

describe('ImportePipe', () => {
  const pipe = new ImportePipe();

  it('usa separador de miles y siempre dos decimales, como el texto de la notificación', () => {
    expect(pipe.transform(27800.5)).toBe('27.800,50 COP');
    expect(pipe.transform(45000)).toBe('45.000,00 COP');
    expect(pipe.transform(0.07)).toBe('0,07 COP');
    expect(pipe.transform(1234567.89)).toBe('1.234.567,89 COP');
  });

  it('no inventa un importe cuando no hay valor', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
