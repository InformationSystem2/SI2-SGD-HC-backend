package com.sgd_hc.tenants.dto;

public record PaymentIntentResponseDto(
        String clientSecret,
        long amount,
        String currency,
        String status
) {}
