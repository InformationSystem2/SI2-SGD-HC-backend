package com.sgd_hc.tenants.dto;

/**
 * Respuesta después de iniciar la sesión de registro.
 */
public record TenantSessionResponseDto(
        String sessionToken,
        String message
) {}