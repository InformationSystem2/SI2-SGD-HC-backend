package com.sgd_hc.users.dto;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record RoleCreateDto(
    @NotBlank(message = "Nombre de rol es requerido")
    String name,

    String description,

    Set<UUID> permissionsIds
) {}
