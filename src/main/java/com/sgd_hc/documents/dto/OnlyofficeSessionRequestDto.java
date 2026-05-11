package com.sgd_hc.documents.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record OnlyofficeSessionRequestDto(
        @NotNull(message = "El paciente es obligatorio") UUID patientId,
        @NotNull(message = "La fecha de emisión es obligatoria") LocalDate issueDate,
        String title,
        String fileUrl,
        OoDocType docType
) {}
