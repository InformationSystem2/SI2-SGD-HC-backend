package com.sgd_hc.workflow.dto;

import com.sgd_hc.workflow.entity.ReviewTaskOutcome;
import com.sgd_hc.workflow.entity.ReviewTaskStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewTaskResponseDto(
        UUID id,
        UUID documentId,
        UUID assignedToId,
        String assignedToName,
        ReviewTaskStatus status,
        ReviewTaskOutcome outcome,
        Integer priority,
        OffsetDateTime dueDate,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        UUID completedById,
        String completedByName
) {}
