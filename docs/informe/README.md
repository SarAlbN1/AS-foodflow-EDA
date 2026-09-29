# Informe técnico (entregable académico)

Aquí vive el documento técnico en LaTeX (`main.tex`), el logo y las figuras exportadas.

## `main.tex` es la fuente de verdad

`main.tex` **fija el diseño** de FoodFlow: estilo arquitectónico, matrices de análisis, ADR, flujo end-to-end, contratos y diagramas. La wiki (`docs/wiki/`) no decide nada por su cuenta: **describe, explica y deriva** lo que este documento establece, y lo traduce a reglas, contratos e historias ejecutables.

Dos consecuencias prácticas:

1. **Solo Sara edita `main.tex`.** Ninguna herramienta ni asistente de IA lo modifica. Una corrección al informe **se propone, no se aplica**.
2. **Cuando la wiki y el informe no coinciden, se corrige la wiki** en un PR que cite la sección del informe. Si el que parece equivocado es el informe, se documenta en el PR o en un comentario de la HU y decide Sara tras la revisión de Juan; no se cambia nada en silencio ni en las dos direcciones a la vez.

Jerarquía completa: [`CLAUDE.md`](../../CLAUDE.md) §1 y [Home de la wiki](../wiki/Home.md).

| Archivo | Contenido |
|---|---|
| `main.tex` | Documento técnico: investigación, análisis arquitectónico, diseño y lecciones aprendidas |
| `diagramas/` | Imágenes exportadas desde las fuentes de la wiki (HU-704) |
| `figuras/` | Gráficas de mercado laboral |
| `correcciones-propuestas-HU-008.md` | Cambios propuestos al documento, pendientes de que Sara los aplique |
| `propuesta-HU-702-proyeccion-laboral.md` | Propuesta de texto para la columna «Proyección sustentada» de la matriz de mercado (HU-702): tendencia con regla explícita y datos observados separados de inferencias |
