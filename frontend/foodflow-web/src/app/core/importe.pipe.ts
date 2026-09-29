import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formatea un importe en pesos colombianos para mostrarlo: separador de miles y dos decimales,
 * seguido de la moneda (`27800.5` → `27.800,50 COP`).
 *
 * Usa `Intl.NumberFormat` con la configuración regional fija `es-CO`, así que el texto no cambia
 * con el idioma del navegador y coincide con el que redacta Notification Service en el `content`
 * de la notificación. El contrato transporta `total` como número; esto es solo presentación.
 */
const FORMATO = new Intl.NumberFormat('es-CO', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

@Pipe({ name: 'importe' })
export class ImportePipe implements PipeTransform {
  transform(valor: number | null | undefined, moneda = 'COP'): string {
    if (valor === null || valor === undefined || Number.isNaN(valor)) {
      return '';
    }
    return `${FORMATO.format(valor)} ${moneda}`;
  }
}
