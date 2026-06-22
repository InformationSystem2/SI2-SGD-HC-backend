package com.sgd_hc.workflow.dto;

import com.sgd_hc.workflow.entity.ReviewTaskOutcome;
import com.sgd_hc.workflow.entity.ReviewTaskStatus;
import com.sgd_hc.workflow.entity.WorkflowStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WorkflowResponseDto(
        UUID id,
        String title,
        String message,
        UUID creatorId,
        String creatorName,
        String creatorUsername,
        UUID assigneeId,
        String assigneeName,
        WorkflowStatus status,
        Integer priority,
        OffsetDateTime dueDate,
        Boolean sendEmailNotifications,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<WorkflowDocumentDto> documents,
        List<WorkflowTaskDto> tasks,
        long pendingTaskCount,
        long completedTaskCount
) {
    public record WorkflowDocumentDto(
            UUID id,
            UUID documentId,
            String documentName,
            String documentStatus,
            OffsetDateTime addedAt
    ) {}

    public record WorkflowTaskDto(
            UUID id,
            UUID documentId,
            String documentName,
            UUID assignedToId,
            String assignedToName,
            Integer documentVersion,
            ReviewTaskStatus status,
            ReviewTaskOutcome outcome,
            Integer priority,
            OffsetDateTime dueDate,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt,
            UUID completedById,
            String completedByName
    ) {}
}
