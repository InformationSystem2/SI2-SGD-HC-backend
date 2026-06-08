package com.sgd_hc.documents.mapper;

import com.sgd_hc.documents.dto.DocumentTemplateRequestDto;
import com.sgd_hc.documents.dto.DocumentTemplateResponseDto;
import com.sgd_hc.documents.entity.DocumentTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;

@Component
public class DocumentTemplateMapper {

    private static final Set<String> ALL_READ_AUTHORITIES = Set.of(
            "template:read:id",
            "template:read:name",
            "template:read:description",
            "template:read:ui_schema"
    );

    public DocumentTemplate toEntity(DocumentTemplateRequestDto dto) {
        DocumentTemplate template = new DocumentTemplate();
        template.setName(dto.name());
        template.setDescription(dto.description());
        template.setUiSchema(dto.uiSchema());
        template.setIsActive(true);
        return template;
    }

    public void updateEntityFromDto(DocumentTemplateRequestDto dto, DocumentTemplate template) {
        if (dto.name() != null) template.setName(dto.name());
        if (dto.description() != null) template.setDescription(dto.description());
        if (dto.uiSchema() != null) template.setUiSchema(dto.uiSchema());
    }

    public DocumentTemplateResponseDto toResponseDto(DocumentTemplate template) {
        return toResponseDto(template, ALL_READ_AUTHORITIES);
    }

    public DocumentTemplateResponseDto toResponseDto(DocumentTemplate template, Set<String> userAuthorities) {
        return new DocumentTemplateResponseDto(
                userAuthorities.contains("template:read:id") ? template.getId() : null,
                userAuthorities.contains("template:read:name") ? template.getName() : null,
                userAuthorities.contains("template:read:description") ? template.getDescription() : null,
                userAuthorities.contains("template:read:ui_schema") && template.getUiSchema() != null
                        ? template.getUiSchema().entrySet().stream()
                          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                        : null
        );
    }

    public Map<String, Object> toAuditMap(DocumentTemplate entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("description", entity.getDescription());
        map.put("uiSchema", entity.getUiSchema());
        map.put("isActive", entity.getIsActive());
        map.put("tenantId", entity.getTenant() != null ? entity.getTenant().getId().toString() : null);
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }
}
