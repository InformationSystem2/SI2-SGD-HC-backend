package com.sgd_hc.documents.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * DTO de entrada para crear un nuevo documento clínico.
 *
 * @param patientId        UUID del paciente al que pertenece el documento.
 * @param templateId       UUID de la plantilla usada como molde.
 * @param clinicalContent  Mapa JSON con las respuestas del médico (estructura libre).
 * @param issueDate        Fecha de emisión del documento.
 * @param expiryDate       Fecha de vencimiento (opcional).
 * @param fileUrl          URL del archivo adjunto (opcional).
 * @param isExternalSource Indica si el documento proviene de una fuente externa.
 */
public record DocumentRequestDto(

        @NotNull(message = "El paciente es obligatorio")
        UUID patientId,

        @NotNull(message = "La plantilla es obligatoria")
        UUID templateId,

        @NotNull(message = "El contenido clínico es obligatorio")
        Map<String, Object> clinicalContent,

        @NotNull(message = "La fecha de emisión es obligatoria")
        LocalDate issueDate,

        LocalDate expiryDate,

        String fileUrl,

        Boolean isExternalSource
) {}
