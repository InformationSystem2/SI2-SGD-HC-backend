package com.sgd_hc.tenants.dto;

import com.sgd_hc.tenants.entity.SubscriptionPlan;
import com.sgd_hc.tenants.entity.SubscriptionStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantListItemDto(
        UUID id,
        String name,
        String slug,
        SubscriptionPlan subscriptionPlan,
        SubscriptionStatus subscriptionStatus,
        LocalDate subscriptionEndDate,
        String adminName,
        String adminEmail,
        int userCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}