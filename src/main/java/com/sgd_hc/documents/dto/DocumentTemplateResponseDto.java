package com.sgd_hc.documents.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Objeto de transferencia de datos (DTO) que representa una respuesta para una plantilla de documento
 * Este registro se utiliza para encapsular los datos de salida de una plantilla de documento.
 * @param id El identificador único de la plantilla de documento
 * @param name El nombre de la plantilla de documento
 * @param description Una descripción breve de la plantilla de documento
 * @param uiSchema Un mapa que representa el esquema de la interfaz de usuario asociado con la plantilla de documento
 */
public record DocumentTemplateResponseDto(
        UUID id,
        String name,
        String description,
        Map<String, FieldConfig> uiSchema
) {
}
