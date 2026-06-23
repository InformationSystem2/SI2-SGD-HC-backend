//src/main/java/com/sgd_hc/documents/mapper/DocumentMapper.java

package com.sgd_hc.documents.mapper;

import com.sgd_hc.documents.dto.DocumentRequestDto;
import com.sgd_hc.documents.dto.DocumentResponseDto;
import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.entity.DocumentTemplate;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.patients.entity.ClinicalHistory;
import com.sgd_hc.users.entity.User;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@Component
public class DocumentMapper {

    private static final Set<String> ALL_READ_AUTHORITIES = Set.of(
            "document:read:id",
            "document:read:patient_id",
            "document:read:uploader_id",
            "document:read:template_id",
            "document:read:status",
            "document:read:clinical_content",
            "document:read:issue_date",
            "document:read:expiry_date",
            "document:read:file_url",
            "document:read:is_external_source",
            "document:read:version_number"
    );

    public Document toEntity(DocumentRequestDto dto, Patient patient, User uploader, DocumentTemplate template) {
        Document doc = new Document();
        doc.setPatient(patient);
        doc.setUploader(uploader);
        doc.setTemplate(template);
        doc.setClinicalContent(dto.clinicalContent());
        doc.setIssueDate(dto.issueDate());
        doc.setExpiryDate(dto.expiryDate());
        doc.setFileUrl(dto.fileUrl());
        doc.setIsExternalSource(dto.isExternalSource() != null ? dto.isExternalSource() : false);
        return doc;
    }

    public DocumentResponseDto toResponseDto(Document doc) {
        return toResponseDto(doc, ALL_READ_AUTHORITIES);
    }

    public DocumentResponseDto toResponseDto(Document doc, Set<String> userAuthorities) {
        // template es nullable para documentos externos
        UUID templateId = doc.getTemplate() != null ? doc.getTemplate().getId() : null;
        String templateName;
        if (doc.getTemplate() != null) {
            templateName = doc.getTemplate().getName();
        } else if (doc.getClinicalContent() != null
                && doc.getClinicalContent().get("titulo") instanceof String t
                && !t.isBlank()) {
            templateName = t;
        } else {
            templateName = "Documento Externo";
        }

        String patientName = doc.getPatient().getFirstName() + " " + doc.getPatient().getLastName();
        String patientDocNumber = doc.getPatient().getDocumentNumber();
        String uploaderName = doc.getUploader().getFirstName() + " " + doc.getUploader().getLastName();

        return new DocumentResponseDto(
                userAuthorities.contains("document:read:id") ? doc.getId() : null,
                userAuthorities.contains("document:read:patient_id") && doc.getPatient() != null ? doc.getPatient().getId() : null,
                userAuthorities.contains("document:read:patient_id") ? patientName : null,
                userAuthorities.contains("document:read:patient_id") ? patientDocNumber : null,
                userAuthorities.contains("document:read:uploader_id") && doc.getUploader() != null ? doc.getUploader().getId() : null,
                userAuthorities.contains("document:read:uploader_id") ? uploaderName : null,
                userAuthorities.contains("document:read:template_id") ? templateId : null,
                userAuthorities.contains("document:read:template_id") ? templateName : null,
                userAuthorities.contains("document:read:status") ? doc.getStatus() : null,
                userAuthorities.contains("document:read:clinical_content") ? doc.getClinicalContent() : null,
                userAuthorities.contains("document:read:issue_date") ? doc.getIssueDate() : null,
                userAuthorities.contains("document:read:expiry_date") ? doc.getExpiryDate() : null,
                userAuthorities.contains("document:read:file_url") ? doc.getFileUrl() : null,
                userAuthorities.contains("document:read:is_external_source") ? doc.getIsExternalSource() : null,
                userAuthorities.contains("document:read:version_number") ? doc.getVersionNumber() : null,
                doc.getClinicalHistory() != null ? doc.getClinicalHistory().getId() : null,
                doc.getClinicalHistory() != null ? doc.getClinicalHistory().getCode() : null);
    }

    public Map<String, Object> toAuditMap(Document entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId().toString());
        map.put("status", entity.getStatus() != null ? entity.getStatus().name() : null);
        map.put("isExternalSource", entity.getIsExternalSource());
        map.put("issueDate", entity.getIssueDate() != null ? entity.getIssueDate().toString() : null);
        map.put("expiryDate", entity.getExpiryDate() != null ? entity.getExpiryDate().toString() : null);
        map.put("versionNumber", entity.getVersionNumber());
        map.put("patientId", entity.getPatient() != null ? entity.getPatient().getId().toString() : null);
        map.put("uploaderId", entity.getUploader() != null ? entity.getUploader().getId().toString() : null);
        map.put("templateId", entity.getTemplate() != null ? entity.getTemplate().getId().toString() : null);
        map.put("clinicalContent", entity.getClinicalContent());
        map.put("fileUrl", entity.getFileUrl());
        map.put("tenantId", entity.getTenant() != null ? entity.getTenant().getId().toString() : null);
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }

    public Map<String, Object> toAuditMapFromDto(DocumentResponseDto dto) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", dto.id().toString());
        map.put("status", dto.status() != null ? dto.status().name() : null);
        map.put("issueDate", dto.issueDate() != null ? dto.issueDate().toString() : null);
        map.put("expiryDate", dto.expiryDate() != null ? dto.expiryDate().toString() : null);
        map.put("patientId", dto.patientId() != null ? dto.patientId().toString() : null);
        map.put("uploaderId", dto.uploaderId() != null ? dto.uploaderId().toString() : null);
        map.put("templateId", dto.templateId() != null ? dto.templateId().toString() : null);
        map.put("clinicalContent", dto.clinicalContent());
        map.put("fileUrl", dto.fileUrl());
        map.put("isExternalSource", dto.isExternalSource());
        map.put("versionNumber", dto.versionNumber());
        return map;
    }
}
