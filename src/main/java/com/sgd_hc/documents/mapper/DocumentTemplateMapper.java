package com.sgd_hc.documents.mapper;

import com.sgd_hc.documents.dto.DocumentTemplateRequestDto;
import com.sgd_hc.documents.dto.DocumentTemplateResponseDto;
import com.sgd_hc.documents.entity.DocumentTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DocumentTemplateMapper {

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
        return new DocumentTemplateResponseDto(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getUiSchema() != null
                        ? template.getUiSchema().entrySet().stream()
                          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                        : null
        );
    }

}
