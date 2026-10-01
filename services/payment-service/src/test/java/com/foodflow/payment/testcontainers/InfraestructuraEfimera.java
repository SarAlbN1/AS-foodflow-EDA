package com.foodflow.payment.testcontainers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Kafka y Payment DB efimeros para las pruebas de integracion (HU-011, opcional).
 *
 * <p>Solo actua con {@code -Dfoodflow.testcontainers=true}, que pone el perfil Maven
 * {@code testcontainers}. Sin esa propiedad no hace nada y las pruebas siguen funcionando como
 * antes: contra el entorno Compose si estan sus variables, u omitidas si no.
 *
 * <p>Arranca los contenedores una sola vez por JVM y los comparte entre todos los contextos de
 * prueba; Testcontainers los destruye al terminar la ejecucion. Toma de la infraestructura del
 * repositorio todo lo que puede, para no duplicarlo:
 * <ul>
 *   <li>las imagenes, de {@code .env.example} ({@code POSTGRES_IMAGE} y {@code KAFKA_IMAGE});</li>
 *   <li>el esquema, de {@code infrastructure/postgres/payment-db/01-schema.sql};</li>
 *   <li>los topicos y sus DLQ con 3 particiones, como {@code infrastructure/kafka/scripts/create-topics.sh}.</li>
 * </ul>
 *
 * <p>Es una copia propia del patron, no una clase compartida: ningun servicio depende de codigo de
 * otro (regla arquitectonica 8). Se registra en {@code META-INF/spring.factories} de las pruebas.
 */
public class InfraestructuraEfimera implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /** Propiedad de sistema que activa los contenedores. */
    static final String ACTIVAR = "foodflow.testcontainers";

    private static final Path RAIZ = Path.of("../..");
    private static final Path ESQUEMA = RAIZ.resolve("infrastructure/postgres/payment-db/01-schema.sql");
    private static final List<String> TOPICOS = List.of("orders.events", "payments.events", "notifications.events");
    private static final int PARTICIONES = 3;

    private static Map<String, Object> propiedades;

    @Override
    public void initialize(ConfigurableApplicationContext contexto) {
        if (!Boolean.getBoolean(ACTIVAR)) {
            return;
        }
        contexto.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("testcontainers-hu011", arrancar()));
    }

    private static synchronized Map<String, Object> arrancar() {
        if (propiedades != null) {
            return propiedades;
        }
        Properties ejemplo = leerEnvExample();

        PostgreSQLContainer postgres = new PostgreSQLContainer(
                DockerImageName.parse(ejemplo.getProperty("POSTGRES_IMAGE")))
                .withDatabaseName("paymentdb")
                .withUsername("payment_user")
                .withPassword("payment_test")
                .withCopyFileToContainer(MountableFile.forHostPath(ESQUEMA), "/docker-entrypoint-initdb.d/01-schema.sql");
        KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(ejemplo.getProperty("KAFKA_IMAGE")));

        Startables.deepStart(postgres, kafka).join();
        crearTopicos(kafka.getBootstrapServers());

        propiedades = Map.of(
                "spring.datasource.url", postgres.getJdbcUrl(),
                "spring.datasource.username", postgres.getUsername(),
                "spring.datasource.password", postgres.getPassword(),
                "spring.kafka.bootstrap-servers", kafka.getBootstrapServers());
        return propiedades;
    }

    private static void crearTopicos(String bootstrap) {
        List<NewTopic> nuevos = new ArrayList<>();
        for (String topico : TOPICOS) {
            nuevos.add(new NewTopic(topico, PARTICIONES, (short) 1));
            nuevos.add(new NewTopic(topico + ".dlq", PARTICIONES, (short) 1));
        }
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap))) {
            admin.createTopics(nuevos).all().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("No se pudieron crear los topicos", e);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron crear los topicos", e);
        }
    }

    private static Properties leerEnvExample() {
        Properties ejemplo = new Properties();
        try (var lector = Files.newBufferedReader(RAIZ.resolve(".env.example"))) {
            ejemplo.load(lector);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer .env.example", e);
        }
        return ejemplo;
    }
}
