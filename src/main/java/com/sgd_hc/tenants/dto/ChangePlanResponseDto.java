package com.sgd_hc.tenants.dto;

import java.time.LocalDate;

public record ChangePlanResponseDto(
    String previousPlan,
    String newPlan,
    String billingCycle,
    LocalDate newEndDate,
    String message
) {}
