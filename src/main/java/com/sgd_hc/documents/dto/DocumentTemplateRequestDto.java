package com.sgd_hc.documents.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Objeto de transferencia de datos (DTO) que representa una solicitud para crear o actualizar una plantilla de documento.
 * Este registro se utiliza para encapsular los datos de entrada necesarios para las operaciones de plantilla de documento.
 * Incluye propiedades para el nombre, la descripción y el esquema de interfaz de usuario de la plantilla.
 * @param name El nombre de la plantilla de documento
 * @param description Una breve descripción de la plantilla de documento
 * @param uiSchema Un mapa que representa el esquema de la interfaz de usuario asociado con la plantilla de documento
 */
public record DocumentTemplateRequestDto(
        @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres")
        String name,

        @Size(max = 255, message = "La descripción no puede exceder los 255 caracteres")
        String description,

        @NotNull(message = "El esquema de la plantilla es obligatorio")
        Map<String, FieldConfig> uiSchema
) {
}
