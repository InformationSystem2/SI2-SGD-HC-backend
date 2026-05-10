package com.sgd_hc.tenants.dto;

import com.sgd_hc.tenants.entity.SubscriptionPlan;
import com.sgd_hc.tenants.entity.SubscriptionStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantResponseDto(
        UUID id,
        String name,
        String slug,
        String email,
        String phone,
        String address,
        SubscriptionPlan subscriptionPlan,
        SubscriptionStatus subscriptionStatus,
        LocalDate subscriptionStartDate,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
