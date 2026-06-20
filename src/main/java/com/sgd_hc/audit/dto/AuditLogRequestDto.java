package com.sgd_hc.audit.dto;

import java.util.Map;

public record AuditLogRequestDto(
        String requestMethod,
        String requestPath,
        String ipAddress,
        String userAgent,
        Map<String, Object> requestBody,
        Map<String, Object> changesBefore,
        Map<String, Object> changesAfter,
        Integer responseStatus,
        String errorMessage
) {}
