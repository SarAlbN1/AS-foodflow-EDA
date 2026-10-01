# Propuesta: gráficas por nivel de experiencia en §2.5

[← Informe técnico](README.md)

> `main.tex` lo edita únicamente Sara (`CLAUDE.md` §1). Esta página **propone** el cambio; no lo aplica.

## Qué se propone

Añadir a la sección **2.5 «Matriz de Mercado Laboral vs Estilo y Stack»** cuatro gráficas que separan la información laboral por nivel de experiencia:

| Figura | Archivo (ya en el repositorio) | Fuente |
|---|---|---|
| Vacantes Junior por tecnología | `docs/informe/figuras/vacantes_junior_colombia.png` | `vacantes_junior_colombia.mmd` |
| Vacantes Senior por tecnología | `docs/informe/figuras/vacantes_senior_colombia.png` | `vacantes_senior_colombia.mmd` |
| Salario mensual observado, perfiles Junior | `docs/informe/figuras/salarios_junior_colombia.png` | `salarios_junior_colombia.mmd` |
| Salario mensual observado, perfiles Senior | `docs/informe/figuras/salarios_senior_colombia.png` | `salarios_senior_colombia.mmd` |

Las fuentes están en `docs/wiki/02-arquitectura/diagramas/fuentes/`. Las imágenes están en la ruta relativa a `main.tex`, así que el bloque compila sin tocar `\graphicspath`.

**Dónde insertarlo:** justo después de la figura «Mercado laboral - ofertas publicadas durante el último mes» (`figuras/ofertas-mercado-laboral.png`) y antes del comentario `% 3. DISENO EJEMPLO PRACTICO`.

## Antes de aplicarlo: tres puntos a decidir

1. **Las cifras salariales no tienen `\cite`.** Las notas dicen «vacantes reales publicadas en Colombia durante 2026», pero no identifican las vacantes: Junior 3.0 / 2.75 / 4.4 / 5.5 M y Senior 16.5 / 9.0 / 7.0 / 16.0 M. El criterio 3 de HU-702 exige que ninguna cifra quede sin fuente. Hace falta un `\bibitem` por vacante, como `job-java-81` o `job-kafka-9m`, y citarlo en la nota de cada figura. Los conteos de vacantes sí tienen metodología reproducible (LinkedIn, filtros *Entry level* y *Mid-Senior level*, corte del 30-09-2026), pero conviene citarlos igual que `linkedin-*-jobs`.
2. **Los salarios Senior no cuadran con la matriz.** La matriz de §2.5 da para Java + Spring Boot «COP 8.1 M/mes; otras ofertas revisadas: aprox. COP 4.5--10 M», y CENISOFT/Fedesoft «Senior Tipo I COP 6.7--9.1 M/mes». La gráfica Senior pone Spring Boot en **16.5 M** y Kafka en **16.0 M**. Puede ser correcto con otra muestra, pero el texto tendría que explicar la diferencia o la matriz y la gráfica se contradicen.
3. **La gráfica salarial anterior sigue en el informe.** `figuras/mercado-laboral.png` todavía muestra Kafka en 9.5 M; la fuente ya se corrigió a 9.0 M, que es el tope publicado de `job-kafka-9m`. Hay dos opciones: reexportarla desde `mercado-laboral.mmd` o retirarla si las cuatro gráficas nuevas la sustituyen.

## Bloque LaTeX propuesto

```latex
\subsubsection{Mercado laboral por nivel de experiencia}

Las siguientes gráficas separan la información laboral observada por nivel de experiencia y por tecnología. Para las vacantes se utilizan los filtros de experiencia de LinkedIn Colombia; para los salarios, valores observados en vacantes reales publicadas en Colombia durante 2026. En todos los casos las cifras deben interpretarse como evidencia observacional y no como estadísticas oficiales del mercado laboral colombiano.

\begin{figure}[H]
    \centering
    \includegraphics[width=0.88\textwidth]{figuras/vacantes_junior_colombia.png}
    \caption{Vacantes Junior por tecnología en Colombia durante el último mes.}
    \label{fig:vacantes-junior}

    \vspace{0.2cm}
    \begin{minipage}{0.92\textwidth}
        \footnotesize
        \textit{Fuente y metodología:} resultados públicos de búsqueda en LinkedIn Colombia, con corte al 30 de septiembre de 2026 y publicaciones del último mes. Para el nivel Junior se utilizó el filtro de experiencia \textit{Entry level}. Los valores corresponden a resultados de búsqueda por palabra clave y no representan una estadística oficial de empleo ni garantizan que todas las publicaciones sean vacantes únicas.
    \end{minipage}
\end{figure}

\begin{figure}[H]
    \centering
    \includegraphics[width=0.88\textwidth]{figuras/vacantes_senior_colombia.png}
    \caption{Vacantes Senior por tecnología en Colombia durante el último mes.}
    \label{fig:vacantes-senior}

    \vspace{0.2cm}
    \begin{minipage}{0.92\textwidth}
        \footnotesize
        \textit{Fuente y metodología:} resultados públicos de búsqueda en LinkedIn Colombia, con corte al 30 de septiembre de 2026 y publicaciones del último mes. Para aproximar el nivel Senior se utilizó el filtro \textit{Mid-Senior level}; la gráfica representa la presencia de cada tecnología en publicaciones orientadas a experiencia media y alta, no un conteo oficial de puestos Senior únicos.
    \end{minipage}
\end{figure}

\begin{figure}[H]
    \centering
    \includegraphics[width=0.88\textwidth]{figuras/salarios_junior_colombia.png}
    \caption{Salario mensual observado en vacantes Junior asociadas al stack tecnológico en Colombia.}
    \label{fig:salarios-junior}

    \vspace{0.2cm}
    \begin{minipage}{0.92\textwidth}
        \footnotesize
        \textit{Fuente y metodología:} valores observados en vacantes reales publicadas en Colombia durante 2026 que incluyen las tecnologías analizadas entre sus requisitos % TODO: \cite{...} de cada vacante
        . No representan el salario promedio de cada tecnología: la remuneración corresponde al cargo completo. Cuando la publicación presentó un rango se utilizó su punto medio, únicamente para la representación gráfica. La vacante de Kafka estaba clasificada como Junior--Mid, por lo que su valor es una referencia observada y no un promedio Junior del mercado.
    \end{minipage}
\end{figure}

\begin{figure}[H]
    \centering
    \includegraphics[width=0.88\textwidth]{figuras/salarios_senior_colombia.png}
    \caption{Salario mensual observado en vacantes Senior asociadas al stack tecnológico en Colombia.}
    \label{fig:salarios-senior}

    \vspace{0.2cm}
    \begin{minipage}{0.92\textwidth}
        \footnotesize
        \textit{Fuente y metodología:} valores observados en vacantes reales publicadas en Colombia durante 2026 para perfiles de experiencia avanzada que incluyen Spring Boot, Angular, PostgreSQL o Kafka en su stack % TODO: \cite{...} de cada vacante
        . No deben interpretarse como salarios promedio por tecnología, sino como referencias de cargos reales asociados a esas herramientas. Cuando la publicación expresó un intervalo se utilizó su punto medio, únicamente para facilitar la comparación visual.
    \end{minipage}
\end{figure}

\textbf{Interpretación por nivel.} Las gráficas de vacantes reflejan la \textbf{presencia de cada tecnología en publicaciones laborales}, mientras que las salariales muestran \textbf{remuneraciones observadas en cargos reales asociados al stack}; por eso una mayor cantidad de ofertas no implica un salario superior. PostgreSQL aparece como requisito transversal en perfiles backend, de datos, DevOps y full stack, mientras que Kafka se concentra en un subconjunto menor de posiciones orientadas a sistemas distribuidos, integración y arquitecturas basadas en eventos. Experiencia, nivel de inglés, modalidad de contratación, tamaño de la organización y combinación de tecnologías pueden producir diferencias significativas entre ofertas.

\begin{table}[H]
\centering
\caption{Criterio utilizado para clasificar la información laboral por nivel.}
\label{tab:criterios-mercado-laboral}
\small
\renewcommand{\arraystretch}{1.25}
\begin{tabular}{p{3.0cm} p{4.3cm} p{6.4cm}}
\toprule
\textbf{Variable} & \textbf{Criterio utilizado} & \textbf{Interpretación} \\
\midrule
Vacantes Junior & LinkedIn: \textit{Entry level} & Publicaciones asociadas a perfiles de entrada o menor experiencia. \\
Vacantes Senior & LinkedIn: \textit{Mid-Senior level} & Publicaciones asociadas a perfiles con experiencia media o avanzada. \\
Salarios Junior & Vacantes reales publicadas en Colombia durante 2026 & Salarios observados; no corresponden a un promedio nacional por tecnología. \\
Salarios Senior & Vacantes reales publicadas en Colombia durante 2026 & Salarios observados en roles avanzados asociados a las tecnologías analizadas. \\
\bottomrule
\end{tabular}
\end{table}
```

### Cambios respecto al borrador original

- `\subsection*` pasa a un único `\subsubsection` numerado dentro de §2.5, y la «Interpretación» y la tabla quedan como párrafo y tabla de esa subsubsección.
- Rutas `figuras/...` para que `\includegraphics` las encuentre desde `docs/informe/`.
- Se quitan las líneas «Archivo de imagen: …», que eran notas de trabajo.
- Anchura de las figuras a `0.88\textwidth`, como la gráfica salarial que ya tiene §2.5.
- Del preámbulo del borrador solo se necesitan `float`, `booktabs`, `array` y `graphicx`, que `main.tex` ya carga; `caption` no hace falta porque el bloque no usa `\captionsetup`.
- Se marcan con `% TODO` los dos lugares donde faltan las citas de las vacantes (punto 1).
