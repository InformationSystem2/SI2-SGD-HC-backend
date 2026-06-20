package com.sgd_hc.documents.service;

import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.service.AuditableService;

import com.sgd_hc.documents.dto.DocumentTemplateRequestDto;
import com.sgd_hc.documents.dto.DocumentTemplateResponseDto;
import com.sgd_hc.documents.entity.DocumentTemplate;
import com.sgd_hc.documents.mapper.DocumentTemplateMapper;
import com.sgd_hc.documents.repository.DocumentTemplateRepository;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.PlanLimitValidator;
import com.sgd_hc.tenants.service.TenantResolverService;
import jakarta.persistence.EntityNotFoundException;
import static com.sgd_hc.security.utils.SecurityUtils.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentTemplateService implements AuditableService<UUID, DocumentTemplate> {

    private final DocumentTemplateRepository documentTemplateRepository;
    private final DocumentTemplateMapper     documentTemplateMapper;
    private final TenantResolverService      tenantResolverService;
    private final PlanLimitValidator         planLimitValidator;

    @Transactional
    @Auditable(resourceType = "DOCUMENT_TEMPLATE", actionType = ActionType.CREATE)
    public DocumentTemplateResponseDto create(DocumentTemplateRequestDto dto) {
        Set<String> authorities = currentAuthorities();
        validateCreateAttributePermissions(dto, authorities);

        Tenant tenant = tenantResolverService.resolve();
        planLimitValidator.checkDocumentTemplatesLimit(tenant.getId());

        DocumentTemplate template = documentTemplateMapper.toEntity(dto);
        template.setTenant(tenant);
        return documentTemplateMapper.toResponseDto(documentTemplateRepository.save(template), authorities);
    }

    @Transactional(readOnly = true)
    public List<DocumentTemplateResponseDto> getAllActive() {
        Set<String> authorities = currentAuthorities();
        Tenant tenant = tenantResolverService.resolve();
        return documentTemplateRepository
                .findByTenantIdAndIsActiveTrue(tenant.getId())
                .stream()
                .map(template -> documentTemplateMapper.toResponseDto(template, authorities))
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentTemplateResponseDto getById(UUID id) {
        return documentTemplateMapper.toResponseDto(findOrThrow(id), currentAuthorities());
    }

    @Transactional
    @Auditable(resourceType = "DOCUMENT_TEMPLATE", actionType = ActionType.UPDATE, idParamName = "id")
    public DocumentTemplateResponseDto update(UUID id, DocumentTemplateRequestDto dto) {
        Set<String> authorities = currentAuthorities();
        validateUpdateAttributePermissions(dto, authorities);

        DocumentTemplate template = findOrThrow(id);
        documentTemplateMapper.updateEntityFromDto(dto, template);
        return documentTemplateMapper.toResponseDto(documentTemplateRepository.save(template), authorities);
    }

    @Transactional
    @Auditable(resourceType = "DOCUMENT_TEMPLATE", actionType = ActionType.DELETE, idParamName = "id")
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

    private void validateCreateAttributePermissions(DocumentTemplateRequestDto dto, Set<String> authorities) {
        if (dto.name() != null) requireAuthority(authorities, "template:create:name");
        if (dto.description() != null) requireAuthority(authorities, "template:create:description");
    }

    private void validateUpdateAttributePermissions(DocumentTemplateRequestDto dto, Set<String> authorities) {
        if (dto.name() != null) requireAuthority(authorities, "template:update:name");
        if (dto.description() != null) requireAuthority(authorities, "template:update:description");
        if (dto.uiSchema() != null) requireAuthority(authorities, "template:update:ui_schema");
    }

    @Override
    public DocumentTemplate getEntity(UUID id) {
        return findOrThrow(id);
    }

    @Override
    public Map<String, Object> toAuditMap(DocumentTemplate entity) {
        return documentTemplateMapper.toAuditMap(entity);
    }    
}
