package com.sgd_hc.tenants.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePlanRequestDto(
        @NotBlank(message = "El nuevo plan es requerido")
        String newPlan,
        
        String paymentIntentId
) {}
