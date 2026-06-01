package com.sgd_hc.security.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDto(
        @NotBlank String refreshToken
) {
}
