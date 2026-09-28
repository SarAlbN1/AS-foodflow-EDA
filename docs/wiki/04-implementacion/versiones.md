# Versiones de la línea base técnica

[← Índice de la wiki](../Home.md)

> **Estado: Propuesto** (punto abierto [A-6](../02-arquitectura/puntos-abiertos.md)). Verificado el **2026-09-27** en las fuentes oficiales de la última columna. Requiere revisión y aprobación de Sara antes de usarse en código; hasta entonces **ningún componente las consume todavía**.
>
> Un asistente de IA **no elige versiones por su cuenta**: usa las de esta tabla.

| Componente | Versión fijada | Fuente verificada | Nota |
|---|---|---|---|
| Java (LTS) | `25` | [api.adoptium.net/v3/info/available_releases](https://api.adoptium.net/v3/info/available_releases) (`most_recent_lts: 25`) | LTS vigente. Imagen sugerida `eclipse-temurin:25-jdk` |
| Spring Boot | `4.1.1` | [spring.io/projects/spring-boot](https://spring.io/projects/spring-boot) | GA vigente. Compatible con Spring for Apache Kafka 4.1.x |
| Spring for Apache Kafka | `4.1.1` | [spring.io/projects/spring-kafka](https://spring.io/projects/spring-kafka) | Matriz oficial: Spring Kafka 4.1.x ↔ Spring Boot 4.1.x ↔ `kafka-clients` 4.2.x. Es la línea 4.x que cita el informe |
| Herramienta de build | Maven `3.9.14` | [maven.apache.org/download.cgi](https://maven.apache.org/download.cgi) | Fijada por el *wrapper* de cada proyecto en HU-001 (`.mvn/wrapper/maven-wrapper.properties`, script del wrapper 3.3.4) |
| Angular | `22.2.0` | [registry.npmjs.org/@angular/core/latest](https://registry.npmjs.org/@angular/core/latest) · [angular.dev/reference/versions](https://angular.dev/reference/versions) | Línea con soporte activo |
| Node.js | `24.21.0` | [nodejs.org/en/about/previous-releases](https://nodejs.org/en/about/previous-releases) · [nodejs.org/dist/index.json](https://nodejs.org/dist/index.json) | Active LTS (*Krypton*). Cumple el rango de Angular 22 (`^22.22.3 \|\| ^24.15.0 \|\| ^26.0.0`) |
| PostgreSQL | `18.6` | [postgresql.org/support/versioning](https://www.postgresql.org/support/versioning/) | Mayor vigente en su minor actual. Imagen `postgres:18.6` |
| Apache Kafka (KRaft) | `4.3.1` | [kafka.apache.org/downloads](https://kafka.apache.org/downloads) | Release soportada (25-06-2026). Imagen oficial `apache/kafka:4.3.1`. Kafka 4.x es **solo KRaft**: ZooKeeper ya no existe |
| Nginx | `1.30.5` | [nginx.org/en/download.html](https://nginx.org/en/download.html) | Rama *stable*. Imagen `nginx:1.30.5-alpine` |

## Compatibilidad comprobada

- **Java 25 ↔ Spring Boot 4.1.1:** Spring Boot 4.x tiene línea base Java 17, por lo que Java 25 (LTS) queda dentro del rango soportado.
- **Spring Boot 4.1.1 ↔ Spring for Apache Kafka 4.1.1:** pareja declarada en la matriz oficial de Spring for Apache Kafka.
- **Spring for Apache Kafka 4.1.1 ↔ broker Kafka 4.3.1:** el cliente gestionado es `kafka-clients` 4.2.x; Kafka mantiene compatibilidad bidireccional cliente/broker, así que un cliente 4.2.x opera contra un broker 4.3.1.
- **Angular 22.2.0 ↔ Node.js 24.21.0:** `@angular/core@22.2.0` declara `engines.node = ^22.22.3 || ^24.15.0 || >=26.0.0`; Node 24.21.0 lo cumple y es la Active LTS. Angular 22 exige además TypeScript `>=6.0.0 <6.1.0`.

## Reglas

- Sin `latest`: toda imagen y dependencia usa una etiqueta fija de esta tabla.
- Cada cambio de versión va en un PR que actualiza esta tabla, con su fuente oficial y la fecha de verificación.
- Si una versión no se puede verificar en su fuente oficial, se deja como `_por definir_` y se reporta; no se inventa.
