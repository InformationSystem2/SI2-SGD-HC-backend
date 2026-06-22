package com.sgd_hc.workflow.dto;

import com.sgd_hc.workflow.entity.ReviewTaskOutcome;
import jakarta.validation.constraints.NotNull;

public record CompleteTaskRequestDto(
        @NotNull ReviewTaskOutcome outcome,
        String comment  // requerido si outcome=REJECTED, opcional si APPROVED
) {}
