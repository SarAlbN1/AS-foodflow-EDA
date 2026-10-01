package com.foodflow.payment.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodflow.payment.domain.Payment;
import com.foodflow.payment.infrastructure.persistence.PaymentRepository;

/**
 * Consulta del pago de un pedido (HU-205, opcional).
 *
 * <p>Solo lee Payment DB (criterio 4): no consulta Order Service ni ninguna otra base, y no
 * coordina el flujo, que sigue siendo por eventos. Un pedido tiene a lo sumo un pago
 * ({@code payments.order_id} es unico), asi que la busqueda por {@code orderId} es directa.
 */
@Service
public class PaymentQueryService {

    private final PaymentRepository pagos;

    public PaymentQueryService(PaymentRepository pagos) {
        this.pagos = pagos;
    }

    /**
     * El pago del pedido.
     *
     * @throws PaymentNotFoundException si todavia no hay pago registrado para ese pedido, ya sea
     *         porque el resultado aun no se proceso (consistencia eventual) o porque el pedido no
     *         existe: Payment Service no puede distinguirlo sin consultar otra base, y no debe.
     */
    @Transactional(readOnly = true)
    public Payment pagoDelPedido(UUID orderId) {
        return pagos.findByOrderId(orderId).orElseThrow(() -> new PaymentNotFoundException(orderId));
    }
}
