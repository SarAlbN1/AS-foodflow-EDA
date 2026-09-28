# Estilo y flujo end-to-end

[← Índice de la wiki](../Home.md)

## Estilo

Event-Driven Architecture con topología **broker/coreografía**: no hay un componente que coordine todos los pasos; cada servicio reacciona de forma autónoma. Kafka aporta además características de *event streaming*: los eventos se conservan según la política de retención y pueden ser leídos por grupos independientes.

## Flujo canónico end-to-end

```text
Cliente -> Angular -> (REST/JSON) -> API Gateway -> POST /orders
   -> Order Service ---> Order DB
        | OrderCreated
        v
   Kafka [orders.events]
        v
   Payment Service ---> Payment DB        (PAY-OK / PAY-FAIL)
        | PaymentApproved | PaymentRejected
        v
   Kafka [payments.events]
        |                              |
        v                              v
   Order Service -> Order DB      Notification Service ---> Notification DB
        | OrderStatusChanged           | HTTPS/REST
        v                              v
   Kafka [orders.events]          Proveedor de Notificaciones
                                       |
                                       v
                               Notification Service
                                       | NotificationSent | NotificationFailed
                                       v
                               Kafka [notifications.events]
```
