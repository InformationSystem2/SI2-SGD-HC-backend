package com.sgd_hc.tenants.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record PlanDto(
    UUID id,
    String name,
    String displayName,
    String description,
    BigDecimal priceMonthly,
    BigDecimal priceYearly,
    Integer cycleDays,
    Integer gracePeriodDays,
    Integer sortOrder,
    Map<String, Long> limits,
    Map<String, Boolean> features
) {}
