package com.sgd_hc.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateDto(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String firstName,
        @NotBlank(message = "El apellido es obligatorio") @Size(max = 100) String lastName,
        @NotBlank(message = "El correo electrónico es obligatorio") @Email @Size(max = 50) String email,
        String phone,
        String gender,
        String documentType,
        String documentNumber
) {}
