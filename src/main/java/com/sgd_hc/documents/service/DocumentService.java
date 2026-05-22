//src/main/java/com/sgd_hc/documents/service/DocumentService.java

package com.sgd_hc.documents.service;

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
import com.sgd_hc.patients.repository.PatientRepository;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sgd_hc.documents.dto.OcrResultDto;
import com.sgd_hc.documents.entity.DocumentOcrMetadata;
import com.sgd_hc.documents.repository.DocumentOcrMetadataRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentTemplateRepository documentTemplateRepository;

    private final PatientRepository          patientRepository;
    private final DocumentMapper             documentMapper;
    private final TenantResolverService      tenantResolverService;
    private final OcrClientService          ocrClientService;
    private final DocumentOcrMetadataRepository ocrMetadataRepository;

    @Value("${storage.upload-dir:uploads}")
    private String uploadDir;
    // ── Documento basado en plantilla ────────────────────────────────────────

    @Transactional
    public DocumentResponseDto create(DocumentRequestDto dto) {
        Tenant tenant = tenantResolverService.resolve();

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Paciente no encontrado con id: " + dto.patientId()));

        DocumentTemplate template = documentTemplateRepository
                .findByIdAndTenantId(dto.templateId(), tenant.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Plantilla no encontrada con id: " + dto.templateId()));

        Document doc = documentMapper.toEntity(dto, patient, currentUser(), template);
        doc.setTenant(tenant);
        return documentMapper.toResponseDto(documentRepository.save(doc));
    }

    // ── Documento externo (archivo subido) ───────────────────────────────────

    @Transactional
    public DocumentResponseDto createExternal(ExternalDocumentRequestDto dto) {
        Tenant tenant = tenantResolverService.resolve();

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Paciente no encontrado con id: " + dto.patientId()));

        Document doc = new Document();
        doc.setTenant(tenant);
        doc.setPatient(patient);
        doc.setUploader(currentUser());
        doc.setTemplate(null);
        doc.setFileUrl(dto.fileUrl());
        doc.setIssueDate(dto.issueDate());
        doc.setIsExternalSource(true);
        doc.setStatus(DocumentStatus.DRAFT);

        // Guardamos las notas como contenido clínico simple si las hay
        if (dto.notes() != null && !dto.notes().isBlank()) {
            doc.setClinicalContent(java.util.Map.of("notas", dto.notes()));
        }

        return documentMapper.toResponseDto(documentRepository.save(doc));
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DocumentResponseDto> getAll() {
        Tenant tenant = tenantResolverService.resolve();
        return documentRepository.findAll().stream()
                .filter(d -> d.getTenant().getId().equals(tenant.getId()))
                .map(documentMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponseDto> getByPatient(UUID patientId) {
        Tenant tenant = tenantResolverService.resolve();
        return documentRepository
                .findByPatientIdAndTenantId(patientId, tenant.getId())
                .stream()
                .map(documentMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponseDto getById(UUID id) {
        return documentMapper.toResponseDto(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponseDto> searchByClinicalField(String key, String value) {
        Tenant tenant = tenantResolverService.resolve();
        return documentRepository
                .findByTenantIdAndClinicalContentField(tenant.getId(), key, value)
                .stream()
                .map(documentMapper::toResponseDto)
                .toList();
    }

    @Transactional
    public DocumentResponseDto changeStatus(UUID id, DocumentStatus newStatus) {
        Document doc = findOrThrow(id);
        validateTransition(doc.getStatus(), newStatus);
        doc.setStatus(newStatus);
        return documentMapper.toResponseDto(documentRepository.save(doc));
    }

    @Transactional
    public DocumentResponseDto update(UUID id, DocumentUpdateDto dto) {
        Document doc = findOrThrow(id);
        doc.setIssueDate(dto.issueDate());
        if (dto.expiryDate() != null)
            doc.setExpiryDate(dto.expiryDate());
        if (dto.clinicalContent() != null)
            doc.setClinicalContent(dto.clinicalContent());
        if (dto.status() != null && dto.status() != doc.getStatus()) {
            validateTransition(doc.getStatus(), dto.status());
            doc.setStatus(dto.status());
        }
        return documentMapper.toResponseDto(documentRepository.save(doc));
    }

    @Transactional
    public void delete(UUID id) {
        Document doc = findOrThrow(id);
        documentRepository.delete(doc);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Document findOrThrow(UUID id) {
        Tenant tenant = tenantResolverService.resolve();
        return documentRepository.findByIdAndTenantId(id, tenant.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Documento no encontrado con id: " + id));
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
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
    public OcrResultDto processOcr(UUID documentId) {
        Document doc = findOrThrow(documentId);

        if (doc.getFileUrl() == null || doc.getFileUrl().isBlank())
            throw new IllegalStateException("El documento no tiene archivo físico para procesar");

        // Leer el archivo desde disco
        try {
            Path filePath = Paths.get(uploadDir)
                    .resolve(doc.getFileUrl().replace("/uploads/", ""))
                    .toAbsolutePath().normalize();
            byte[] bytes = Files.readAllBytes(filePath);

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";

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
}