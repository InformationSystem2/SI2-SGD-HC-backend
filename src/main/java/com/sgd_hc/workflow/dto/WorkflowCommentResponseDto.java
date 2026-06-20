package com.sgd_hc.workflow.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkflowCommentResponseDto(
        UUID id,
        UUID authorId,
        String authorName,
        UUID reviewTaskId,
        String commentText,
        OffsetDateTime createdAt
) {}
