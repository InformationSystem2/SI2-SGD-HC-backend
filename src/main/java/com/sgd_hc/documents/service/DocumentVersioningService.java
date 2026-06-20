package com.sgd_hc.documents.service;

import com.sgd_hc.documents.dto.VersionHistoryResponseDto;
import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.entity.DocumentVersion;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.documents.repository.DocumentVersionRepository;
import com.sgd_hc.tenants.service.TenantResolverService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Servicio transaccional de versionado médico-legal (append-only).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVersioningService {

    private final DocumentRepository        documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final TenantResolverService     tenantResolverService;

    /**
     * Registra una versión inmutable (snapshot) a partir del estado actual de un documento
     * y luego incrementa su contador de versiones.
     */
    @Transactional
    public void recordVersion(Document doc, UUID authorId, String changeReason) {
        if (doc == null) return;

        log.info("Creando snapshot inmutable para el documento: docId={}, versionNumber={}", doc.getId(), doc.getVersionNumber());

        DocumentVersion snapshot = DocumentVersion.builder()
                .tenantId(doc.getTenant().getId())
                .document(doc)
                .versionNumber(doc.getVersionNumber())
                .authorId(authorId != null ? authorId : doc.getUploader().getId())
                .clinicalContent(doc.getClinicalContent())
                .status(doc.getStatus())
                .changeReason(changeReason != null ? changeReason : "Edición desde OnlyOffice")
                .fileUrl(doc.getFileUrl())
                .createdAt(OffsetDateTime.now())
                .build();

        documentVersionRepository.save(snapshot);

        // Incrementar el número de versión monotónico en el documento principal
        doc.setVersionNumber(doc.getVersionNumber() + 1);
        documentRepository.save(doc);
    }

    /**
     * Recupera el historial completo de versiones de un documento ordenado
     * de la más reciente a la más antigua. Filtra por tenant.
     */
    @Transactional(readOnly = true)
    public List<VersionHistoryResponseDto> getHistory(UUID documentId) {
        UUID tenantId = tenantResolverService.resolve().getId();

        // Validar pertenencia del documento al tenant
        documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Documento no encontrado: " + documentId));

        return documentVersionRepository
                .findByDocumentIdAndTenantIdOrderByVersionNumberDesc(documentId, tenantId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private VersionHistoryResponseDto toDto(DocumentVersion v) {
        return new VersionHistoryResponseDto(
                v.getId(),
                v.getVersionNumber(),
                v.getAuthorId(),
                v.getStatus(),
                v.getChangeReason(),
                v.getClinicalContent(),
                v.getCreatedAt(),
                v.getFileUrl()
        );
    }
}
