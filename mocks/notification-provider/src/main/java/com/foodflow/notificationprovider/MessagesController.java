package com.foodflow.notificationprovider;

import java.util.UUID;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /v1/messages}: acepta el mensaje o simula el fallo que indique el destino. */
@RestController
class MessagesController {

    private static final Logger log = LoggerFactory.getLogger(MessagesController.class);

    private final MockProperties properties;
    private final FlakyAttempts flakyAttempts;

    MessagesController(MockProperties properties, FlakyAttempts flakyAttempts) {
        this.properties = properties;
        this.flakyAttempts = flakyAttempts;
    }

    @PostMapping("/v1/messages")
    ResponseEntity<?> send(@Valid @RequestBody MessageRequest request) throws InterruptedException {
        DeliveryMode mode = DeliveryMode.forDestination(request.destination());
        String masked = ContactMasker.mask(request.destination());

        switch (mode) {
            case FAIL -> {
                log.info("Envío rechazado (fail.test) destination={} correlationId={}", masked, request.correlationId());
                return unavailable("El destino simula un proveedor caído.");
            }
            case FLAKY -> {
                int attempt = flakyAttempts.register(request.destination());
                if (attempt <= properties.flakyFailures()) {
                    log.info("Fallo transitorio {}/{} (flaky.test) destination={} correlationId={}",
                            attempt, properties.flakyFailures(), masked, request.correlationId());
                    return unavailable("Fallo transitorio simulado, intento " + attempt + ".");
                }
            }
            case SLOW -> {
                log.info("Respuesta lenta {} (slow.test) destination={} correlationId={}",
                        properties.slowDelay(), masked, request.correlationId());
                Thread.sleep(properties.slowDelay());
            }
            case SUCCESS -> {
                // Sin simulación: se acepta el mensaje.
            }
        }

        String reference = "MOCK-" + UUID.randomUUID();
        log.info("Mensaje aceptado providerReference={} channel={} destination={} correlationId={}",
                reference, request.channel(), masked, request.correlationId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageResponse(reference));
    }

    private static ResponseEntity<ProblemDetail> unavailable(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
        problem.setTitle("Proveedor no disponible");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
