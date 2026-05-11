package com.sgd_hc.documents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO para crear un documento externo (archivo subido por el usuario).
 * No requiere plantilla ni contenido clínico estructurado.
 *
 * @param patientId  UUID del paciente al que se enlaza el documento.
 * @param fileUrl    URL/path del archivo ya almacenado en el servidor.
 * @param issueDate  Fecha de emisión del documento.
 * @param notes      Notas adicionales sobre el documento (opcional).
 */
public record ExternalDocumentRequestDto(

        @NotNull(message = "El paciente es obligatorio")
        UUID patientId,

        @NotBlank(message = "La URL del archivo es obligatoria")
        String fileUrl,

        @NotNull(message = "La fecha de emisión es obligatoria")
        LocalDate issueDate,

        String notes
) {}
