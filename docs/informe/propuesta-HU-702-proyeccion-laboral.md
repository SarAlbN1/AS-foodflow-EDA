# Propuesta a `main.tex`: proyección laboral de la matriz de mercado — HU-702

`main.tex` es la fuente de verdad del diseño y **solo Sara lo edita** (PR #66). Esta página propone el cambio y no lo aplica. Trae el texto LaTeX listo para pegar.

Revisado el **2026-09-28** contra la versión de `main.tex` de #66 (§ «Matriz de Mercado Laboral vs Estilo y Stack», líneas 484-512). Si el documento cambia, conviene localizar la tabla por su título en lugar de fiarse de los números de línea.

## Punto de partida

La matriz ya tiene una columna **«Proyección sustentada»**, así que HU-702 no la crea: la contrasta con sus tres criterios.

| Criterio de HU-702 | Estado en la versión actual | Qué falta |
|---|---|---|
| **CA1**: cada tecnología tiene una tendencia (crece, estable o decrece) con fuente citada | **Parcial.** Hay fuentes, pero las etiquetas son «Estable/positiva», «Estable con soporte fuerte…», «Favorable» y «Favorable pero especializada», fuera del vocabulario del criterio. Además, ninguna usa una serie en el tiempo para PostgreSQL ni para Angular | Etiqueta del vocabulario y un criterio explícito para asignarla |
| **CA2**: se distinguen los datos observados de las inferencias | **No se cumple.** El dato y la conclusión van juntos en la misma frase, por ejemplo «GitHub reporta crecimiento sostenido… no se proyecta reemplazo abrupto» | Separar *Observado* e *Inferencia* dentro de la celda |
| **CA3**: no se incluyen cifras sin fuente | **Se cumple.** Todas las cifras de la matriz tienen `\cite`, y las verifiqué contra la fuente (tabla al final). Los totales 1.264 y 316 son cálculos sobre cifras citadas | Nada. Las cifras nuevas de esta propuesta traen su fuente |

## Criterio para asignar la tendencia

Para que la etiqueta sea reproducible y no una opinión, se asigna con esta regla:

| Etiqueta | Regla |
|---|---|
| **Crece** | Al menos una serie observada en el tiempo sube y ninguna de las citadas baja |
| **Estable** | Las series citadas se mueven en direcciones opuestas o casi no cambian |
| **Decrece** | Las series citadas bajan y ninguna sube |

**Ninguna** de las cuatro tecnologías cumple la regla de *decrece*. La única señal negativa encontrada es la de Java (TIOBE y aprendizaje en JetBrains), y por eso Java queda en *estable* y no en *crece*.

Los conteos de LinkedIn Colombia son **una sola observación** (22-09-2026), así que no muestran tendencia por sí solos. La tendencia se apoya en series globales. Para tener una tendencia local habría que repetir la misma búsqueda más adelante (por ejemplo, al cierre del Sprint 6) y comparar los dos puntos. Queda como sugerencia, no como parte de esta propuesta.

## Texto propuesto: columna «Proyección sustentada»

Se reemplaza solo la **quinta celda** de cada fila. La tabla conserva sus cinco columnas y su ancho.

**Java / Spring Boot**

```latex
\textbf{Tendencia: Estable.} \textit{Observado:} 4.º lugar del índice TIOBE en septiembre de 2026 con 7.54\%, 0.81 puntos menos que un año antes; +20.73\% interanual de contribuidores Java en GitHub en 2025; Spring es el framework web del 65\% de los encuestados de JetBrains, mientras quienes están aprendiendo Java bajan del 25\% (2024) al 19\% (2025). \cite{tiobe-2026,github-octoverse,jetbrains-java-2025} \textit{Inferencia:} las señales van en direcciones opuestas y son moderadas, lo que indica un ecosistema maduro que no se contrae; no se proyecta reemplazo del stack empresarial ni crecimiento acelerado.
```

**Angular / TypeScript**

```latex
\textbf{Tendencia: Crece (moderado).} \textit{Observado:} el uso de Angular en la encuesta de Stack Overflow pasa de 17.1\% (2024) a 18.2\% (2025); TypeScript sumó +66.63\% de contribuidores en GitHub en 2025 y en agosto de ese año fue el lenguaje con más contribuidores; JetBrains ubica a TypeScript entre los lenguajes con mayor potencial de crecimiento percibido. \cite{so-2024,so-2025,github-octoverse,jetbrains-ecosystem-2025} \textit{Inferencia:} el crecimiento de TypeScript es fuerte, pero el de Angular es de un punto porcentual; se clasifica como crecimiento moderado y no como una predicción de cuota para Angular.
```

**PostgreSQL**

```latex
\textbf{Tendencia: Crece.} \textit{Observado:} el uso de PostgreSQL en la encuesta de Stack Overflow pasa de 48.7\% (2024) a 55.6\% (2025) y es la base de datos más usada, más admirada y más deseada por tercer año consecutivo. \cite{so-2024,so-2025} \textit{Inferencia:} junto con el mayor conteo de vacantes de la matriz (574), sugiere continuidad de la demanda; no se interpreta como relación causal.
```

**Kafka / EDA**

```latex
\textbf{Tendencia: Crece (señal indirecta).} \textit{Observado:} el 90\% de 4.175 líderes TI encuestados por Confluent en 2025 aumenta su inversión en plataformas de data streaming. \cite{confluent-dsr-2025} \textit{Inferencia:} es una encuesta patrocinada por un proveedor, mide data streaming en general y no Kafka en exclusiva, y no hay serie histórica de vacantes; por eso la tendencia se declara con confianza baja y como señal, no como proyección exclusiva de Kafka.
```

### Referencia nueva

Las cifras de 2024 necesitan una entrada propia en la bibliografía:

```latex
\bibitem{so-2024}
Stack Overflow. \textit{2024 Developer Survey -- Technology}. Disponible en: \url{https://survey.stackoverflow.co/2024/technology}. Consultado: 28 de septiembre de 2026.
```

### Frase para el párrafo que sigue a la tabla (opcional)

Deja explícita la regla y la limitación, que es lo que un evaluador suele preguntar:

```latex
La tendencia de cada tecnología se asigna con una regla explícita: \textit{crece} si al menos una serie observada sube y ninguna de las citadas baja; \textit{estable} si las series se mueven en direcciones opuestas o casi no cambian; \textit{decrece} si bajan y ninguna sube. Los conteos de LinkedIn son una única observación y no permiten inferir tendencia local por sí solos; la tendencia se apoya en series globales.
```

## Verificación de las cifras (CA3)

Cada cifra de la columna, la actual y la propuesta, contrastada con su fuente el 2026-09-28:

| Cifra | Fuente | Resultado |
|---|---|---|
| Java +20.73\% de contribuidores | GitHub Octoverse 2025, tabla «What changed in 2025»: `Java ~174,705 20.73%` | Confirmada |
| TypeScript +66.63\% y n.º 1 en agosto de 2025 | Octoverse 2025: `TypeScript ~1,054,015 66.63%` y «August 2025 marks the first time TypeScript emerged as the most used language on GitHub» | Confirmada |
| TypeScript con mayor potencial de crecimiento | JetBrains Developer Ecosystem 2025: «TypeScript, Rust, and Go boast the highest perceived growth potential» | Confirmada |
| Java 4.º en TIOBE, 7.54\%, −0.81 | TIOBE, septiembre de 2026 | Confirmada |
| Spring 65\%; aprender Java 25\% → 19\% | JetBrains State of Java 2025 | Confirmada |
| 90\% de 4.175 líderes TI | Confluent 2025 Data Streaming Report | Confirmada |
| PostgreSQL más admirada y deseada desde 2023 | Stack Overflow 2025 | **Confirmada por fuentes secundarias** (ver nota) |
| PostgreSQL 48.7\% → 55.6\%; Angular 17.1\% → 18.2\% | Stack Overflow 2024 y 2025 | **Confirmadas por fuentes secundarias** (ver nota) |

**Nota.** Las secciones de uso y «admired/desired» de la encuesta de Stack Overflow se cargan con JavaScript y no se pudieron leer de forma automática. Los valores coinciden en varios análisis secundarios de la encuesta: el blog de Vonng sobre PostgreSQL en SO 2025, el de data-bene.io y las estadísticas de Angular de CMARIX. Antes de pegarlos, conviene abrir en el navegador `survey.stackoverflow.co/2024/technology` y `/2025/technology` y confirmar los cuatro porcentajes.
