package com.foodflow.payment.application;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodflow.payment.domain.Payment;
import com.foodflow.payment.domain.ProcessedEvent;
import com.foodflow.payment.infrastructure.messaging.PaymentEventPublisher;
import com.foodflow.payment.infrastructure.persistence.PaymentRepository;
import com.foodflow.payment.infrastructure.persistence.ProcessedEventRepository;

/**
 * Caso de uso de pago. Recibe ordenes ya validadas desde {@code infrastructure.messaging} y no
 * conoce Kafka ni JSON.
 *
 * <p>Resuelve el pago de forma determinista (ADR-10) y lo persiste en Payment DB, que es la
 * unica base que este servicio toca (regla arquitectonica 2). El resultado es transaccional e
 * independiente del estado del pedido: Order Service se entera por el evento de pago, nunca
 * leyendo esta tabla.
 *
 * <p>Tras el commit publica el resultado en {@code payments.events} (HU-203), y Order Service
 * y Notification Service reaccionan cada uno por su cuenta.
 *
 * <p><strong>Un evento, un pago (ADR-09, HU-601).</strong> El {@code eventId} del
 * {@code OrderCreated} consumido se registra en {@code processed_events} en la <strong>misma
 * transaccion local</strong> que el pago: o quedan las dos cosas o no queda ninguna. Si el
 * evento ya estaba registrado se ignora con {@code INFO} y no se cobra ni se publica nada, de
 * modo que una reentrega de Kafka no produce un segundo cobro.
 */
@Service
public class PaymentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    /** Identifica a este consumidor en {@code processed_events}. */
    static final String CONSUMIDOR = "payment-service.orders";

    private final PaymentRepository repositorio;
    private final ProcessedEventRepository procesados;
    private final TransactionReferences referencias;
    private final PaymentEventPublisher publicador;

    public PaymentApplicationService(PaymentRepository repositorio, ProcessedEventRepository procesados,
            TransactionReferences referencias, PaymentEventPublisher publicador) {
        this.repositorio = repositorio;
        this.procesados = procesados;
        this.referencias = referencias;
        this.publicador = publicador;
    }

    /**
     * Resuelve y persiste el pago del pedido descrito por la orden, y devuelve el pago vigente.
     *
     * <p><strong>Un pedido, un pago.</strong> Si el pedido ya tiene pago se devuelve ese mismo y
     * no se cobra de nuevo, de modo que un {@code OrderCreated} republicado con otro
     * {@code eventId} tampoco duplica nada (criterio 5). El {@code eventId} de esa reentrega
     * queda registrado igualmente, para no volver a evaluarla.
     *
     * <p>Dos consumidores del grupo no pueden entrar aqui a la vez para el mismo pedido: la clave
     * de particion es el {@code orderId} (regla 11, ADR-04), asi que todos los eventos de un
     * pedido van a la misma particion y la atiende un solo consumidor del grupo. El indice unico
     * de {@code order_id} y la clave primaria de {@code processed_events} quedan como ultima
     * garantia de la base; si llegaran a violarse, el fallo sube y lo trata el consumidor, no se
     * disimula aqui. Reintentarlo encontraria el pago ya escrito y volveria por una de las ramas
     * de arriba.
     *
     * <p>El contacto es dato personal y se registra enmascarado
     * ({@code docs/wiki/04-implementacion/convenciones.md}); el resto de la linea son los campos
     * de correlacion y el importe, que es lo que hace util la traza del pago.
     *
     * @return el pago vigente del pedido, o vacio si el evento ya se habia procesado
     */
    @Transactional
    public Optional<Payment> iniciarPago(StartPaymentCommand orden) {
        if (procesados.existsById(orden.eventId())) {
            log.info("Evento ya procesado, no se vuelve a cobrar. eventId={} orderId={} correlationId={}",
                    orden.eventId(), orden.orderId(), orden.correlationId());
            return Optional.empty();
        }

        Optional<Payment> yaCobrado = repositorio.findByOrderId(orden.orderId());
        if (yaCobrado.isPresent()) {
            Payment pago = yaCobrado.get();
            // Tampoco se vuelve a publicar: el resultado ya se anuncio cuando el pago se creo,
            // y repetirlo haria que Order y Notification lo procesaran dos veces.
            registrarEventoProcesado(orden);
            log.info("El pedido ya tenia pago, no se cobra ni se publica de nuevo. orderId={} paymentId={} status={} eventId={} correlationId={}",
                    orden.orderId(), pago.id(), pago.status(), orden.eventId(), orden.correlationId());
            return Optional.of(pago);
        }

        Payment pago = Payment.resolver(
                orden.orderId(),
                orden.amount(),
                orden.paymentToken(),
                referencias.nueva(orden.orderId(), Instant.now()));

        Payment guardado = repositorio.saveAndFlush(pago);
        registrarEventoProcesado(orden);
        publicador.publicarResultado(guardado, orden);
        log.info("Pago resuelto. orderId={} paymentId={} status={} amount={} {} reasonCode={} transactionReference={} eventId={} correlationId={} contacto={}",
                guardado.orderId(), guardado.id(), guardado.status(), guardado.amount(),
                orden.currency(), guardado.reasonCode(), guardado.transactionReference(),
                orden.eventId(), orden.correlationId(),
                ContactMasker.mask(orden.contact().destination()));
        return Optional.of(guardado);
    }

    /**
     * Registra el {@code eventId} en la misma transaccion que el efecto de negocio, y antes de
     * anotar la publicacion: si el registro choca con la clave primaria, no queda ni el pago ni
     * el evento publicado.
     */
    private void registrarEventoProcesado(StartPaymentCommand orden) {
        procesados.saveAndFlush(ProcessedEvent.de(orden.eventId(), CONSUMIDOR));
    }
}
