package com.sgd_hc.documents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Solicitud para registrar una edición del contenido clínico de un documento.
 *
 * @param clinicalContent Nuevo contenido clínico (JSONB) a aplicar al documento.
 * @param changeReason    Justificación médica/legal de la edición. Obligatorio.
 */
public record EditClinicalContentRequestDto(
        @NotNull  Map<String, Object> clinicalContent,
        @NotBlank @Size(max = 1000) String changeReason
) {}
