package com.sgd_hc.workflow.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StartReviewRequestDto(
        @NotNull UUID documentId,
        @NotEmpty List<UUID> reviewerIds,
        Integer priority,       // 1=alta, 2=media, 3=baja. Default: 3
        OffsetDateTime dueDate  // opcional
) {}
