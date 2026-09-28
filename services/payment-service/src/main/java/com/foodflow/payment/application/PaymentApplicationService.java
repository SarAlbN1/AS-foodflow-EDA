package com.foodflow.payment.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de pago. Recibe ordenes ya validadas desde
 * {@code infrastructure.messaging} y no conoce Kafka ni JSON.
 *
 * <p><strong>Alcance de HU-201.</strong> Esta historia cubre el consumo de
 * {@code OrderCreated}: suscripcion, filtrado y extraccion de los datos requeridos. Todavia
 * no crea ni persiste el pago, que es el alcance de HU-202, ni publica su resultado, que es
 * el de HU-203 y HU-204. Aqui la orden validada solo se registra, de modo que el recorrido
 * {@code OrderCreated -> Payment} sea observable de extremo a extremo con el
 * {@code correlationId} del pedido.
 */
@Service
public class PaymentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    /**
     * Inicia el pago del pedido descrito por la orden.
     *
     * <p>El contacto es dato personal y se registra enmascarado. El importe no se registra
     * junto al contacto para no componer un perfil innecesario en los logs.
     */
    public void iniciarPago(StartPaymentCommand orden) {
        log.info("Pago iniciado. orderId={} eventId={} correlationId={} amount={} {} token={} contacto={}",
                orden.orderId(),
                orden.eventId(),
                orden.correlationId(),
                orden.amount(),
                orden.currency(),
                orden.paymentToken().valor(),
                ContactMasker.mask(orden.contact().destination()));
    }
}
