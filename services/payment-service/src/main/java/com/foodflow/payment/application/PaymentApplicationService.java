package com.foodflow.payment.application;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodflow.payment.domain.Payment;
import com.foodflow.payment.infrastructure.persistence.PaymentRepository;

/**
 * Caso de uso de pago. Recibe ordenes ya validadas desde {@code infrastructure.messaging} y no
 * conoce Kafka ni JSON.
 *
 * <p>Resuelve el pago de forma determinista (ADR-10) y lo persiste en Payment DB, que es la
 * unica base que este servicio toca (regla arquitectonica 2). El resultado es transaccional e
 * independiente del estado del pedido: Order Service se entera por el evento de pago, nunca
 * leyendo esta tabla.
 *
 * <p><strong>Alcance de HU-202.</strong> Crea y persiste el pago. La publicacion de
 * {@code PaymentApproved} y {@code PaymentRejected} es HU-203 y HU-204; el registro en
 * {@code processed_events} por {@code eventId} (ADR-09) es HU-601.
 */
@Service
public class PaymentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private final PaymentRepository repositorio;
    private final TransactionReferences referencias;

    public PaymentApplicationService(PaymentRepository repositorio, TransactionReferences referencias) {
        this.repositorio = repositorio;
        this.referencias = referencias;
    }

    /**
     * Resuelve y persiste el pago del pedido descrito por la orden, y devuelve el pago vigente.
     *
     * <p><strong>Un pedido, un pago.</strong> Si el pedido ya tiene pago se devuelve ese mismo y
     * no se cobra de nuevo, de modo que reprocesar el mismo {@code OrderCreated} no duplica
     * nada (criterio 5).
     *
     * <p>Dos consumidores del grupo no pueden entrar aqui a la vez para el mismo pedido: la clave
     * de particion es el {@code orderId} (regla 11, ADR-04), asi que todos los eventos de un
     * pedido van a la misma particion y la atiende un solo consumidor del grupo. El indice unico
     * de {@code order_id} queda como ultima garantia de la base; si llegara a violarse, el fallo
     * sube y lo trata el consumidor, no se disimula aqui. Reintentarlo encontraria el pago ya
     * escrito y volveria por la rama de arriba.
     *
     * <p>El contacto es dato personal y se registra enmascarado
     * ({@code docs/wiki/04-implementacion/convenciones.md}); el resto de la linea son los campos
     * de correlacion y el importe, que es lo que hace util la traza del pago.
     */
    @Transactional
    public Payment iniciarPago(StartPaymentCommand orden) {
        Optional<Payment> yaCobrado = repositorio.findByOrderId(orden.orderId());
        if (yaCobrado.isPresent()) {
            Payment pago = yaCobrado.get();
            log.info("El pedido ya tenia pago, no se cobra de nuevo. orderId={} paymentId={} status={} eventId={} correlationId={}",
                    orden.orderId(), pago.id(), pago.status(), orden.eventId(), orden.correlationId());
            return pago;
        }

        Payment pago = Payment.resolver(
                orden.orderId(),
                orden.amount(),
                orden.paymentToken(),
                referencias.nueva(orden.orderId(), Instant.now()));

        Payment guardado = repositorio.saveAndFlush(pago);
        log.info("Pago resuelto. orderId={} paymentId={} status={} amount={} {} reasonCode={} transactionReference={} eventId={} correlationId={} contacto={}",
                guardado.orderId(), guardado.id(), guardado.status(), guardado.amount(),
                orden.currency(), guardado.reasonCode(), guardado.transactionReference(),
                orden.eventId(), orden.correlationId(),
                ContactMasker.mask(orden.contact().destination()));
        return guardado;
    }
}
