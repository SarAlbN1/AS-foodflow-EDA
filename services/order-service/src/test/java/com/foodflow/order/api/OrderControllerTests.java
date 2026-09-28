package com.foodflow.order.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.foodflow.order.application.OrderApplicationService;
import com.foodflow.order.application.OrderNotFoundException;
import com.foodflow.order.domain.NotificationChannel;
import com.foodflow.order.domain.Order;
import com.foodflow.order.domain.PaymentToken;
import com.foodflow.order.validation.OrderDraft;
import com.foodflow.order.validation.OrderValidationException;
import com.foodflow.order.validation.OrderValidationException.Violation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de pedidos: criterios 4 y 6 de HU-101 ({@code 201} con {@code Location} y errores
 * en Problem Details) y criterios 1 y 3 de HU-102 ({@code 200} y {@code 404} en la consulta).
 *
 * <p>Monta solo el controlador y su manejador de errores, sin contexto de Spring Boot ni base de
 * datos: el caso de uso esta simulado, asi que la prueba no depende de infraestructura.
 */
class OrderControllerTests {

    private static final String CUERPO_VALIDO = """
            {
              "customerReference": "PED-0001",
              "customerContact": "ana@foodflow.test",
              "notificationChannel": "EMAIL",
              "total": 45000.00,
              "paymentToken": "PAY-OK"
            }
            """;

    private final OrderApplicationService servicio = mock(OrderApplicationService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new OrderController(servicio))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    @DisplayName("201 con Location y los campos del pedido")
    void creaPedido() throws Exception {
        Order pedido = Order.crear("PED-0001", NotificationChannel.EMAIL, "ana@foodflow.test",
                PaymentToken.PAY_OK, new BigDecimal("45000.00"));
        when(servicio.crearPedido(any(OrderDraft.class), any(UUID.class))).thenReturn(pedido);

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/orders/" + pedido.id()))
                .andExpect(jsonPath("$.id").value(pedido.id().toString()))
                .andExpect(jsonPath("$.status").value("CREADO"))
                .andExpect(jsonPath("$.total").value(45000.00))
                .andExpect(jsonPath("$.notificationChannel").value("EMAIL"))
                .andExpect(jsonPath("$.customerReference").value("PED-0001"))
                .andExpect(jsonPath("$.customerContact").value("ana@foodflow.test"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("400 en formato Problem Details cuando la entrada es invalida")
    void entradaInvalida() throws Exception {
        when(servicio.crearPedido(any(OrderDraft.class), any(UUID.class)))
                .thenThrow(new OrderValidationException(List.of(new Violation("total", "debe ser mayor que cero"))));

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "11111111-1111-1111-1111-111111111111")
                        .content(CUERPO_VALIDO.replace("45000.00", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://foodflow.local/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Solicitud invalida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("total: debe ser mayor que cero"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    @DisplayName("400 cuando el cuerpo no es interpretable")
    void cuerpoIlegible() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO.replace("45000.00", "\"cuarenta y cinco mil\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("GET /orders/{id} responde 200 con la misma representacion que la creacion")
    void consultaPedido() throws Exception {
        Order pedido = Order.crear("PED-0001", NotificationChannel.EMAIL, "ana@foodflow.test",
                PaymentToken.PAY_OK, new BigDecimal("45000.00"));
        when(servicio.consultarPedido(pedido.id())).thenReturn(pedido);

        mockMvc.perform(get("/orders/{id}", pedido.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pedido.id().toString()))
                .andExpect(jsonPath("$.status").value("CREADO"))
                .andExpect(jsonPath("$.total").value(45000.00))
                .andExpect(jsonPath("$.notificationChannel").value("EMAIL"))
                .andExpect(jsonPath("$.customerReference").value("PED-0001"))
                .andExpect(jsonPath("$.customerContact").value("ana@foodflow.test"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("GET /orders/{id} responde 404 en Problem Details si el pedido no existe")
    void pedidoInexistente() throws Exception {
        UUID id = UUID.fromString("3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10");
        when(servicio.consultarPedido(id)).thenThrow(new OrderNotFoundException(id));

        mockMvc.perform(get("/orders/{id}", id)
                        .header("X-Correlation-Id", "33333333-3333-3333-3333-333333333333"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://foodflow.local/problems/not-found"))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("no existe un pedido con id " + id))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").value("33333333-3333-3333-3333-333333333333"));
    }

    @Test
    @DisplayName("GET /orders/{id} responde 400 si el identificador no es un UUID")
    void identificadorInvalido() throws Exception {
        mockMvc.perform(get("/orders/{id}", "no-es-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("id: debe ser un UUID valido"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("un fallo no controlado responde 500 sin trazas de pila")
    void sinTrazas() throws Exception {
        when(servicio.crearPedido(any(OrderDraft.class), any(UUID.class))).thenThrow(new IllegalStateException("fallo interno"));

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("la solicitud no pudo procesarse"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
