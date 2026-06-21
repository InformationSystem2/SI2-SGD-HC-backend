package com.sgd_hc.tenants.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentIntentRequestDto(
        @NotBlank(message = "El token de sesión es requerido")
        String sessionToken
) {}
