package com.sgd_hc.documents.dto;

import com.sgd_hc.documents.entity.FieldType;

import java.util.Map;

public record FieldConfig(
    FieldType type,
    boolean required,
    String label,
    int order,
    Map<String, String> options,
    Map<String, FieldConfig> subSchema
) {
    public FieldConfig {
        if (options == null) {
            options = Map.of(); 
        }
    }

    public FieldConfig(FieldType type, boolean required, String label, int order) {
        this(type, required, label, order, Map.of());
    }

    public FieldConfig(FieldType type, boolean required, String label, int order, Map<String, FieldConfig> subSchema) {
        this(type, required, label, order, Map.of(), subSchema);
    }
}
