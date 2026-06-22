package com.sgd_hc.workflow.dto;

import com.sgd_hc.workflow.entity.WorkflowEventType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record WorkflowEventResponseDto(
        UUID id,
        WorkflowEventType eventType,
        UUID performedById,
        String performedByName,
        OffsetDateTime performedAt,
        Map<String, Object> detailsJson,
        String result,
        String comment,
        UUID documentId,
        String documentName
) {}
