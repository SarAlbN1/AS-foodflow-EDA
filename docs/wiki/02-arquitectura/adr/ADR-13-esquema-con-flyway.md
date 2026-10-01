# ADR-13: El esquema de cada base lo crea su servicio con Flyway

**Estado:** Propuesto (pasa a Aprobado al integrarse el PR de HU-010)
**Fecha:** 2026-10-01
**Decisores:** Sara, Juan

## Contexto

Hasta HU-010, el esquema de cada base lo creaba PostgreSQL al inicializar el volumen: Compose montaba `infrastructure/postgres/<db>/01-schema.sql` en `docker-entrypoint-initdb.d` (HU-002), y cada servicio solo validaba el modelo (`spring.jpa.hibernate.ddl-auto=validate`).

Ese mecanismo tiene tres limitaciones:

- **El esquema no es del servicio, sino de la infraestructura.** La regla 2 hace a cada servicio único propietario de su base, pero su esquema vivía fuera de él.
- **Solo funciona una vez.** Los scripts se ejecutan al crear el volumen. Cualquier cambio exige recrearlo con `down -v`, y se pierden los datos.
- **No hay registro de versión.** Nada en la base dice qué esquema tiene, ni si coincide con el código desplegado.

El informe trata las migraciones versionadas como recomendables y opcionales, por si el tiempo lo permite (líneas 467, 719 y 791 de `main.tex`). HU-010 las implementa.

## Decisión

Cada servicio **migra su propia base con Flyway al arrancar**:

- Las migraciones viven en `services/<servicio>/src/main/resources/db/migration/`. `V1__esquema_inicial.sql` reproduce el modelo de HU-002 carácter a carácter, salvo `BEGIN`/`COMMIT`, porque Flyway ya envuelve cada migración en una transacción.
- **Compose deja de montar los scripts de inicialización**: hay una sola fuente del esquema por base. Los `01-schema.sql` se conservan como referencia histórica de HU-002.
- `spring.flyway.baseline-on-migrate=true` con `baseline-version=1`: una base creada con el mecanismo anterior se registra como V1 sin recrearse.
- Hibernate sigue en `validate`: valida contra lo que dejó Flyway y nunca modifica el esquema.
- En las pruebas, Flyway está desactivado por defecto (sin base no puede migrar) y lo activa el inicializador de pruebas cuando hay una base real o efímera.

**Consecuencia sobre `CLAUDE.md` §5**: Flyway deja de ser opcional, porque es la única vía para crear el esquema. CI y Testcontainers siguen siendo opcionales.

## Opciones consideradas

### Opción A: Flyway como única fuente del esquema (elegida)

**Pros:** el servicio es dueño de su esquema de verdad (regla 2); los cambios futuros son migraciones nuevas (`V2__...`) sin borrar datos; `flyway_schema_history` registra la versión de cada base; una sola fuente, sin posibilidad de que dos definiciones diverjan.
**Contras:** el entorno ya no arranca sin Flyway, así que deja de ser opcional; añade dos dependencias por servicio, gestionadas por el BOM de Spring Boot.

### Opción B: Flyway en *baseline* y los scripts de inicialización montados

**Pros:** respeta la letra de §5 («Flyway es opcional»): el esquema seguiría naciendo sin Flyway.
**Contras:** dos fuentes del esquema. La *baseline* daría por buena cualquier diferencia entre el script y la migración, sin detectarla, y eso es justo lo que HU-010 venía a eliminar.

### Opción C: seguir sin migraciones

**Pros:** ningún cambio.
**Contras:** las tres limitaciones del contexto se mantienen.

## Consecuencias

- `CLAUDE.md` §5 y las páginas que decían «Flyway es opcional» (visión y alcance, DoR/DoD, trabajo con IA) pasan a describir Flyway como el mecanismo del esquema.
- Un cambio de esquema es una migración nueva en el servicio dueño de la base, nunca un cambio en `infrastructure/postgres/`.
- Verificado con bases nuevas (Testcontainers y volúmenes nuevos de Compose: `1 SQL esquema inicial`) y con bases existentes (`1 BASELINE`), en todos los casos con `smoke-test.sh` en OK.

Referencias: [HU-010](../../06-backlog/epicas/EP-00-base-tecnica-y-estandares.md) · [Persistencia](../../03-contratos/persistencia.md) · [ADR-03](ADR-03-una-base-postgresql-por-servicio.md)
