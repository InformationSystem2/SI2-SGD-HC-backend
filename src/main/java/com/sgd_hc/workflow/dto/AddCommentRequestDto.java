package com.sgd_hc.workflow.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record AddCommentRequestDto(
        @NotBlank String commentText,
        UUID reviewTaskId  // opcional: asociar comentario a una tarea específica
) {}
