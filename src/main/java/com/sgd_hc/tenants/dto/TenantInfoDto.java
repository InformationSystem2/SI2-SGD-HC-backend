package com.sgd_hc.tenants.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sgd_hc.tenants.entity.SubscriptionPlan;
import com.sgd_hc.tenants.entity.SubscriptionStatus;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantInfoDto(
    String name,
    String slug,
    String email,
    String phone,
    String address,
    SubscriptionPlan subscriptionPlan,
    SubscriptionStatus subscriptionStatus,
    LocalDate subscriptionStartDate,
    LocalDate subscriptionEndDate,
    String adminFirstName,
    String adminLastName,
    String adminEmail,
    String adminPhone,
    String logoUrl
) {}