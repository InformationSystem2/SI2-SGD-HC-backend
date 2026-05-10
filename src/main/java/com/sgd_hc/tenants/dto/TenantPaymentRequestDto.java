package com.sgd_hc.tenants.dto;

import java.util.UUID;

public record TenantPaymentRequestDto(
        UUID tenantId,
        String paymentMethod, 
        Double amount
) {}
