package com.sgd_hc.documents.dto;

import com.sgd_hc.documents.entity.DocumentStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

public record DocumentUpdateDto(
        @NotNull LocalDate issueDate,
        LocalDate expiryDate,
        Map<String, Object> clinicalContent,
        DocumentStatus status
) {}
