package com.foodflow.payment.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.foodflow.payment.application.PaymentQueryService;

/**
 * Consulta del pago de un pedido (HU-205, opcional): {@code GET /orders/{id}/payment}.
 *
 * <p>El API Gateway enruta aqui solo esta operacion, igual que {@code /orders/{id}/notifications}
 * va a Notification Service. Es de solo lectura: el pago se sigue resolviendo por eventos
 * ({@code OrderCreated} → {@code PaymentApproved}/{@code PaymentRejected}), nunca por esta API.
 */
@RestController
public class PaymentController {

    private final PaymentQueryService consultas;

    public PaymentController(PaymentQueryService consultas) {
        this.consultas = consultas;
    }

    @GetMapping("/orders/{id}/payment")
    public PaymentResponse pagoDelPedido(@PathVariable UUID id) {
        return PaymentResponse.from(consultas.pagoDelPedido(id));
    }
}
