package com.sgd_hc.documents.controller;

import com.sgd_hc.documents.dto.DocumentRequestDto;
import com.sgd_hc.documents.dto.DocumentResponseDto;
import com.sgd_hc.documents.dto.DocumentUpdateDto;
import com.sgd_hc.documents.dto.ExternalDocumentRequestDto;
import com.sgd_hc.documents.entity.DocumentStatus;
import com.sgd_hc.documents.service.DocumentService;
import com.sgd_hc.documents.service.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService     documentService;
    private final FileStorageService  fileStorageService;

    // ── Documento basado en plantilla ────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('DOCUMENT_CREATE')")
    public ResponseEntity<DocumentResponseDto> create(@Valid @RequestBody DocumentRequestDto dto) {
        return new ResponseEntity<>(documentService.create(dto), HttpStatus.CREATED);
    }

    // ── Lista general ────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public ResponseEntity<List<DocumentResponseDto>> getAll() {
        return ResponseEntity.ok(documentService.getAll());
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public ResponseEntity<List<DocumentResponseDto>> getByPatient(@PathVariable UUID patientId) {
        return ResponseEntity.ok(documentService.getByPatient(patientId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public ResponseEntity<DocumentResponseDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getById(id));
    }

    /** Busca por clave/valor dentro del JSONB clinical_content. */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public ResponseEntity<List<DocumentResponseDto>> searchByClinicalField(
            @RequestParam String key,
            @RequestParam String value) {
        return ResponseEntity.ok(documentService.searchByClinicalField(key, value));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('DOCUMENT_UPDATE')")
    public ResponseEntity<DocumentResponseDto> changeStatus(
            @PathVariable UUID id,
            @RequestParam DocumentStatus status) {
        return ResponseEntity.ok(documentService.changeStatus(id, status));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_UPDATE')")
    public ResponseEntity<DocumentResponseDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentUpdateDto dto) {
        return ResponseEntity.ok(documentService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_UPDATE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Subida de archivo ────────────────────────────────────────────────────

    /**
     * Sube un archivo al servidor y devuelve su URL relativa.
     * Paso 1 del flujo de documentos externos.
     */
    @PostMapping(value = "/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DOCUMENT_CREATE')")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestPart("file") MultipartFile file) throws IOException {
        String url = fileStorageService.store(file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * Crea un documento externo (no basado en plantilla) enlazándolo a un paciente.
     * Paso 2 del flujo de documentos externos.
     */
    @PostMapping("/external")
    @PreAuthorize("hasAuthority('DOCUMENT_CREATE')")
    public ResponseEntity<DocumentResponseDto> createExternal(
            @Valid @RequestBody ExternalDocumentRequestDto dto) {
        return new ResponseEntity<>(documentService.createExternal(dto), HttpStatus.CREATED);
    }
}
