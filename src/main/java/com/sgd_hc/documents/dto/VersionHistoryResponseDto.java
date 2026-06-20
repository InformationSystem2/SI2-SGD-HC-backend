package com.sgd_hc.documents.dto;

import com.sgd_hc.documents.entity.DocumentStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Representación de solo lectura de una versión histórica de un documento.
 */
public record VersionHistoryResponseDto(
        UUID id,
        Integer versionNumber,
        UUID authorId,
        DocumentStatus status,
        String changeReason,
        Map<String, Object> clinicalContent,
        OffsetDateTime createdAt,
        String fileUrl
) {}
