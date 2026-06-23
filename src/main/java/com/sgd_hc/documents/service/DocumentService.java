package com.sgd_hc.documents.service;

import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.service.AuditableService;
import com.sgd_hc.workflow.entity.WorkflowEventType;
import com.sgd_hc.workflow.service.ReviewTaskService;
import com.sgd_hc.workflow.service.WorkflowEventService;
import com.sgd_hc.documents.dto.DocumentRequestDto;
import com.sgd_hc.documents.dto.DocumentResponseDto;
import com.sgd_hc.documents.dto.DocumentUpdateDto;
import com.sgd_hc.documents.dto.ExternalDocumentRequestDto;
import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.entity.DocumentStatus;
import com.sgd_hc.documents.entity.DocumentTemplate;
import com.sgd_hc.documents.mapper.DocumentMapper;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.documents.repository.DocumentTemplateRepository;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.patients.entity.ClinicalHistory;
import com.sgd_hc.patients.repository.PatientRepository;
import com.sgd_hc.patients.repository.ClinicalHistoryRepository;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.PlanFeatureValidator;
import com.sgd_hc.tenants.service.PlanLimitValidator;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import static com.sgd_hc.security.utils.SecurityUtils.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaTypeFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sgd_hc.documents.dto.OcrResultDto;
import com.sgd_hc.documents.entity.DocumentOcrMetadata;
import com.sgd_hc.documents.repository.DocumentOcrMetadataRepository;
import org.springframework.util.MimeType;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
//import Map;
import java.util.Objects;
import java.util.UUID;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.LinkedHashMap;


@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService implements AuditableService<UUID, Document> {

    private final DocumentRepository documentRepository;
    private final DocumentTemplateRepository documentTemplateRepository;

    private final PatientRepository             patientRepository;
    private final ClinicalHistoryRepository     clinicalHistoryRepository;
    private final DocumentMapper                documentMapper;
    private final TenantResolverService         tenantResolverService;
    private final OcrClientService              ocrClientService;
    private final DocumentOcrMetadataRepository ocrMetadataRepository;
    private final FileStorageService        fileStorageService;
    private final PlanLimitValidator         planLimitValidator;
    private final PlanFeatureValidator       planFeatureValidator;
    private final DocumentVersioningService     documentVersioningService;
    private final ReviewTaskService             reviewTaskService;
    private final WorkflowEventService          workflowEventService;


    // ── Documento basado en plaantilla ────────────────────────────────────────

    @Transactional
    @Auditable(resourceType = "DOCUMENT", actionType = ActionType.CREATE)    
    public DocumentResponseDto create(DocumentRequestDto dto) {
        Set<String> authorities = currentAuthorities();
        validateCreateAttributePermissions(dto, authorities);

        Tenant tenant = tenantResolverService.resolve();
        planLimitValidator.checkDocumentsLimit(tenant.getId());

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Paciente no encontrado con id: " + dto.patientId()));

        DocumentTemplate template = documentTemplateRepository
                .findByIdAndTenantId(dto.templateId(), tenant.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Plantilla no encontrada con id: " + dto.templateId()));

        Document doc = documentMapper.toEntity(dto, patient, currentUser(), template);
        doc.setTenant(tenant);
        clinicalHistoryRepository.findByPatientId(patient.getId()).ifPresent(doc::setClinicalHistory);
        return documentMapper.toResponseDto(documentRepository.save(doc), authorities);
    }

    // ── Documento externo (archivo subido) ───────────────────────────────────

    @Transactional
    @Auditable(resourceType = "DOCUMENT", actionType = ActionType.CREATE)    
    public DocumentResponseDto createExternal(ExternalDocumentRequestDto dto) {
        Set<String> authorities = currentAuthorities();
        validateCreateExternalAttributePermissions(dto, authorities);

        Tenant tenant = tenantResolverService.resolve();
        planLimitValidator.checkDocumentsLimit(tenant.getId());

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Paciente no encontrado con id: " + dto.patientId()));

        Document doc = new Document();
        doc.setTenant(tenant);
        doc.setPatient(patient);
        clinicalHistoryRepository.findByPatientId(patient.getId()).ifPresent(doc::setClinicalHistory);
        doc.setUploader(currentUser());
        doc.setTemplate(null);
        doc.setFileUrl(dto.fileUrl());
        doc.setFileSizeBytes(fileStorageService.getFileSize(
                dto.fileUrl() != null ? dto.fileUrl().replaceFirst("^/uploads/", "") : ""));
        doc.setIssueDate(dto.issueDate());
        doc.setIsExternalSource(true);
        doc.setStatus(DocumentStatus.DRAFT);

        // Guardamos las notas como contenido clínico simple si las hay
        if (dto.notes() != null && !dto.notes().isBlank()) {
            doc.setClinicalContent(Map.of("notas", dto.notes()));
        }

        return documentMapper.toResponseDto(documentRepository.save(doc), authorities);
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Auditable(resourceType = "DOCUMENT", actionType = ActionType.READ)
    public List<DocumentResponseDto> getAll() {
        Set<String> authorities = currentAuthorities();
        Tenant tenant = tenantResolverService.resolve();
        return documentRepository.findAll().stream()
                .filter(d -> d.getTenant().getId().equals(tenant.getId()))
                .map(doc -> documentMapper.toResponseDto(doc, authorities))
                .toList();
    }

    @Transactional(readOnly = true)
    @Auditable(resourceType = "DOCUMENT", actionType = ActionType.READ)
    public List<DocumentResponseDto> getByPatient(UUID patientId) {
        Set<String> authorities = currentAuthorities();
        Tenant tenant = tenantResolverService.resolve();
        return documentRepository
                .findByPatientIdAndTenantId(patientId, tenant.getId())
                .stream()
                .map(doc -> documentMapper.toResponseDto(doc, authorities))
                .toList();
    }

    @Transactional(readOnly = true)
    @Auditable(resourceType = "DOCUMENT", actionType = ActionType.READ, idParamName = "id")
    public DocumentResponseDto getById(UUID id) {
        return documentMapper.toResponseDto(findOrThrow(id), currentAuthorities());
    }

    @Transactional(readOnly = true)
    @Auditable(resourceType = "DOCUMENT", actionType = ActionType.READ)
    public List<DocumentResponseDto> searchByClinicalField(String key, String value) {
        Set<String> authorities = currentAuthorities();
        Tenant tenant = tenantResolverService.resolve();
        return documentRepository
                .findByTenantIdAndClinicalContentField(tenant.getId(), key, value)
                .stream()
                .map(doc -> documentMapper.toResponseDto(doc, authorities))
                .toList();
    }

    @Transactional
    @Auditable(resourceType = "DOCUMENT", actionType = ActionType.UPDATE, idParamName = "id")
    public DocumentResponseDto changeStatus(UUID id, DocumentStatus newStatus) {
        Set<String> authorities = currentAuthorities();
        requireAuthority(authorities, "document:update:status");

        Document doc = findOrThrow(id);
        DocumentStatus previousStatus = doc.getStatus();
        validateTransition(previousStatus, newStatus);
        doc.setStatus(newStatus);
        Document saved = documentRepository.save(doc);

        UUID tenantId = saved.getTenant().getId();

        // DRAFT → PENDING_REVIEW: cancelar tareas previas y registrar evento
        if (previousStatus == DocumentStatus.DRAFT && newStatus == DocumentStatus.PENDING_REVIEW) {
            reviewTaskService.cancelTasksForDocument(id, tenantId);
            documentVersioningService.recordVersion(doc, currentUser().getId(), "Enviado a revisión");
            workflowEventService.recordEvent(saved, tenantId,
                    WorkflowEventType.SENT_TO_REVIEW, currentUser(), null, null);
        }
        // PENDING_REVIEW → FINALIZED: cancelar tareas y registrar eventos
        else if (previousStatus == DocumentStatus.PENDING_REVIEW && newStatus == DocumentStatus.FINALIZED) {
            reviewTaskService.cancelTasksForDocument(id, tenantId);
            documentVersioningService.recordVersion(doc, currentUser().getId(), "Aprobado y finalizado");
            workflowEventService.recordEvent(saved, tenantId,
                    WorkflowEventType.TASK_APPROVED, currentUser(), null, null);
            workflowEventService.recordEvent(saved, tenantId,
                    WorkflowEventType.DOCUMENT_FINALIZED, currentUser(), null, null);
        }
        // PENDING_REVIEW → REJECTED: cancelar tareas y registrar eventos
        else if (previousStatus == DocumentStatus.PENDING_REVIEW && newStatus == DocumentStatus.REJECTED) {
            reviewTaskService.cancelTasksForDocument(id, tenantId);
            documentVersioningService.recordVersion(doc, currentUser().getId(), "Rechazado");
            workflowEventService.recordEvent(saved, tenantId,
                    WorkflowEventType.TASK_REJECTED, currentUser(), null, null);
            workflowEventService.recordEvent(saved, tenantId,
                    WorkflowEventType.DOCUMENT_REJECTED, currentUser(), null, null);
        }
        // REJECTED → DRAFT: cancelar tareas activas y registrar evento
        else if (previousStatus == DocumentStatus.REJECTED && newStatus == DocumentStatus.DRAFT) {
            reviewTaskService.cancelTasksForDocument(id, tenantId);
            documentVersioningService.recordVersion(doc, currentUser().getId(), "Corregido - vuelta a borrador");
            workflowEventService.recordEvent(saved, tenantId,
                    WorkflowEventType.DOCUMENT_CORRECTED, currentUser(), null, null);
        }
        // REJECTED → PENDING_REVIEW: cancelar tareas viejas y reenviar
        else if (previousStatus == DocumentStatus.REJECTED && newStatus == DocumentStatus.PENDING_REVIEW) {
            reviewTaskService.cancelTasksForDocument(id, tenantId);
            documentVersioningService.recordVersion(doc, currentUser().getId(), "Reenviado a revisión");
            workflowEventService.recordEvent(saved, tenantId,
                    WorkflowEventType.SENT_TO_REVIEW, currentUser(), null, null);
        }

        return documentMapper.toResponseDto(saved, authorities);
    }

    @Transactional
    @Auditable(resourceType = "DOCUMENT", actionType = ActionType.UPDATE, idParamName = "id")
    public DocumentResponseDto update(UUID id, DocumentUpdateDto dto) {
        Set<String> authorities = currentAuthorities();
        validateUpdateAttributePermissions(dto, authorities);

        Document doc = findOrThrow(id);
        doc.setIssueDate(dto.issueDate());
        if (dto.expiryDate() != null)
            doc.setExpiryDate(dto.expiryDate());
        if (dto.clinicalContent() != null)
            doc.setClinicalContent(dto.clinicalContent());
        if (dto.status() != null && dto.status() != doc.getStatus()) {
            validateTransition(doc.getStatus(), dto.status());
            doc.setStatus(dto.status());
            documentVersioningService.recordVersion(doc, currentUser().getId(), "Cambio de estado");
        }
        return documentMapper.toResponseDto(documentRepository.save(doc), authorities);
    }

    @Transactional
    @Auditable(resourceType = "DOCUMENT", actionType = ActionType.DELETE, idParamName = "id")
    public void delete(UUID id) {
        Document doc = findOrThrow(id);
        documentRepository.delete(doc);
    }

    private void validateCreateAttributePermissions(DocumentRequestDto dto, Set<String> authorities) {
        if (dto.templateId() != null) requireAuthority(authorities, "document:create:template_id");
        if (dto.clinicalContent() != null) requireAuthority(authorities, "document:create:clinical_content");
        if (dto.fileUrl() != null) requireAuthority(authorities, "document:create:file_url");
        if (dto.expiryDate() != null) requireAuthority(authorities, "document:create:expiry_date");
        if (dto.isExternalSource() != null) requireAuthority(authorities, "document:create:is_external_source");
    }

    private void validateCreateExternalAttributePermissions(ExternalDocumentRequestDto dto, Set<String> authorities) {
        if (dto.fileUrl() != null) requireAuthority(authorities, "document:create:file_url");
        if (dto.notes() != null) requireAuthority(authorities, "document:create:clinical_content");
    }

    private void validateUpdateAttributePermissions(DocumentUpdateDto dto, Set<String> authorities) {
        if (dto.issueDate() != null) requireAuthority(authorities, "document:update:issue_date");
        if (dto.expiryDate() != null) requireAuthority(authorities, "document:update:expiry_date");
        if (dto.clinicalContent() != null) requireAuthority(authorities, "document:update:clinical_content");
        if (dto.status() != null) requireAuthority(authorities, "document:update:status");
    }


    // ── Helpers ──────────────────────────────────────────────────────────────

    private Document findOrThrow(UUID id) {
        Tenant tenant = tenantResolverService.resolve();
        return documentRepository.findByIdAndTenantId(id, tenant.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Documento no encontrado con id: " + id));
    }

    private User currentUser() {
        Object principal = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        if (principal instanceof SecurityUser su)
            return su.getUser();
        throw new IllegalStateException("No se pudo determinar el usuario autenticado");
    }

    private void validateTransition(DocumentStatus current, DocumentStatus next) {
        boolean valid = switch (current) {
            case DRAFT          -> next == DocumentStatus.PENDING_REVIEW;
            case PENDING_REVIEW -> next == DocumentStatus.REJECTED || next == DocumentStatus.FINALIZED;
            case REJECTED       -> next == DocumentStatus.DRAFT     || next == DocumentStatus.PENDING_REVIEW;
            case FINALIZED      -> false;
        };
        if (!valid)
            throw new IllegalStateException(
                    "Transición inválida: " + current + " → " + next);
    }

    // ── OCR ──────────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(resourceType = "DOCUMENT", actionType = ActionType.CREATE, idParamName = "documentId")
    public OcrResultDto processOcr(UUID documentId) {
        Document doc = findOrThrow(documentId);

        planFeatureValidator.checkOcrScanning(doc.getTenant().getId());
        planLimitValidator.checkOcrPagesLimit(doc.getTenant().getId());

        if (doc.getFileUrl() == null || doc.getFileUrl().isBlank())
            throw new IllegalStateException("El documento no tiene archivo físico para procesar");

        // Leer el archivo utilizando FileStorageService
        try {
            Resource resource = fileStorageService.loadAsResource(doc.getFileUrl());
            byte[] bytes;
            try (InputStream inputStream = resource.getInputStream()) {
                bytes = inputStream.readAllBytes();
            }

            String contentType = MediaTypeFactory.getMediaType(doc.getFileUrl())
                    .map(MimeType::toString)
                    .orElse("application/octet-stream");

            OcrResultDto result = ocrClientService.extract(bytes, contentType);

            // Guardar o actualizar en document_ocr_metadata
            DocumentOcrMetadata meta = ocrMetadataRepository
                    .findByDocumentId(documentId)
                    .orElse(DocumentOcrMetadata.builder().document(doc).build());

            meta.setRawText(result.rawText());
            meta.setConfidenceScore(result.confidenceScore());
            meta.setPagesProcessed(result.pagesProcessed());
            meta.setFileType(result.fileType());
            meta.setCreatedAt(LocalDateTime.now());
            ocrMetadataRepository.save(meta);

            return result;
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    @Auditable(resourceType = "DOCUMENT_OCR", actionType = ActionType.READ, idParamName = "documentId")
    public OcrResultDto getOcrResult(UUID documentId) {
        findOrThrow(documentId); // verifica que el documento exista y sea del tenant
        DocumentOcrMetadata meta = ocrMetadataRepository
                .findByDocumentId(documentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No hay OCR procesado para el documento: " + documentId));
        return new OcrResultDto(
                meta.getRawText(), meta.getDatosEstructurados(),
                meta.getConfidenceScore(),
                meta.getPagesProcessed(), meta.getFileType());
    }
    /**
     * Ejecuta una búsqueda paginada de historiales clínicos según los criterios
     * proporcionados.
     * <p>
     * Este método actúa como puente entre el controlador y el repositorio,
     * convirtiendo las entidades {@code Document} en {@code DocumentResponseDto}
     * para su exposición en la API.
     * </p>
     *
     * @param nombre     Nombre del paciente (parcial, opcional).
     * @param nroDoc     Número de documento del paciente (opcional).
     * @param estado     Estado del documento (opcional).
     * @param fechaDesde Fecha de emisión mínima (opcional).
     * @param fechaHasta Fecha de emisión máxima (opcional).
     * @param pageable   Parámetros de paginación y orden.
     * @return Página de DTOs de documentos.
     */
    
    @Auditable(resourceType = "DOCUMENT", actionType = ActionType.READ)
    public Page<DocumentResponseDto> searchHistoriales(
        String nombre,
        String nroDoc,
        DocumentStatus estado,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        Pageable pageable) {

        String estadoStr = estado != null ? estado.name() : null;
        String fechaDesdeStr = fechaDesde != null ? fechaDesde.toString() : null;
        String fechaHastaStr = fechaHasta != null ? fechaHasta.toString() : null;

        Page<Document> documentsPage = documentRepository.searchHistoriales(
                nombre, nroDoc, estadoStr, fechaDesdeStr, fechaHastaStr, pageable);

        return documentsPage.map(documentMapper::toResponseDto);
    }

    @Override
    public Document getEntity(UUID id) {
        return findOrThrow(id);
    }

    @Override
    public Map<String, Object> toAuditMap(Document entity) {
        return documentMapper.toAuditMap(entity);
    }

    @Override
    public Map<String, Object> toAuditMapFromResult(Object result) {
        if (result instanceof OcrResultDto ocr) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rawText", ocr.rawText() != null ? "[TEXTO_EXTRAIDO]" : null);
            map.put("datosEstructurados", ocr.structuredData());
            map.put("confidenceScore", ocr.confidenceScore());
            map.put("pagesProcessed", ocr.pagesProcessed());
            map.put("fileType", ocr.fileType());
            return map;
        }
        // Return empty map for DocumentResponseDto to force full entity reload
        return Map.of();
    }

}
