package com.sgd_hc.tenants.dto;

import java.time.LocalDate;

public record ChangePlanResponseDto(
    String previousPlan,
    String newPlan,
    LocalDate newEndDate,
    String message
) {}
