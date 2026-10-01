package com.foodflow.payment.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.foodflow.payment.application.PaymentNotFoundException;
import com.foodflow.payment.application.PaymentQueryService;
import com.foodflow.payment.domain.Payment;
import com.foodflow.payment.domain.PaymentToken;

/**
 * HU-205: forma de {@code GET /orders/{id}/payment} y de sus errores, con la consulta simulada.
 * La lectura real de Payment DB la cubre {@code PaymentQueryIntegrationTests}.
 */
class PaymentControllerTests {

    private static final UUID PEDIDO = UUID.fromString("3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10");
    private static final String CORRELACION = "7d1c2b3a-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    private final PaymentQueryService consultas = mock(PaymentQueryService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PaymentController(consultas))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    @DisplayName("CA2: un pago aprobado devuelve estado, monto y referencia, sin motivo de rechazo")
    void pagoAprobado() throws Exception {
        Payment pago = Payment.resolver(PEDIDO, new BigDecimal("45900.00"), PaymentToken.PAY_OK, "TXN-HU205-OK");
        when(consultas.pagoDelPedido(PEDIDO)).thenReturn(pago);

        mockMvc.perform(get("/orders/{id}/payment", PEDIDO))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(pago.id().toString()))
                .andExpect(jsonPath("$.orderId").value(PEDIDO.toString()))
                .andExpect(jsonPath("$.status").value("APROBADO"))
                .andExpect(jsonPath("$.amount").value(45900.00))
                .andExpect(jsonPath("$.transactionReference").value("TXN-HU205-OK"))
                .andExpect(jsonPath("$.reasonCode").isEmpty())
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("CA2: un pago rechazado lleva su motivo y su referencia")
    void pagoRechazado() throws Exception {
        Payment pago = Payment.resolver(PEDIDO, new BigDecimal("45900.00"), PaymentToken.PAY_FAIL, "TXN-HU205-KO");
        when(consultas.pagoDelPedido(PEDIDO)).thenReturn(pago);

        mockMvc.perform(get("/orders/{id}/payment", PEDIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECHAZADO"))
                .andExpect(jsonPath("$.reasonCode").value(pago.reasonCode()))
                .andExpect(jsonPath("$.transactionReference").value("TXN-HU205-KO"));
    }

    @Test
    @DisplayName("CA3: sin pago todavia responde 404 NOT_FOUND en Problem Details, sin inventar un resultado")
    void sinPagoTodavia() throws Exception {
        when(consultas.pagoDelPedido(PEDIDO)).thenThrow(new PaymentNotFoundException(PEDIDO));

        mockMvc.perform(get("/orders/{id}/payment", PEDIDO).header("X-Correlation-Id", CORRELACION))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.correlationId").value(CORRELACION))
                .andExpect(jsonPath("$.detail").value("todavia no hay un pago registrado para el pedido " + PEDIDO));
    }

    @Test
    @DisplayName("un id que no es UUID responde 400 VALIDATION_ERROR y no consulta la base")
    void idInvalido() throws Exception {
        mockMvc.perform(get("/orders/{id}/payment", "no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(consultas);
    }
}
