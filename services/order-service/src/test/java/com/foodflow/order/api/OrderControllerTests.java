package com.foodflow.order.api;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.foodflow.order.application.OrderApplicationService;
import com.foodflow.order.domain.NotificationChannel;
import com.foodflow.order.domain.Order;
import com.foodflow.order.domain.PaymentToken;
import com.foodflow.order.validation.OrderDraft;
import com.foodflow.order.validation.OrderValidationException;
import com.foodflow.order.validation.OrderValidationException.Violation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterios 4 y 6 de HU-101: {@code 201} con {@code Location} y errores en Problem Details.
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
        when(servicio.crearPedido(any(OrderDraft.class))).thenReturn(pedido);

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
        when(servicio.crearPedido(any(OrderDraft.class)))
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
    @DisplayName("un fallo no controlado responde 500 sin trazas de pila")
    void sinTrazas() throws Exception {
        when(servicio.crearPedido(any(OrderDraft.class))).thenThrow(new IllegalStateException("fallo interno"));

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("la solicitud no pudo procesarse"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
