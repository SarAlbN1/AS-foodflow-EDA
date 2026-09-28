package com.foodflow.order;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.foodflow.order.application.IdempotencyConflictException;
import com.foodflow.order.application.OrderApplicationService;
import com.foodflow.order.application.OrderNotFoundException;
import com.foodflow.order.domain.IdempotencyKey;
import com.foodflow.order.domain.Order;
import com.foodflow.order.domain.OrderStatus;
import com.foodflow.order.infrastructure.persistence.IdempotencyKeyRepository;
import com.foodflow.order.infrastructure.persistence.OrderRepository;
import com.foodflow.order.validation.OrderDraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Arranque del contexto completo y persistencia real en Order DB (criterios 1, 2 y 5 de HU-101 y
 * criterios 1 a 3 de HU-102).
 *
 * <p>Necesita la base levantada ({@code docker compose -f infrastructure/compose/docker-compose.yml
 * up -d order-db}) y las variables de {@code .env}. Se omite cuando {@code ORDER_DB_URL} no esta
 * definida, para que {@code ./mvnw verify} siga funcionando sin infraestructura; el prototipo no
 * usa Testcontainers (es opcional, pagina de vision y alcance).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ORDER_DB_URL", matches = ".+")
class OrderServiceApplicationTests {

    @Autowired
    private OrderApplicationService servicio;

    @Autowired
    private OrderRepository repositorio;

    @Autowired
    private IdempotencyKeyRepository claves;

    @Test
    void contextLoads() {
        assertThat(servicio).isNotNull();
    }

    @Test
    @DisplayName("el pedido se persiste en Order DB con estado CREADO y se puede releer")
    void persisteElPedido() {
        String clave = "IT-" + UUID.randomUUID();
        UUID id = servicio.crearPedido(new OrderDraft(
                "PED-IT-" + UUID.randomUUID(), "ana@foodflow.test", "EMAIL",
                new BigDecimal("45000.00"), "PAY-OK"), UUID.randomUUID(), clave).id();

        try {
            Optional<Order> guardado = repositorio.findById(id);

            assertThat(guardado).isPresent();
            assertThat(guardado.get().status()).isEqualTo(OrderStatus.CREADO);
            assertThat(guardado.get().total()).isEqualByComparingTo("45000.00");
            assertThat(guardado.get().customerContact()).isEqualTo("ana@foodflow.test");
        } finally {
            claves.deleteById(clave);
            repositorio.deleteById(id);
        }
    }

    @Test
    @DisplayName("CA-5 y CA-6 de HU-107: la misma clave devuelve el pedido original y queda en idempotency_keys")
    void laMismaClaveNoCreaDosPedidos() {
        String clave = "IT-" + UUID.randomUUID();
        OrderDraft draft = new OrderDraft("PED-IT-" + UUID.randomUUID(), "ana@foodflow.test",
                "EMAIL", new BigDecimal("45000.00"), "PAY-OK");

        Order primero = servicio.crearPedido(draft, UUID.randomUUID(), clave);
        Order segundo = servicio.crearPedido(draft, UUID.randomUUID(), clave);

        try {
            assertThat(segundo.id()).isEqualTo(primero.id());
            assertThat(claves.findById(clave)).isPresent()
                    .get().extracting(IdempotencyKey::orderId).isEqualTo(primero.id());
        } finally {
            claves.deleteById(clave);
            repositorio.deleteById(primero.id());
        }
    }

    @Test
    @DisplayName("CA-3 de HU-107: la misma clave con otro cuerpo produce conflicto y no crea nada")
    void laMismaClaveConOtroCuerpoNoCreaNada() {
        String clave = "IT-" + UUID.randomUUID();
        Order original = servicio.crearPedido(new OrderDraft("PED-IT-" + UUID.randomUUID(),
                "ana@foodflow.test", "EMAIL", new BigDecimal("45000.00"), "PAY-OK"),
                UUID.randomUUID(), clave);

        try {
            OrderDraft otroCuerpo = new OrderDraft("PED-IT-" + UUID.randomUUID(),
                    "bruno@foodflow.test", "EMAIL", new BigDecimal("999.99"), "PAY-FAIL");

            assertThatExceptionOfType(IdempotencyConflictException.class)
                    .isThrownBy(() -> servicio.crearPedido(otroCuerpo, UUID.randomUUID(), clave));

            // La clave sigue apuntando al pedido original y no hay un segundo pedido.
            assertThat(claves.findById(clave)).get()
                    .extracting(IdempotencyKey::orderId).isEqualTo(original.id());
        } finally {
            claves.deleteById(clave);
            repositorio.deleteById(original.id());
        }
    }

    @Test
    @DisplayName("el pedido persistido se consulta por su identificador y uno inexistente no se encuentra")
    void consultaElPedidoPersistido() {
        String clave = "IT-" + UUID.randomUUID();
        UUID id = servicio.crearPedido(new OrderDraft(
                "PED-IT-" + UUID.randomUUID(), "ana@foodflow.test", "EMAIL",
                new BigDecimal("12500.50"), "PAY-FAIL"), UUID.randomUUID(), clave).id();

        try {
            Order consultado = servicio.consultarPedido(id);

            assertThat(consultado.id()).isEqualTo(id);
            assertThat(consultado.status()).isEqualTo(OrderStatus.CREADO);
            assertThat(consultado.total()).isEqualByComparingTo("12500.50");
            assertThatExceptionOfType(OrderNotFoundException.class)
                    .isThrownBy(() -> servicio.consultarPedido(UUID.randomUUID()));
        } finally {
            claves.deleteById(clave);
            repositorio.deleteById(id);
        }
    }
}
