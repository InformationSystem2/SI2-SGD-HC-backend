package com.sgd_hc.sgd_hc.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequestDto(
        @NotBlank @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String password,
        String documentType,
        String documentNumber,
        String phone,
        String gender
) {}
