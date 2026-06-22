package com.sgd_hc.tenants.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePlanPaymentIntentDto(
        @NotBlank(message = "El plan es requerido")
        String plan
) {}
