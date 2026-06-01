//src/main/java/com/sgd_hc/documents/dto/DocumentResponseDto.java

package com.sgd_hc.documents.dto;

import com.sgd_hc.documents.entity.DocumentStatus;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * DTO de salida para un documento clínico.
 *
 * @param id               Identificador único del documento.
 * @param patientId        UUID del paciente asociado.
 * @param uploaderId       UUID del usuario que subió el documento.
 * @param templateId       UUID de la plantilla utilizada.
 * @param templateName     Nombre legible de la plantilla.
 * @param status           Estado actual del ciclo de vida del documento.
 * @param clinicalContent  Contenido clínico dinámico en formato JSON.
 * @param issueDate        Fecha de emisión.
 * @param expiryDate       Fecha de vencimiento (puede ser nula).
 * @param fileUrl          URL del archivo físico (puede ser nula).
 * @param isExternalSource Indica si es de fuente externa.
 */
public record DocumentResponseDto(
        UUID id,
        UUID patientId,
        String patientName,
        String patientDocumentNumber, // ← NUEVO CAMPO
        UUID uploaderId,
        String uploaderName,
        UUID templateId,
        String templateName,
        DocumentStatus status,
        Map<String, Object> clinicalContent,
        LocalDate issueDate,
        LocalDate expiryDate,
        String fileUrl,
        Boolean isExternalSource) {
}
