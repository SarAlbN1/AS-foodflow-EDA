# frontend/foodflow-web — Aplicación Angular

> **Estado:** esqueleto compilable (HU-001), sin funcionalidad de negocio. La funcionalidad la construyen las historias indicadas.

**Responsabilidad:** Interfaz para crear pedidos y consultar su estado y sus notificaciones. Se publica con Nginx. Solo consume APIs REST del API Gateway.

**Historias que lo construyen:** HU-001 (esqueleto), HU-501, HU-502, HU-504, HU-505

**Reglas que aplican:** Nunca accede a Kafka ni a PostgreSQL; sin URLs internas de los servicios.

## Construir, ejecutar y probar

Requisitos: Node.js 24.21.0 (`.nvmrc`) y npm. Angular 22.2.0, fijado de forma exacta en `package.json`. Versiones en [versiones.md](../../docs/wiki/04-implementacion/versiones.md).

Desde `frontend/foodflow-web/`:

| Acción | Comando |
|---|---|
| Instalar dependencias (primera vez) | `npm ci` |
| Compilar | `npm run build` (salida en `dist/foodflow-web/`) |
| Pruebas unitarias (Vitest) | `npm test -- --watch=false` |
| Ejecutar en desarrollo | `npm start` y abrir http://localhost:4200/ |
| Detener | `Ctrl+C` en la terminal |

La publicación con Nginx y la conexión con el API Gateway se añaden en HU-501 y HU-607.

Referencias: [`CLAUDE.md`](../../CLAUDE.md) · [Wiki](../../docs/wiki/Home.md)
