package com.sgd_hc.tenants.dto;

import com.sgd_hc.tenants.entity.SubscriptionPlan;
import com.sgd_hc.tenants.entity.SubscriptionStatus;
import jakarta.validation.constraints.Size;

public record TenantUpdateDto(
        @Size(max = 100) String name,
        @Size(max = 100) String email,
        @Size(max = 20)  String phone,
        @Size(max = 200) String address,
        SubscriptionPlan subscriptionPlan,
        SubscriptionStatus subscriptionStatus
) {}
