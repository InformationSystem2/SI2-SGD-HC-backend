//src/main/java/com/sgd_hc/documents/mapper/DocumentMapper.java

package com.sgd_hc.documents.mapper;

import com.sgd_hc.documents.dto.DocumentRequestDto;
import com.sgd_hc.documents.dto.DocumentResponseDto;
import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.entity.DocumentTemplate;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.users.entity.User;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DocumentMapper {

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
        // template es nullable para documentos externos
        UUID templateId = doc.getTemplate() != null ? doc.getTemplate().getId() : null;
        String templateName = doc.getTemplate() != null ? doc.getTemplate().getName() : "Documento Externo";

        String patientName = doc.getPatient().getFirstName() + " " + doc.getPatient().getLastName();
        String patientDocNumber = doc.getPatient().getDocumentNumber(); // ← OBTENER NÚMERO
        String uploaderName = doc.getUploader().getFirstName() + " " + doc.getUploader().getLastName();

        return new DocumentResponseDto(
                doc.getId(),
                doc.getPatient().getId(),
                patientName,
                patientDocNumber, // ← AGREGAR
                doc.getUploader().getId(),
                uploaderName,
                templateId,
                templateName,
                doc.getStatus(),
                doc.getClinicalContent(),
                doc.getIssueDate(),
                doc.getExpiryDate(),
                doc.getFileUrl(),
                doc.getIsExternalSource());
    }
}
