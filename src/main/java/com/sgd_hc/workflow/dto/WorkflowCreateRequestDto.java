package com.sgd_hc.workflow.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WorkflowCreateRequestDto(
        String title,
        String message,
        UUID assigneeId,
        Integer priority,
        OffsetDateTime dueDate,
        List<UUID> documentIds,
        List<TaskAssignmentDto> taskAssignments,
        Boolean sendEmailNotifications
) {
    public record TaskAssignmentDto(
            UUID documentId,
            List<UUID> reviewerIds
    ) {}
}
