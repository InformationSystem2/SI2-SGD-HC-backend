package com.sgd_hc.security.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeDto(
        @NotBlank(message = "La contraseña actual es obligatoria") String currentPassword,
        @NotBlank(message = "La nueva contraseña es obligatoria") String newPassword
) {}
