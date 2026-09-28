# Stack y protocolos

[← Índice de la wiki](../Home.md)

| Capa | Tecnología |
|---|---|
| Frontend | Angular + TypeScript, publicado con Nginx |
| Entrada síncrona | API Gateway |
| Backend | Spring Boot + Java |
| Persistencia | PostgreSQL |
| Eventos | Apache Kafka |
| Contenerización | Docker Compose o Podman Compose |
| **Protocolo en el borde** | **REST/JSON sobre HTTPS** |
| **Protocolo interno** | **Eventos JSON sobre Apache Kafka** |
| Proveedor externo | HTTPS/REST |

Las versiones concretas se fijan en `docs/wiki/04-implementacion/versiones.md` (punto abierto A-6). Un asistente **no elige versiones por su cuenta**.
