package com.sgd_hc.documents.dto;

/**
 * Respuesta al abrir una sesión de edición OnlyOffice.
 * El frontend usa estos datos para configurar el componente document-editor.
 */
public record OnlyofficeSessionResponseDto(
        String documentId,
        String docKey,
        String documentServerUrl,
        String emptyDocUrl,
        String callbackUrl,
        String configToken,     // JWT firmado con el secreto compartido con OO DS
        String fileType,        // docx | xlsx | pptx
        String ooDocumentType   // word | cell | slide
) {}
