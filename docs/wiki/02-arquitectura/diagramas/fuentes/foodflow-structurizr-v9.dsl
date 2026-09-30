workspace "FoodFlow - Arquitectura Orientada a Eventos" "Modelo C4 del prototipo FoodFlow." {

    !impliedRelationships false

    model {

        // =====================================================
        // PERSONAS
        // =====================================================

        cliente = person "Cliente" "Crea pedidos, consulta su estado y recibe notificaciones." {
            tags "Customer"
        }


        // =====================================================
        // ORGANIZACIÓN FOODFLOW
        // Elementos usados principalmente en System Landscape.
        // No forman parte del prototipo implementado.
        // =====================================================

        group "Organización FoodFlow" {

            personalOperaciones = person "Personal de Operaciones" "Atiende consultas e incidencias operativas relacionadas con pedidos, pagos y notificaciones." {
                tags "Staff"
            }

            analistaNegocio = person "Analista de Negocio" "Consulta indicadores del servicio para seguimiento del comportamiento del negocio." {
                tags "Staff"
            }

            backoffice = softwareSystem "Backoffice Operativo" "Sistema organizacional de apoyo para registrar y hacer seguimiento a incidencias. Fuera del alcance de implementación del prototipo." {
                tags "SupportSystem" "DesignedOnly"
            }

            analytics = softwareSystem "Analítica y Reportes" "Sistema organizacional para consultar indicadores y reportes. Fuera del alcance de implementación del prototipo." {
                tags "SupportSystem" "DesignedOnly"
            }


            // =====================================================
            // SISTEMA PRINCIPAL
            // =====================================================

            foodFlow = softwareSystem "FoodFlow" "Plataforma de pedidos que procesa pagos simulados y notificaciones mediante arquitectura orientada a eventos." {

                tags "PrimarySystem"


                // =================================================
                // FRONTEND
                // =================================================

                angularApp = container "Aplicación Angular" "Interfaz web para crear pedidos, aportar datos de contacto y consultar el estado." "Angular + TypeScript" {
                    tags "WebApp"
                }

                nginx = container "Nginx" "Servidor web y reverse proxy encargado de publicar los artefactos estáticos de Angular." "Nginx" {
                    tags "WebServer"
                }


                // =================================================
                // API GATEWAY
                // =================================================

                apiGateway = container "API Gateway" "Punto de entrada HTTP que enruta las solicitudes hacia los servicios de negocio." "Spring Cloud Gateway / REST JSON sobre HTTPS" {
                    tags "Gateway"
                }


                // =================================================
                // ORDER SERVICE
                // =================================================

                orderService = container "Order Service" "Gestiona la entidad Pedido y su ciclo de vida." "Spring Boot + Java" {

                    tags "Backend"

                    orderController = component "OrderController" "Expone los endpoints REST relacionados con pedidos." "Spring MVC"

                    orderApplicationService = component "OrderApplicationService" "Orquesta la creación del pedido y sus cambios de estado." "Spring Bean"

                    orderValidator = component "OrderValidator" "Valida datos del pedido, contacto y token de pago de prueba." "Jakarta Bean Validation"

                    orderRepository = component "OrderRepository" "Persiste y consulta la entidad Pedido." "Spring Data JPA"

                    orderEventPublisher = component "OrderEventPublisher" "Publica los eventos OrderCreated y OrderStatusChanged." "Spring for Apache Kafka"

                    orderEventConsumer = component "OrderEventConsumer" "Consume los resultados del pago y aplica idempotencia mediante eventId." "Spring for Apache Kafka"

                    processedEventRepository = component "ProcessedEventRepository" "Registra los eventId ya procesados para impedir efectos duplicados." "Spring Data JPA"
                }


                // =================================================
                // PAYMENT SERVICE
                // =================================================

                paymentService = container "Payment Service" "Consume OrderCreated y procesa el pago simulado del pedido." "Spring Boot + Java" {
                    tags "Backend"
                }


                // =================================================
                // NOTIFICATION SERVICE
                // =================================================

                notificationService = container "Notification Service" "Consume PaymentApproved y PaymentRejected en su propio grupo de consumidores y crea las notificaciones asociadas al resultado del pago." "Spring Boot + Java" {
                    tags "Backend"
                }


                // =================================================
                // APACHE KAFKA
                // =================================================

                kafka = container "Apache Kafka" "Broker de eventos utilizado para desacoplar productores y consumidores mediante eventos JSON." "Apache Kafka" {
                    tags "EventBroker"
                }


                // =================================================
                // BASES DE DATOS
                // =================================================

                ordersDb = container "Order DB" "Base de datos propietaria de Order Service. Persiste pedidos, snapshot de contacto y datos técnicos de idempotencia." "PostgreSQL" {
                    tags "Database"
                }

                paymentsDb = container "Payment DB" "Base de datos propietaria de Payment Service. Persiste pagos y datos técnicos de idempotencia." "PostgreSQL" {
                    tags "Database"
                }

                notificationsDb = container "Notification DB" "Base de datos propietaria de Notification Service. Persiste notificaciones y datos técnicos de idempotencia." "PostgreSQL" {
                    tags "Database"
                }
            }
        }


        // =====================================================
        // SISTEMAS EXTERNOS
        // =====================================================

        group "Servicios Externos" {

            proveedorNotificaciones = softwareSystem "Proveedor de Notificaciones" "Servicio externo encargado de entregar email, SMS o notificaciones push." {
                tags "ExternalSystem"
            }
        }


        // =====================================================
        // RELACIONES C1
        // =====================================================

        cliente -> foodFlow "Crea pedidos y consulta su estado"

        foodFlow -> proveedorNotificaciones "Solicita el envío de notificaciones"

        proveedorNotificaciones -> cliente "Entrega notificaciones"


        // =====================================================
        // RELACIONES SYSTEM LANDSCAPE
        // =====================================================

        cliente -> personalOperaciones "Solicita soporte ante una incidencia"

        personalOperaciones -> backoffice "Registra y consulta incidencias"

        analistaNegocio -> analytics "Consulta indicadores y reportes"


        // =====================================================
        // RELACIONES C2
        // =====================================================

        cliente -> angularApp "Usa" "HTTPS"

        nginx -> angularApp "Sirve los artefactos estáticos"

        angularApp -> apiGateway "Envía solicitudes" "REST/JSON sobre HTTPS"

        apiGateway -> orderService "Enruta operaciones de pedidos" "REST/JSON sobre HTTPS"

        apiGateway -> notificationService "Consulta notificaciones del pedido" "REST/JSON sobre HTTPS"


        // =====================================================
        // PERSISTENCIA
        //
        // Cada base se comunica únicamente con su servicio
        // propietario.
        //
        // Ninguna base de datos se conecta directamente a Kafka.
        // =====================================================

        orderService -> ordersDb "Lee y escribe pedidos y datos técnicos de idempotencia" "JDBC/JPA"

        paymentService -> paymentsDb "Lee y escribe pagos y datos técnicos de idempotencia" "JDBC/JPA"

        notificationService -> notificationsDb "Lee y escribe notificaciones y datos técnicos de idempotencia" "JDBC/JPA"


        // =====================================================
        // EVENTOS KAFKA
        // =====================================================

        orderService -> kafka "Publica OrderCreated y OrderStatusChanged" "Kafka / JSON"

        kafka -> orderService "Entrega PaymentApproved y PaymentRejected" "Kafka / JSON"


        paymentService -> kafka "Publica PaymentApproved o PaymentRejected" "Kafka / JSON"

        kafka -> paymentService "Entrega OrderCreated" "Kafka / JSON"


        notificationService -> kafka "Publica NotificationSent o NotificationFailed" "Kafka / JSON"

        // Abanico: Order Service y Notification Service consumen el
        // mismo resultado del pago en grupos distintos; Notification
        // no depende de OrderStatusChanged.
        kafka -> notificationService "Entrega PaymentApproved y PaymentRejected" "Kafka / JSON"


        // =====================================================
        // PROVEEDOR DE NOTIFICACIONES
        // =====================================================

        notificationService -> proveedorNotificaciones "Solicita el envío de la notificación" "REST/JSON sobre HTTPS"

        proveedorNotificaciones -> notificationService "Devuelve el resultado del envío" "HTTPS"


        // =====================================================
        // RELACIONES C3 - ORDER SERVICE
        // =====================================================

        apiGateway -> orderController "Invoca endpoints de pedidos" "REST/JSON sobre HTTPS"

        orderController -> orderApplicationService "Delega el caso de uso"

        orderApplicationService -> orderValidator "Solicita validación"

        orderApplicationService -> orderRepository "Persiste y consulta pedidos"

        orderApplicationService -> orderEventPublisher "Solicita publicar eventos"

        orderRepository -> ordersDb "Lee y escribe Pedido" "JDBC/JPA"


        orderEventPublisher -> kafka "Publica OrderCreated y OrderStatusChanged" "Kafka / JSON"

        kafka -> orderEventConsumer "Entrega PaymentApproved y PaymentRejected" "Kafka / JSON"


        orderEventConsumer -> processedEventRepository "Verifica y registra eventId"

        processedEventRepository -> ordersDb "Lee y escribe processed_events" "JDBC/JPA"

        orderEventConsumer -> orderApplicationService "Solicita actualizar el estado del pedido"



        // =====================================================
        // DEPLOYMENT ENVIRONMENT
        // DEVELOPMENT
        //
        // Se utiliza el modelo nativo de Structurizr:
        //
        // Deployment Node
        //     -> Container Instance
        //
        // Las relaciones entre las instancias se derivan
        // automáticamente de las relaciones de C2.
        // =====================================================

        development = deploymentEnvironment "Development" {


            // =================================================
            // DISPOSITIVO DEL CLIENTE
            // =================================================

            clientDevice = deploymentNode "Dispositivo del Cliente" {

                description "Equipo desde el cual el cliente accede a FoodFlow."
                technology "PC / portátil / dispositivo de usuario"
                tags "ClientDevice"


                browserNode = deploymentNode "Navegador Web" {

                    description "Entorno donde se ejecuta la SPA Angular."
                    technology "Chrome / Edge / Firefox"
                    tags "BrowserNode"

                    angularInstance = containerInstance angularApp
                }
            }


            // =================================================
            // SERVIDOR FOODFLOW
            // =================================================

            foodFlowHost = deploymentNode "Servidor FoodFlow" {

                description "Servidor Linux donde se ejecuta el prototipo FoodFlow mediante Docker Compose."
                technology "Linux Server"
                tags "HostNode"


                // =================================================
                // DOCKER COMPOSE
                // =================================================

                dockerRuntime = deploymentNode "Docker Compose" {

                    description "Entorno contenerizado donde se ejecutan los componentes de FoodFlow."
                    technology "Docker Engine + Docker Compose"
                    tags "DockerRuntime"


                    // =============================================
                    // NGINX
                    // =============================================

                    nginxNode = deploymentNode "nginx-web" {

                        description "Contenedor encargado de servir los artefactos estáticos de Angular."
                        technology "Docker Container / Nginx"
                        tags "WebDeployment"

                        nginxInstance = containerInstance nginx
                    }


                    // =============================================
                    // API GATEWAY
                    // =============================================

                    gatewayNode = deploymentNode "api-gateway" {

                        description "Contenedor que expone el punto de entrada REST del backend."
                        technology "Docker Container / Spring Cloud Gateway"
                        tags "GatewayDeployment"

                        apiGatewayInstance = containerInstance apiGateway
                    }


                    // =============================================
                    // ORDER SERVICE
                    // =============================================

                    orderNode = deploymentNode "order-service" {

                        description "Contenedor donde se ejecuta Order Service."
                        technology "Docker Container / Spring Boot / JVM"
                        tags "ServiceDeployment"

                        orderInstance = containerInstance orderService
                    }


                    // =============================================
                    // PAYMENT SERVICE
                    // =============================================

                    paymentNode = deploymentNode "payment-service" {

                        description "Contenedor donde se ejecuta Payment Service."
                        technology "Docker Container / Spring Boot / JVM"
                        tags "ServiceDeployment"

                        paymentInstance = containerInstance paymentService
                    }


                    // =============================================
                    // NOTIFICATION SERVICE
                    // =============================================

                    notificationNode = deploymentNode "notification-service" {

                        description "Contenedor donde se ejecuta Notification Service."
                        technology "Docker Container / Spring Boot / JVM"
                        tags "ServiceDeployment"

                        notificationInstance = containerInstance notificationService
                    }


                    // =============================================
                    // KAFKA
                    // =============================================

                    kafkaNode = deploymentNode "kafka" {

                        description "Contenedor que ejecuta Apache Kafka como broker de eventos."
                        technology "Docker Container / Apache Kafka"
                        tags "KafkaDeployment"

                        kafkaInstance = containerInstance kafka
                    }


                    // =============================================
                    // ORDER DATABASE
                    // =============================================

                    orderDbNode = deploymentNode "order-db" {

                        description "Contenedor PostgreSQL propietario de Order Service."
                        technology "Docker Container / PostgreSQL"
                        tags "DatabaseDeployment"

                        ordersDbInstance = containerInstance ordersDb
                    }


                    // =============================================
                    // PAYMENT DATABASE
                    // =============================================

                    paymentDbNode = deploymentNode "payment-db" {

                        description "Contenedor PostgreSQL propietario de Payment Service."
                        technology "Docker Container / PostgreSQL"
                        tags "DatabaseDeployment"

                        paymentsDbInstance = containerInstance paymentsDb
                    }


                    // =============================================
                    // NOTIFICATION DATABASE
                    // =============================================

                    notificationDbNode = deploymentNode "notification-db" {

                        description "Contenedor PostgreSQL propietario de Notification Service."
                        technology "Docker Container / PostgreSQL"
                        tags "DatabaseDeployment"

                        notificationsDbInstance = containerInstance notificationsDb
                    }
                }
            }
        }
    }



    // =========================================================
    // VISTAS
    // =========================================================

    views {


        // =====================================================
        // C4 NIVEL 1 - SYSTEM CONTEXT
        // =====================================================

        systemContext foodFlow "C1_Context" {

            description "FoodFlow y sus interacciones externas directas."

            include foodFlow
            include cliente
            include proveedorNotificaciones

            autoLayout lr

            title "C4 Nivel 1 - System Context - FoodFlow"

            properties {
                "structurizr.groups" "false"
            }
        }



        // =====================================================
        // C4 NIVEL 2 - CONTAINER DIAGRAM
        // =====================================================

        container foodFlow "C2_Containers" {

            description "Contenedores que conforman la solución FoodFlow."

            include *

            autoLayout lr

            title "C4 Nivel 2 - Container Diagram - FoodFlow"
        }



        // =====================================================
        // C4 NIVEL 3 - COMPONENT DIAGRAM
        // ORDER SERVICE
        // =====================================================

        component orderService "C3_OrderService" {

            description "Componentes internos del Order Service."

            include *

            autoLayout lr

            title "C4 Nivel 3 - Component Diagram - Order Service"
        }



        // =====================================================
        // C4 DYNAMIC DIAGRAM
        //
        // Representa el happy path PAY-OK.
        //
        // PAY-FAIL utiliza el mismo patrón, pero Payment Service
        // publica PaymentRejected y Order Service cambia el
        // estado del pedido a PAGO_RECHAZADO.
        //
        // Abanico (informe, sección 3): PaymentApproved se publica
        // una sola vez y lo consumen Order Service y Notification
        // Service en paralelo, cada uno en su grupo. Los dos
        // bloques internos son secuencias paralelas: Structurizr
        // numera ambas ramas a partir del mismo paso.
        //
        // Hay dos vistas con el mismo contenido y distinta
        // disposición: Dynamic_OrderFlow (la que cita el informe)
        // y Dynamic_OrderFlow_Horizontal.
        // =====================================================

        dynamic foodFlow "Dynamic_OrderFlow" {

            description "Caso de uso end-to-end: crear pedido, procesar pago simulado y notificar resultado."

            cliente -> angularApp "Confirma un pedido con PAY-OK y datos de contacto"
            angularApp -> apiGateway "Envía POST /orders con Idempotency-Key"
            apiGateway -> orderService "Enruta la creación del pedido"
            orderService -> ordersDb "Persiste Pedido con estado CREADO"
            orderService -> kafka "Publica OrderCreated"
            kafka -> paymentService "Entrega OrderCreated"
            paymentService -> paymentsDb "Persiste Pago con estado APROBADO"
            paymentService -> kafka "Publica PaymentApproved"
            {
                {
                    kafka -> orderService "Entrega PaymentApproved (grupo de Order)"
                    orderService -> ordersDb "Actualiza Pedido a PAGADO"
                    orderService -> kafka "Publica OrderStatusChanged"
                }
                {
                    kafka -> notificationService "Entrega PaymentApproved (grupo de Notification)"
                    notificationService -> notificationsDb "Persiste Notificación con estado PENDIENTE"
                    notificationService -> proveedorNotificaciones "Solicita el envío de la notificación"
                    proveedorNotificaciones -> notificationService "Devuelve resultado del envío"
                    notificationService -> notificationsDb "Actualiza Notificación a ENVIADA"
                    notificationService -> kafka "Publica NotificationSent"
                    proveedorNotificaciones -> cliente "Entrega la notificación al cliente"
                }
            }

            autoLayout lr

            title "C4 Dynamic - Flujo principal FoodFlow - PAY-OK"
        }


        dynamic foodFlow "Dynamic_OrderFlow_Horizontal" {

            description "Mismo flujo que Dynamic_OrderFlow, dispuesto en horizontal con más separación entre columnas."

            cliente -> angularApp "Confirma un pedido con PAY-OK y datos de contacto"
            angularApp -> apiGateway "Envía POST /orders con Idempotency-Key"
            apiGateway -> orderService "Enruta la creación del pedido"
            orderService -> ordersDb "Persiste Pedido con estado CREADO"
            orderService -> kafka "Publica OrderCreated"
            kafka -> paymentService "Entrega OrderCreated"
            paymentService -> paymentsDb "Persiste Pago con estado APROBADO"
            paymentService -> kafka "Publica PaymentApproved"
            {
                {
                    kafka -> orderService "Entrega PaymentApproved (grupo de Order)"
                    orderService -> ordersDb "Actualiza Pedido a PAGADO"
                    orderService -> kafka "Publica OrderStatusChanged"
                }
                {
                    kafka -> notificationService "Entrega PaymentApproved (grupo de Notification)"
                    notificationService -> notificationsDb "Persiste Notificación con estado PENDIENTE"
                    notificationService -> proveedorNotificaciones "Solicita el envío de la notificación"
                    proveedorNotificaciones -> notificationService "Devuelve resultado del envío"
                    notificationService -> notificationsDb "Actualiza Notificación a ENVIADA"
                    notificationService -> kafka "Publica NotificationSent"
                    proveedorNotificaciones -> cliente "Entrega la notificación al cliente"
                }
            }

            autoLayout lr 400 200

            title "C4 Dynamic - Flujo principal FoodFlow - PAY-OK (horizontal)"
        }



        // =====================================================
        // C4 DEPLOYMENT DIAGRAM
        //
        // Aquí NO se crean relaciones artificiales entre
        // deployment nodes.
        //
        // Structurizr reutiliza automáticamente las relaciones
        // existentes entre los containers del modelo estático
        // para dibujar las relaciones entre sus instancias.
        //
        // Layout vertical para evitar un diagrama ultrapanorámico.
        // =====================================================

        deployment foodFlow development "Deployment_Development" {

            description "Despliegue del prototipo FoodFlow en Development utilizando Docker Compose."

            include *

            autoLayout tb 160 80

            title "C4 Deployment - FoodFlow - Development"
        }



        // =====================================================
        // C4 SYSTEM LANDSCAPE
        // =====================================================

        systemLandscape "SystemLandscape" {

            description "Mapa de personas y sistemas relacionados con el ecosistema organizacional de FoodFlow."

            include *

            autoLayout lr

            title "C4 - System Landscape - Organización FoodFlow"

            properties {
                "structurizr.groups" "true"
            }
        }



        // =====================================================
        // ESTILOS
        // =====================================================

        styles {


            // =================================================
            // PERSONAS
            // =================================================

            element "Person" {
                shape Person
                background #2E7D32
                color #FFFFFF
                stroke #1B5E20
            }


            element "Customer" {
                background #2E7D32
                color #FFFFFF
                stroke #1B5E20
            }


            element "Staff" {
                background #16859B
                color #FFFFFF
                stroke #0E6677
            }



            // =================================================
            // SOFTWARE SYSTEMS
            // =================================================

            element "Software System" {
                background #1168BD
                color #FFFFFF
                stroke #0B4884
            }


            element "PrimarySystem" {
                background #1168BD
                color #FFFFFF
                stroke #0B4884
            }


            element "SupportSystem" {
                background #F57C00
                color #FFFFFF
                stroke #E65100
            }


            element "DesignedOnly" {
                border dashed
                opacity 70
            }


            element "ExternalSystem" {
                background #C62828
                color #FFFFFF
                stroke #8E0000
            }


            element "Group" {
                color #555555
            }



            // =================================================
            // CONTAINERS
            // =================================================

            element "Container" {
                background #438DD5
                color #FFFFFF
                stroke #2E6295
            }


            element "Component" {
                background #85BBF0
                color #000000
                stroke #5D82A8
            }


            element "Database" {
                shape Cylinder
                background #438DD5
                color #FFFFFF
                stroke #2E6295
            }


            element "EventBroker" {
                background #7E57C2
                color #FFFFFF
                stroke #5E35B1
            }


            element "WebApp" {
                shape WebBrowser
            }


            element "WebServer" {
                background #E8F5E9
                color #1B5E20
                stroke #43A047
            }


            element "Gateway" {
                background #FFF3E0
                color #E65100
                stroke #FB8C00
            }



            // =================================================
            // DEPLOYMENT NODES
            // =================================================

            element "Deployment Node" {
                background #FFFFFF
                color #333333
                stroke #607D8B
            }


            element "ClientDevice" {
                background #F5F5F5
                color #263238
                stroke #607D8B
            }


            element "BrowserNode" {
                background #E8F5E9
                color #1B5E20
                stroke #43A047
            }


            element "HostNode" {
                background #FAFAFA
                color #263238
                stroke #455A64
            }


            element "DockerRuntime" {
                background #E3F2FD
                color #0D47A1
                stroke #1976D2
            }


            element "WebDeployment" {
                background #F1F8E9
                color #33691E
                stroke #7CB342
            }


            element "GatewayDeployment" {
                background #FFF3E0
                color #E65100
                stroke #FB8C00
            }


            element "ServiceDeployment" {
                background #EAF4FE
                color #0D47A1
                stroke #64B5F6
            }


            element "KafkaDeployment" {
                background #F3E5F5
                color #4527A0
                stroke #7E57C2
            }


            element "DatabaseDeployment" {
                background #E8EAF6
                color #1A237E
                stroke #5C6BC0
            }



            // =================================================
            // RELACIONES
            // =================================================

            relationship "Relationship" {
                color #555555
                style dashed
                routing Orthogonal
            }
        }
    }
}