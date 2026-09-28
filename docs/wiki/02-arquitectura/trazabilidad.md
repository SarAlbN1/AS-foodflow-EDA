# Trazabilidad

[← Índice de la wiki](../Home.md)

## Reglas arquitectónicas a backlog

| Regla / elemento | Historias que lo implementan o verifican |
|---|---|
| Angular -> API Gateway -> servicios | HU-401, HU-402, HU-501, HU-502, HU-504, HU-505 |
| Order DB solo por Order Service | HU-101, HU-102, HU-104, HU-105, HU-107, HU-606 |
| Payment DB solo por Payment Service | HU-202, HU-606 |
| Notification DB solo por Notification Service | HU-301, HU-303, HU-304, HU-305, HU-606 |
| `orders.events` | HU-004, HU-103, HU-106, HU-201 |
| `payments.events` | HU-004, HU-203, HU-204, HU-104, HU-105, HU-301 |
| `notifications.events` | HU-004, HU-303, HU-304 |
| Coreografía sin orquestador | HU-103, HU-201, HU-203, HU-204, HU-301, HU-606 |
| Idempotencia (eventos y HTTP) | HU-601, HU-107 |
| Retry y DLQ | HU-602 |
| Correlación y logs estructurados | HU-403, HU-603 |
| Proveedor externo solo desde Notification | HU-302, HU-306 |
| Contenerización | HU-002, HU-606, HU-607 |
| Consistencia eventual visible en la UI | HU-502, HU-504, HU-505 |
| Contrato REST, OpenAPI y Problem Details | HU-404, HU-101 |
| Decisiones ADR registradas y documento alineado | HU-005, HU-008 |
| Atributos de calidad verificados | HU-608 |
| Estándares del repositorio y guardas | HU-009, HU-006 |
| Entregables académicos | HU-701 a HU-707 |

## Investigado, diseñado, implementado

Regla: **implementado ⊆ diseñado ⊆ investigado**. Se implementa un subconjunto de lo estudiado y todo lo implementado está diseñado y justificado.

| Elemento | Investigado | Diseñado | Implementado |
|---|:---:|:---:|:---:|
| Publish-Subscribe y Event Streaming (3 tópicos) | Sí | Sí | Sí |
| Database per Service | Sí | Sí | Sí |
| API Gateway (ruteo mínimo) | Sí | Sí | Sí |
| Repository y validación con Bean Validation | Sí | Sí | Sí |
| Idempotent Consumer y `Idempotency-Key` | Sí | Sí | Sí |
| Retry y Dead Letter Queue | Sí | Sí | Sí |
| Timeout y Retry hacia el proveedor | Sí | Sí | Sí |
| `correlationId` y logs estructurados | Sí | Sí | Sí |
| Health checks | Sí | Sí | Sí |
| Strategy (canal de notificación) | Sí | Sí | Un canal |
| Adapter (proveedor externo) | Sí | Sí | Sí |
| OpenAPI y Problem Details | Sí | Sí | Sí |
| Flyway, Testcontainers, CI automático | Sí | Sí | Opcional |
| Transactional Outbox | Sí | Documentado (ADR-08) | **No** |
| Circuit Breaker | Sí | Evolución futura | **No** |
| Distributed Tracing completo | Sí | Evolución futura | **No** |
| Saga, CQRS, Event Sourcing | Sí | No (YAGNI) | **No** |
| Kubernetes | Sí | Evolución futura | **No** |
| Seguridad completa (JWT, RBAC, TLS, ACL de Kafka) | Sí | Requisito de producción | **No** |

Los asistentes de IA no implementan ninguna fila marcada **No** salvo que un ADR nuevo cambie la decisión.

Esta misma matriz, con el motivo de cada nivel alcanzado, está en el informe técnico (HU-703), junto con la tabla de antipatrones evitados y la táctica que previene cada uno.
