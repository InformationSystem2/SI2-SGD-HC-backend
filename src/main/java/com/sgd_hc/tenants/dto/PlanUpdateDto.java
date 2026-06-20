package com.sgd_hc.tenants.dto;

import java.math.BigDecimal;
import java.util.Map;

public record PlanUpdateDto(
    String displayName,
    String description,
    BigDecimal priceMonthly,
    BigDecimal priceYearly,
    Integer cycleDays,
    Integer gracePeriodDays,
    Map<String, Long> limits,
    Map<String, Boolean> features
) {}
