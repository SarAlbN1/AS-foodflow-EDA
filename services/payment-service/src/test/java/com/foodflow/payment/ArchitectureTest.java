package com.foodflow.payment;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guardas de arquitectura de Payment Service (HU-006). Se ejecutan con {@code ./mvnw test} y con
 * {@code bash scripts/verify-architecture.sh}; una violacion hace fallar la prueba con la clase
 * y la dependencia que la incumplen.
 *
 * <p>Solo analiza el codigo de produccion: las pruebas pueden usar clientes HTTP para ejercitar
 * el servicio.
 */
@AnalyzeClasses(packages = "com.foodflow.payment", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /*
     * Tipos por nombre y no por clase: asi la regla compila tambien en un servicio que todavia no
     * tiene Kafka en el classpath, y empieza a vigilar en cuanto lo tenga.
     */
    private static final String KAFKA_LISTENER = "org.springframework.kafka.annotation.KafkaListener";
    private static final String KAFKA_TEMPLATE = "org.springframework.kafka.core.KafkaTemplate";

    /** Clientes HTTP salientes: el de Spring (sincrono y reactivo) y los del JDK. */
    private static final String CLIENTE_HTTP = "(org[.]springframework[.]web[.]client[.](RestClient|RestTemplate)"
            + "|org[.]springframework[.]web[.]reactive[.]function[.]client[.]WebClient"
            + "|java[.]net[.]http[.]HttpClient|java[.]net[.]HttpURLConnection)([$].*)?";

    /** Regla 11 y convenciones: solo {@code infrastructure.messaging} habla con Kafka. */
    @ArchTest
    static final ArchRule kafkaListenerSoloEnMessaging = methods()
            .that().areAnnotatedWith(KAFKA_LISTENER)
            .should().beDeclaredInClassesThat().resideInAPackage("..infrastructure.messaging..")
            .as("@KafkaListener solo en infrastructure.messaging")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule kafkaTemplateSoloEnMessaging = noClasses()
            .that().resideOutsideOfPackage("..infrastructure.messaging..")
            .should().dependOnClassesThat().areAssignableTo(KAFKA_TEMPLATE)
            .as("KafkaTemplate solo en infrastructure.messaging");

    /** El dominio no conoce ni el borde HTTP ni la infraestructura. */
    @ArchTest
    static final ArchRule dominioIndependiente = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
            .as("el dominio no depende de api ni de infrastructure")
            .allowEmptyShould(true);

    /**
     * Regla 4: los servicios no se llaman por REST entre si. Payment Service no usa ningun cliente HTTP
     * saliente; solo Notification Service llama al proveedor externo (regla 7).
     */
    @ArchTest
    static final ArchRule sinClienteHttpSaliente = noClasses()
            .should().dependOnClassesThat().haveNameMatching(CLIENTE_HTTP)
            .as("Payment Service no usa clientes HTTP salientes");
}
