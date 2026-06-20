package com.sgd_hc.tenants.dto;

import java.time.LocalDate;

public record RenewSubscriptionResponseDto(
    String plan,
    String billingCycle,
    LocalDate newStartDate,
    LocalDate newEndDate,
    String message
) {}
