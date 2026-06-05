package com.sgd_hc.audit.dto;

import com.sgd_hc.audit.entity.enums.ActionType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponseDto(
        UUID id,
        UUID tenantId,
        UUID userId,
        String userEmail,
        String userName,
        ActionType actionType,
        String resourceType,
        String resourceId,
        String resourceName,
        String ipAddress,
        String userAgent,
        String requestMethod,
        String requestPath,
        Map<String, Object> requestBody,
        Map<String, Object> changesBefore,
        Map<String, Object> changesAfter,
        Integer responseStatus,
        String errorMessage,
        String integrityHash,
        OffsetDateTime createdAt,
        OffsetDateTime clientTime,
        UUID sessionId,
        String severity,
        Long executionTimeMs,
        boolean valid
) {}
