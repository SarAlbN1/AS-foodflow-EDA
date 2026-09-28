package com.foodflow.notificationprovider;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code POST /v1/messages}. */
public record MessageRequest(
        @NotBlank String channel,
        @NotBlank String destination,
        @NotBlank String content,
        String correlationId) {
}
