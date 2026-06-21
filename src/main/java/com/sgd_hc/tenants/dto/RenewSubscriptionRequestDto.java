package com.sgd_hc.tenants.dto;

import jakarta.validation.constraints.NotBlank;

public record RenewSubscriptionRequestDto(
        @NotBlank(message = "El plan es requerido")
        String plan,
        
        String paymentIntentId
) {}
