package com.sgd_hc.users.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record PermissionCreateDto(
    @NotBlank(message = "Nombre es requerido")
    String name,
    
    @NotBlank(message = "Módulo es requerido")
    String module,
    
    @NotBlank(message = "Acción es requerida")
    String action,
    
    String description
) {
}