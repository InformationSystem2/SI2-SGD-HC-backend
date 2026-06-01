package com.sgd_hc.security.dto;

public record AuthResponseDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn
) {
    public AuthResponseDto(String accessToken, String refreshToken, Long expiresIn) {
        this(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
