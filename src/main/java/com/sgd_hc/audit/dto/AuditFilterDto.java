package com.sgd_hc.audit.dto;

import com.sgd_hc.audit.entity.enums.ActionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditFilterDto(
        UUID tenantId,
        String userIdentifier,
        ActionType actionType,
        String resourceType,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo
) {}
