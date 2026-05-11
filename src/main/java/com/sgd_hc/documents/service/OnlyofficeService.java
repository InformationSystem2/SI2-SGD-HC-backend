package com.sgd_hc.documents.service;

import com.sgd_hc.documents.dto.OnlyofficeCallbackDto;
import com.sgd_hc.documents.dto.OnlyofficeSessionRequestDto;
import com.sgd_hc.documents.dto.OnlyofficeSessionResponseDto;
import com.sgd_hc.documents.dto.OoDocType;
import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.entity.DocumentStatus;
import com.sgd_hc.documents.mapper.DocumentMapper;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.patients.repository.PatientRepository;
import com.sgd_hc.security.config.tenant.TenantContext;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlyofficeService {

    private final DocumentRepository    documentRepository;
    private final PatientRepository     patientRepository;
    private final TenantResolverService tenantResolverService;
    private final DocumentMapper        documentMapper;

    @Value("${onlyoffice.document-server-url:http://localhost:8088}")
    private String documentServerUrl;

    @Value("${app.public-url:http://localhost:3000}")
    private String publicUrl;

    @Value("${storage.upload-dir:uploads}")
    private String uploadDir;

    @Value("${onlyoffice.jwt-secret:}")
    private String ooJwtSecret;

    // ── Sesión nueva ─────────────────────────────────────────────────────────

    @Transactional
    public OnlyofficeSessionResponseDto createSession(OnlyofficeSessionRequestDto req) {
        Tenant  tenant   = tenantResolverService.resolve();
        Patient patient  = patientRepository.findById(req.patientId())
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado: " + req.patientId()));

        OoDocType docType = req.docType() != null ? req.docType() : OoDocType.WORD;
        boolean   hasFile = req.fileUrl() != null && !req.fileUrl().isBlank();

        // Si se sube un archivo, inferir tipo del documento por su extensión
        if (hasFile) docType = OoDocType.fromExtension(req.fileUrl());

        String title  = (req.title() != null && !req.title().isBlank()) ? req.title() : "Documento clínico";
        String docKey = UUID.randomUUID().toString().replace("-", "");

        Document doc = new Document();
        doc.setTenant(tenant);
        doc.setPatient(patient);
        doc.setUploader(currentUser());
        doc.setStatus(DocumentStatus.DRAFT);
        doc.setIsExternalSource(true);
        doc.setIssueDate(req.issueDate());
        doc.setClinicalContent(Map.of("titulo", title, "origen", "OnlyOffice", "file_ext", docType.fileExt));
        if (hasFile) doc.setFileUrl(req.fileUrl());
        Document saved = documentRepository.save(doc);

        String docUrl = hasFile
                ? publicUrl + req.fileUrl()
                : publicUrl + "/api/documents/onlyoffice/empty-doc?type=" + docType.fileExt;

        log.info("Sesión OO creada: docId={}, tipo={}", saved.getId(), docType);
        return buildSessionResponse(saved.getId().toString(), docKey, docUrl,
                publicUrl + "/api/documents/onlyoffice/callback?docId=" + saved.getId(),
                docType, false);
    }

    // ── Sesión edición para documento existente ───────────────────────────────

    @Transactional(readOnly = true)
    public OnlyofficeSessionResponseDto openSession(UUID docId) {
        Document  doc     = findTenantDoc(docId);
        OoDocType docType = resolveDocType(doc);
        String    fileUrl = resolveFileUrl(doc, docType);
        String    docKey  = UUID.randomUUID().toString().replace("-", "");

        log.info("Sesión OO (edición) abierta: docId={}, tipo={}", docId, docType);
        return buildSessionResponse(docId.toString(), docKey, fileUrl,
                publicUrl + "/api/documents/onlyoffice/callback?docId=" + docId,
                docType, false);
    }

    // ── Sesión solo lectura para documento existente ──────────────────────────

    @Transactional(readOnly = true)
    public OnlyofficeSessionResponseDto openViewSession(UUID docId) {
        Document  doc     = findTenantDoc(docId);
        OoDocType docType = resolveDocType(doc);
        String    fileUrl = resolveFileUrl(doc, docType);
        String    docKey  = UUID.randomUUID().toString().replace("-", "");

        log.info("Sesión OO (vista) abierta: docId={}, tipo={}", docId, docType);
        return buildSessionResponse(docId.toString(), docKey, fileUrl, "", docType, true);
    }

    // ── Callback ─────────────────────────────────────────────────────────────

    @Transactional
    public void processCallback(UUID docId, OnlyofficeCallbackDto body) {
        log.info("OO callback: docId={}, status={}", docId, body.status());
        if (body.status() != 2 && body.status() != 6) return;
        if (body.url() == null || body.url().isBlank()) {
            log.warn("OO callback sin URL de archivo. docId={}", docId);
            return;
        }

        try {
            byte[] fileBytes = RestClient.create().get().uri(body.url()).retrieve().body(byte[].class);
            if (fileBytes == null || fileBytes.length == 0) {
                log.error("OO devolvió archivo vacío. docId={}", docId);
                return;
            }

            TenantContext.setBypassFilter(true);
            try {
                Document doc = documentRepository.findById(docId)
                        .orElseThrow(() -> new EntityNotFoundException("Documento OO no encontrado: " + docId));

                OoDocType docType = resolveDocType(doc);
                String filename = UUID.randomUUID() + "." + docType.fileExt;
                Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
                Files.createDirectories(dir);
                Files.write(dir.resolve(filename), fileBytes);

                doc.setFileUrl("/uploads/" + filename);
                doc.setStatus(DocumentStatus.COMPLETED);
                documentRepository.save(doc);
                log.info("Documento OO guardado: docId={}, url={}", docId, "/uploads/" + filename);
            } finally {
                TenantContext.clear();
            }
        } catch (IOException e) {
            log.error("Error guardando archivo OO: docId={}", docId, e);
            throw new RuntimeException("Error al guardar documento OnlyOffice", e);
        }
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    private OnlyofficeSessionResponseDto buildSessionResponse(
            String docId, String docKey, String fileUrl,
            String callbackUrl, OoDocType docType, boolean viewOnly) {

        String dsUrl = documentServerUrl.endsWith("/") ? documentServerUrl : documentServerUrl + "/";

        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("edit",     !viewOnly);
        permissions.put("download", true);
        permissions.put("print",    true);

        Map<String, Object> documentMap = new LinkedHashMap<>();
        documentMap.put("fileType",    docType.fileExt);
        documentMap.put("key",         docKey);
        documentMap.put("title",       "Documento");
        documentMap.put("url",         fileUrl);
        documentMap.put("permissions", permissions);

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        if (!callbackUrl.isBlank()) editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("lang", "es");
        editorConfig.put("mode", viewOnly ? "view" : "edit");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("document",     documentMap);
        payload.put("documentType", docType.ooType);
        payload.put("editorConfig", editorConfig);

        return new OnlyofficeSessionResponseDto(
                docId, docKey, dsUrl, fileUrl, callbackUrl,
                signOOConfig(payload), docType.fileExt, docType.ooType);
    }

    private Document findTenantDoc(UUID docId) {
        Tenant tenant = tenantResolverService.resolve();
        return documentRepository.findByIdAndTenantId(docId, tenant.getId())
                .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado: " + docId));
    }

    private OoDocType resolveDocType(Document doc) {
        // 1. Intentar desde clinicalContent["file_ext"]
        if (doc.getClinicalContent() != null) {
            Object ext = doc.getClinicalContent().get("file_ext");
            if (ext instanceof String s) return OoDocType.fromExtension("file." + s);
        }
        // 2. Inferir desde fileUrl
        return OoDocType.fromExtension(doc.getFileUrl());
    }

    private String resolveFileUrl(Document doc, OoDocType docType) {
        return (doc.getFileUrl() != null && !doc.getFileUrl().isBlank())
                ? publicUrl + doc.getFileUrl()
                : publicUrl + "/api/documents/onlyoffice/empty-doc?type=" + docType.fileExt;
    }

    private String extractTitle(Document doc) {
        Object t = doc.getClinicalContent() != null ? doc.getClinicalContent().get("titulo") : null;
        if (t instanceof String s && !s.isBlank()) return s;
        return doc.getTemplate() != null ? doc.getTemplate().getName() : "Documento clínico";
    }

    private String signOOConfig(Map<String, Object> payload) {
        if (ooJwtSecret == null || ooJwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) return "";
        SecretKey key = Keys.hmacShaKeyFor(ooJwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().claims(payload).signWith(key).compact();
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof SecurityUser su) return su.getUser();
        throw new IllegalStateException("No se pudo determinar el usuario autenticado");
    }
}
