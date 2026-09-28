# Informe técnico (entregable académico)

Aquí vive el documento técnico en LaTeX (`main.tex`), el logo y las figuras exportadas.

## `main.tex` es la fuente de verdad

`main.tex` **fija el diseño** de FoodFlow: estilo arquitectónico, matrices de análisis, ADR, flujo end-to-end, contratos y diagramas. La wiki (`docs/wiki/`) no decide nada por su cuenta: **describe, explica y deriva** lo que este documento establece, y lo traduce a reglas, contratos e historias ejecutables.

Dos consecuencias prácticas:

1. **Solo Sara edita `main.tex`.** Ninguna herramienta ni asistente de IA lo modifica. Una corrección al informe **se propone, no se aplica**.
2. **Cuando la wiki y el informe no coinciden, se corrige la wiki** en un PR que cite la sección del informe. Si el que parece equivocado es el informe, la divergencia se registra en [Divergencias informe–wiki](../wiki/02-arquitectura/divergencias-informe-wiki.md) y decide Sara; no se cambia nada en silencio ni en las dos direcciones a la vez.

Jerarquía completa: [`CLAUDE.md`](../../CLAUDE.md) §1 y [Home de la wiki](../wiki/Home.md).

| Archivo | Contenido |
|---|---|
| `main.tex` | Documento técnico: investigación, análisis arquitectónico, diseño y lecciones aprendidas |
| `diagramas/` | Imágenes exportadas desde las fuentes de la wiki (HU-704) |
| `figuras/` | Gráficas de mercado laboral |
| `correcciones-propuestas-HU-008.md` | Cambios propuestos al documento, pendientes de que Sara los aplique |
