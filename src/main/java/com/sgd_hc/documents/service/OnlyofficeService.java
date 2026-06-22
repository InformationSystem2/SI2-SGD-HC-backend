package com.sgd_hc.documents.service;

import com.sgd_hc.documents.dto.OnlyofficeCallbackDto;
import com.sgd_hc.documents.dto.OnlyofficeSessionRequestDto;
import com.sgd_hc.documents.dto.OnlyofficeSessionResponseDto;
import com.sgd_hc.documents.entity.OoDocType;
import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.entity.DocumentStatus;
import com.sgd_hc.documents.entity.DocumentVersion;
import com.sgd_hc.documents.repository.DocumentRepository;
import com.sgd_hc.documents.repository.DocumentVersionRepository;
import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.patients.repository.PatientRepository;
import com.sgd_hc.security.config.tenant.TenantContext;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.service.PlanLimitValidator;
import com.sgd_hc.tenants.service.TenantResolverService;
import com.sgd_hc.users.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlyofficeService {

    private final DocumentRepository        documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentVersioningService versioningService;
    private final PatientRepository         patientRepository;
    private final TenantResolverService     tenantResolverService;
    private final FileStorageService        fileStorageService;
    private final PlanLimitValidator        planLimitValidator;

    @Value("${onlyoffice.document-server-url:http://localhost:8088}")
    private String documentServerUrl;

    @Value("${app.public-url:http://localhost:3000}")
    private String publicUrl;

    @Value("${onlyoffice.jwt-secret:}")
    private String ooJwtSecret;

    // ── Sesión nueva ─────────────────────────────────────────────────────────

    @Transactional
    public OnlyofficeSessionResponseDto createSession(OnlyofficeSessionRequestDto req) {
        Tenant  tenant   = tenantResolverService.resolve();
        planLimitValidator.checkDocumentsLimit(tenant.getId());

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
                ? (req.fileUrl().startsWith("http://") || req.fileUrl().startsWith("https://") ? req.fileUrl() : publicUrl + req.fileUrl())
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

    // ── Sesión solo lectura para una VERSIÓN histórica ────────—──────────────

    @Transactional(readOnly = true)
    public OnlyofficeSessionResponseDto openVersionViewSession(UUID docId, UUID versionId) {
        Tenant tenant = tenantResolverService.resolve();

        // Verifica que el documento es del tenant.
        findTenantDoc(docId);

        DocumentVersion version = documentVersionRepository.findById(versionId)
                .filter(v -> v.getTenantId().equals(tenant.getId())
                          && v.getDocument().getId().equals(docId))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Versión no encontrada: " + versionId));

        if (version.getFileUrl() == null || version.getFileUrl().isBlank()) {
            throw new EntityNotFoundException(
                    "Esta versión no tiene archivo asociado para abrir en OnlyOffice");
        }

        OoDocType docType = OoDocType.fromExtension(version.getFileUrl());
        String    fileUrl = publicUrl + version.getFileUrl();
        String    docKey  = "v-" + versionId.toString().replace("-", "");

        log.info("Sesión OO (vista versión) abierta: docId={}, versionId={}, v#={}",
                docId, versionId, version.getVersionNumber());

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
            String downloadUrl = getDownloadUrl(body);
            log.info("Descargando archivo desde OnlyOffice URL: {}", downloadUrl);

            byte[] fileBytes = RestClient.create().get().uri(downloadUrl).retrieve().body(byte[].class);
            if (fileBytes == null || fileBytes.length == 0) {
                log.error("OO devolvió archivo vacío. docId={}", docId);
                return;
            }

            TenantContext.setBypassFilter(true);
            try {
                Document doc = documentRepository.findById(docId)
                        .orElseThrow(() -> new EntityNotFoundException("Documento OO no encontrado: " + docId));

                TenantContext.clear();
                TenantContext.setCurrentTenantId(doc.getTenant().getId());
                TenantContext.setBypassFilter(true);

                // 1. Guardar la versión anterior en el historial inmutable antes de cambiar el archivo
                UUID authorId = null;
                if (body.users() != null && !body.users().isEmpty()) {
                    try {
                        authorId = UUID.fromString(body.users().getFirst());
                    } catch (Exception e) {
                        log.warn("No se pudo parsear el user ID del callback de OnlyOffice: {}", body.users().getFirst());
                    }
                }
                versioningService.recordVersion(doc, authorId, "Cambios guardados desde co-edición OnlyOffice");

                OoDocType docType = resolveDocType(doc);
                String newExt = (body.filetype() != null && !body.filetype().isBlank()) 
                                ? body.filetype() 
                                : docType.fileExt;
                String filename = UUID.randomUUID() + "." + newExt;
                String relativeUrl = fileStorageService.store(filename, fileBytes);

                doc.setFileUrl(relativeUrl);
                doc.setStatus(DocumentStatus.DRAFT);
                documentRepository.save(doc);
                log.info("Documento OO guardado: docId={}, url={}", docId, relativeUrl);
            } finally {
                TenantContext.clear();
            }
        } catch (IOException e) {
            log.error("Error guardando archivo OO: docId={}", docId, e);
            throw new RuntimeException("Error al guardar documento OnlyOffice", e);
        }
    }

    private static @NonNull String getDownloadUrl(OnlyofficeCallbackDto body) {
        String downloadUrl = body.url();
        if (downloadUrl.contains("localhost:8088")) {
            downloadUrl = downloadUrl.replace("localhost:8088", "host.docker.internal:8088");
        } else if (downloadUrl.contains("127.0.0.1:8088")) {
            downloadUrl = downloadUrl.replace("127.0.0.1:8088", "host.docker.internal:8088");
        } else if (downloadUrl.contains("localhost")) {
            downloadUrl = downloadUrl.replace("localhost", "host.docker.internal");
        } else if (downloadUrl.contains("127.0.0.1")) {
            downloadUrl = downloadUrl.replace("127.0.0.1", "host.docker.internal");
        }

        // Fix para entornos nativos (Linux) donde host.docker.internal no se resuelve automáticamente
        if (downloadUrl.contains("host.docker.internal")) {
            try {
                java.net.InetAddress.getByName("host.docker.internal");
            } catch (java.net.UnknownHostException e) {
                // Si la máquina no lo reconoce, regresamos a localhost
                downloadUrl = downloadUrl.replace("host.docker.internal", "localhost");
            }
        }

        return downloadUrl;
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    private OnlyofficeSessionResponseDto buildSessionResponse(
            String docId, String docKey, String fileUrl,
            String callbackUrl, OoDocType docType, boolean viewOnly) {

        String dsUrl = documentServerUrl.endsWith("/") ? documentServerUrl : documentServerUrl + "/";

        Map<String, Object> permissions = getPermissions(viewOnly);

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

        // Agregar los detalles del usuario a editorConfig para rastrear quién realiza la edición
        try {
            User user = currentUser();
            Map<String, Object> userMap = new LinkedHashMap<>();
            userMap.put("id", user.getId().toString());
            userMap.put("name", user.getFirstName() + " " + user.getLastName());
            editorConfig.put("user", userMap);
        } catch (Exception e) {
            log.debug("No hay usuario autenticado en el contexto actual de OnlyOffice (modo público/anónimo)");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("document",     documentMap);
        payload.put("documentType", docType.ooType);
        payload.put("editorConfig", editorConfig);

        return new OnlyofficeSessionResponseDto(
                docId, docKey, dsUrl, fileUrl, callbackUrl,
                signOOConfig(payload), docType.fileExt, docType.ooType);
    }

    private static @NonNull Map<String, Object> getPermissions(boolean viewOnly) {
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("edit",                 !viewOnly);
        permissions.put("download",             true);
        permissions.put("print",                true);
        permissions.put("fillForms",            !viewOnly);
        permissions.put("comment",              !viewOnly);
        permissions.put("review",               !viewOnly);
        permissions.put("modifyFilter",         !viewOnly);
        permissions.put("modifyContentControl", !viewOnly);
        return permissions;
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
                ? (doc.getFileUrl().startsWith("http://") || doc.getFileUrl().startsWith("https://") ? doc.getFileUrl() : publicUrl + doc.getFileUrl())
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
        Object principal = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        if (principal instanceof SecurityUser su) return su.getUser();
        throw new IllegalStateException("No se pudo determinar el usuario autenticado");
    }
}
