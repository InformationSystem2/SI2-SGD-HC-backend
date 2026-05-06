package com.sgd_hc.security.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequestDto(
        @NotBlank String username,
        @NotBlank String password
) {
}
