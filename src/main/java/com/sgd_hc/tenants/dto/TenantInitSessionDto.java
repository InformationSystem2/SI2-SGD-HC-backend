package com.sgd_hc.tenants.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO para iniciar la sesión de registro (Paso 1: Selección de Plan).
 * Devuelve un sessionToken que se usará en los siguientes pasos.
 */
public record TenantInitSessionDto(
        @NotBlank String selectedPlan,
        String billingCycle
) {}