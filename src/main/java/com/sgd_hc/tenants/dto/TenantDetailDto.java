package com.sgd_hc.tenants.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sgd_hc.tenants.entity.SubscriptionPlan;
import com.sgd_hc.tenants.entity.SubscriptionStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantDetailDto(
        UUID id,
        String name,
        String slug,
        String email,
        String phone,
        String address,
        SubscriptionPlan subscriptionPlan,
        SubscriptionStatus subscriptionStatus,
        LocalDate subscriptionStartDate,
        LocalDate subscriptionEndDate,
        Map<String, Object> settings,
        AdminInfoDto admin,
        TenantStatsDto stats,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}