# Trabajo con asistentes de IA

[← Índice de la wiki](../Home.md)

## Algoritmo para un asistente de IA

1. Identificar la HU y comprobar que existe su issue; si no existe, proponer crearlo con etiquetas y milestone completos.
2. Crear la rama `<tipo>/<HU-###>-<slug>` desde `main` actualizado.
3. Implementar según la página [Trabajo con asistentes de IA](trabajo-con-ia.md) y hacer commits con el formato de 10.2.
4. Ejecutar las pruebas y `scripts/verify-architecture.sh`.
5. Abrir el PR con la plantilla, `Closes #<n>`, las etiquetas del issue y el milestone.
6. Entregar el reporte de la página [Trabajo con asistentes de IA](trabajo-con-ia.md).
7. **No** fusionar su propio PR ni marcar la HU como terminada sin verificar todos los criterios.

Si el issue, las etiquetas o el milestone faltan o son ambiguos, el asistente pregunta en lugar de inventarlos.

## Antes de cambiar código

Un asistente debe:

1. Leer `CLAUDE.md` y las páginas de la wiki que indica su tabla "Qué leer según la tarea".
2. Identificar la HU, sus criterios de aceptación y su issue.
3. Identificar el servicio propietario de los datos involucrados.
4. Revisar los contratos y los ADR existentes antes de crear uno nuevo.
5. Mantener el flujo coreografiado definido por la arquitectura.

## Prohibiciones

Un asistente **no debe**:

- crear una base de datos compartida ni acceder a la base de otro servicio;
- reemplazar Kafka por llamadas REST entre servicios, ni añadir un orquestador central;
- publicar eventos desde PostgreSQL ni hacer que Kafka escriba en PostgreSQL;
- implementar Transactional Outbox, Saga, CQRS, Event Sourcing, Circuit Breaker, tracing distribuido completo ni Kubernetes;
- añadir un endpoint de consulta de pagos ni otros endpoints no listados en la página [API REST](../03-contratos/api-rest.md) sin una decisión explícita;
- usar `OrderUpdated` (el evento se llama `OrderStatusChanged`);
- crear entidades de dominio adicionales, ni ampliar el dominio hacia restaurante, inventario, delivery o autenticación;
- importar entidades JPA ni compartir repositorios entre servicios;
- cambiar nombres de tópicos, eventos o campos sin actualizar contratos y documentación;
- convertir Flyway, Testcontainers o CI en requisito obligatorio del prototipo;
- elegir versiones de dependencias sin consultar `docs/wiki/04-implementacion/versiones.md`;
- inventar rutas, credenciales, URLs o comportamientos no especificados;
- **editar `docs/informe/main.tex`**: es la fuente de verdad del diseño y solo Sara lo modifica. Un asistente propone el cambio (sección, texto actual, texto propuesto y motivo) y lo registra en la página [Divergencias informe–wiki](../02-arquitectura/divergencias-informe-wiki.md);
- corregir el informe y la wiki en direcciones opuestas: ante una contradicción se ajusta la wiki, que describe y deriva el informe;
- hacer commits directos a `main`, fusionar su propio PR o marcar una HU como terminada sin verificar todos sus criterios;
- abrir un PR que no corresponda a una HU: si el cambio no cabe en ninguna, se reabre la HU que lo cubre.

## Orden recomendado y reporte

```text
1. Leer HU, criterios y issue.
2. Crear la rama <tipo>/<HU-###>-<slug> (página [Git: ramas y commits](git-ramas-y-commits.md)).
3. Localizar el componente propietario y revisar contratos afectados.
4. Implementar dominio y aplicación; luego adaptadores HTTP, Kafka y base de datos.
5. Agregar pruebas y ejecutarlas.
6. Ejecutar scripts/verify-architecture.sh.
7. Actualizar contratos, OpenAPI, ADR y docs/wiki/06-backlog/estado.md cuando corresponda.
8. Abrir el PR con la plantilla y las etiquetas correctas.
9. Entregar el reporte siguiente.
```

```markdown
## HU-XXX — Resultado

### Implementado
- ...

### Archivos principales modificados
- `ruta/archivo`

### Criterios de aceptación
- [x] CA1 ...
- [x] CA2 ...

### Pruebas ejecutadas
- `comando` -> resultado

### Decisiones / notas
- ...
```

## Ante ambigüedad

1. **Detenerse y preguntar** si la decisión afecta un contrato, un tópico, un estado o el alcance.
2. Si es una decisión local (nombre de una clase privada), elegir la opción más simple coherente con EDA y anotarla en "Decisiones / notas".
3. Nunca resolver una contradicción en silencio: reportarla y proponer la corrección (página [Home de la wiki](../Home.md)).

## Plantilla de arranque de sesión

```text
Lee CLAUDE.md y las páginas de la wiki que indica su tabla "Qué leer según la tarea".
Implementa únicamente la HU-XXX.
Antes de escribir código, lista los criterios de aceptación, el servicio propietario,
los contratos afectados y la rama que vas a crear. Si algo es ambiguo, pregunta.
Sigue la página [Proceso de trabajo](README.md) para rama, commits, etiquetas y PR.
Al terminar, ejecuta las pruebas y verify-architecture.sh y entrega el reporte 16.3.
No marques la HU como terminada si algún criterio no está verificado.
```
