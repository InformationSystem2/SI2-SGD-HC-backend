package com.sgd_hc.documents.service;

import com.sgd_hc.documents.dto.DocumentTemplateRequestDto;
import com.sgd_hc.documents.dto.DocumentTemplateResponseDto;
import com.sgd_hc.documents.entity.DocumentTemplate;
import com.sgd_hc.documents.mapper.DocumentTemplateMapper;
import com.sgd_hc.documents.repository.DocumentTemplateRepository;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.TenantResolverService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentTemplateService {

    private final DocumentTemplateRepository documentTemplateRepository;
    private final DocumentTemplateMapper     documentTemplateMapper;
    private final TenantResolverService      tenantResolverService;

    @Transactional
    public DocumentTemplateResponseDto create(DocumentTemplateRequestDto dto) {
        Tenant tenant = tenantResolverService.resolve();
        DocumentTemplate template = documentTemplateMapper.toEntity(dto);
        template.setTenant(tenant);
        return documentTemplateMapper.toResponseDto(documentTemplateRepository.save(template));
    }

    @Transactional(readOnly = true)
    public List<DocumentTemplateResponseDto> getAllActive() {
        Tenant tenant = tenantResolverService.resolve();
        return documentTemplateRepository
                .findByTenantIdAndIsActiveTrue(tenant.getId())
                .stream()
                .map(documentTemplateMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentTemplateResponseDto getById(UUID id) {
        return documentTemplateMapper.toResponseDto(findOrThrow(id));
    }

    @Transactional
    public DocumentTemplateResponseDto update(UUID id, DocumentTemplateRequestDto dto) {
        DocumentTemplate template = findOrThrow(id);
        documentTemplateMapper.updateEntityFromDto(dto, template);
        return documentTemplateMapper.toResponseDto(documentTemplateRepository.save(template));
    }

    @Transactional
    public void deactivate(UUID id) {
        DocumentTemplate template = findOrThrow(id);
        template.setIsActive(false);
        documentTemplateRepository.save(template);
    }

    private DocumentTemplate findOrThrow(UUID id) {
        Tenant tenant = tenantResolverService.resolve();
        return documentTemplateRepository
                .findByIdAndTenantId(id, tenant.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Plantilla no encontrada con id: " + id));
    }
}
