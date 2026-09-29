package com.foodflow.notification.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.foodflow.notification.application.NotificationQueryService;
import com.foodflow.notification.domain.Notification;
import com.foodflow.notification.domain.NotificationChannel;

/**
 * Contrato HTTP de {@code GET /orders/{id}/notifications} (HU-305), contra el esquema
 * {@code Notification} de {@code contracts/api/openapi.yaml}.
 *
 * <p>Monta solo el controlador y su manejador de errores, sin contexto de Spring Boot ni base de
 * datos: la consulta esta simulada. La lectura real desde Notification DB la cubre
 * {@code NotificationQueryIntegrationTests}.
 */
class NotificationControllerTests {

    private static final UUID PEDIDO = UUID.fromString("3f6c1e0a-6c9d-4f6f-9c4b-2a9f1d5e7b10");

    private final NotificationQueryService consultas = mock(NotificationQueryService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new NotificationController(consultas))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    @DisplayName("CA1: un pedido sin notificaciones responde 200 con lista vacia, no 404")
    void pedidoSinNotificaciones() throws Exception {
        when(consultas.notificacionesDelPedido(PEDIDO)).thenReturn(List.of());

        mockMvc.perform(get("/orders/{id}/notifications", PEDIDO))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("CA2 y CA3: varias notificaciones, cada una con canal, estado, contenido y fechas")
    void variasNotificacionesConSusCampos() throws Exception {
        UUID pago = UUID.randomUUID();
        Notification reciente = Notification.pendiente(PEDIDO, pago, NotificationChannel.EMAIL,
                "ana@foodflow.test", "Tu pago de 45.000,00 COP fue aprobado.");
        Notification anterior = Notification.pendiente(PEDIDO, null, NotificationChannel.EMAIL,
                "ana@foodflow.test", "Tu pago de 45.000,00 COP fue rechazado.");
        when(consultas.notificacionesDelPedido(PEDIDO)).thenReturn(List.of(reciente, anterior));

        mockMvc.perform(get("/orders/{id}/notifications", PEDIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(reciente.id().toString()))
                .andExpect(jsonPath("$[0].orderId").value(PEDIDO.toString()))
                .andExpect(jsonPath("$[0].paymentId").value(pago.toString()))
                .andExpect(jsonPath("$[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$[0].status").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].content").value("Tu pago de 45.000,00 COP fue aprobado."))
                .andExpect(jsonPath("$[0].attempts").value(0))
                .andExpect(jsonPath("$[0].failureCode").isEmpty())
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].updatedAt").exists())
                .andExpect(jsonPath("$[1].id").value(anterior.id().toString()))
                .andExpect(jsonPath("$[1].paymentId").isEmpty());
    }

    @Test
    @DisplayName("el destino sale siempre enmascarado, nunca el correo completo")
    void destinoEnmascarado() throws Exception {
        Notification notificacion = Notification.pendiente(PEDIDO, UUID.randomUUID(),
                NotificationChannel.EMAIL, "ana@foodflow.test", "Tu pago de 45.000,00 COP fue aprobado.");
        when(consultas.notificacionesDelPedido(PEDIDO)).thenReturn(List.of(notificacion));

        String cuerpo = mockMvc.perform(get("/orders/{id}/notifications", PEDIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].destination").value("a***@foodflow.test"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain("ana@foodflow.test");
        org.assertj.core.api.Assertions.assertThat("a***@foodflow.test").matches("^[^@][*]{3}@[^@]+$");
    }

    @Test
    @DisplayName("un identificador que no es UUID responde 400 en Problem Details, sin consultar la base")
    void identificadorInvalido() throws Exception {
        mockMvc.perform(get("/orders/{id}/notifications", "no-es-uuid")
                        .header("X-Correlation-Id", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://foodflow.local/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Solicitud invalida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("id: debe ser un UUID valido"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("11111111-1111-1111-1111-111111111111"));

        verifyNoInteractions(consultas);
    }

    @Test
    @DisplayName("un fallo inesperado responde 500 sin detalle interno")
    void falloInesperado() throws Exception {
        when(consultas.notificacionesDelPedido(PEDIDO)).thenThrow(new IllegalStateException("conexion rota a jdbc:postgresql"));

        String cuerpo = mockMvc.perform(get("/orders/{id}/notifications", PEDIDO))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("la solicitud no pudo procesarse"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain("jdbc");
    }
}
