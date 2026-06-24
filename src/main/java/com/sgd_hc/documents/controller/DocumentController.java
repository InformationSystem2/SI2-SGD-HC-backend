package com.sgd_hc.documents.controller;

import com.sgd_hc.documents.dto.DocumentRequestDto;
import com.sgd_hc.documents.dto.DocumentResponseDto;
import com.sgd_hc.documents.dto.DocumentUpdateDto;
import com.sgd_hc.documents.dto.ExternalDocumentRequestDto;
import com.sgd_hc.documents.entity.DocumentStatus;
import com.sgd_hc.documents.service.DocumentService;
import com.sgd_hc.documents.service.FileStorageService;
import com.sgd_hc.documents.dto.OcrResultDto;

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

    private final DocumentService documentService;
    private final FileStorageService fileStorageService;

    // ── Documento basado en plantilla ────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('document:create')")
    public ResponseEntity<DocumentResponseDto> create(@Valid @RequestBody DocumentRequestDto dto) {
        return new ResponseEntity<>(documentService.create(dto), HttpStatus.CREATED);
    }

    // ── Lista general ────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<DocumentResponseDto>> getAll() {
        return ResponseEntity.ok(documentService.getAll());
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<DocumentResponseDto>> getByPatient(@PathVariable UUID patientId) {
        return ResponseEntity.ok(documentService.getByPatient(patientId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<DocumentResponseDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getById(id));
    }

    /** Busca por clave/valor dentro del JSONB clinical_content. */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<DocumentResponseDto>> searchByClinicalField(
            @RequestParam String key,
            @RequestParam String value) {
        return ResponseEntity.ok(documentService.searchByClinicalField(key, value));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('document:update')")
    public ResponseEntity<DocumentResponseDto> changeStatus(
            @PathVariable UUID id,
            @RequestParam DocumentStatus status) {
        return ResponseEntity.ok(documentService.changeStatus(id, status));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('document:update')")
    public ResponseEntity<DocumentResponseDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentUpdateDto dto) {
        return ResponseEntity.ok(documentService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('document:delete')")
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
    @PreAuthorize("hasAuthority('document:create')")
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
    @PreAuthorize("hasAuthority('document:create')")
    public ResponseEntity<DocumentResponseDto> createExternal(
            @Valid @RequestBody ExternalDocumentRequestDto dto) {
        return new ResponseEntity<>(documentService.createExternal(dto), HttpStatus.CREATED);
    }

    // ── OCR ──────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/ocr")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<OcrResultDto> triggerOcr(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.processOcr(id));
    }

    @GetMapping("/{id}/ocr")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<OcrResultDto> getOcr(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getOcrResult(id));
    }

    @PostMapping("/{id}/ocr/result")
    @PreAuthorize("hasAuthority('document_ocr:create')")
    public ResponseEntity<OcrResultDto> saveOcrResult(
            @PathVariable UUID id,
            @RequestBody OcrResultDto result) {
        return ResponseEntity.ok(documentService.saveOcrResult(id, result));
    }
    /*
    @GetMapping("/api/records/search")
    public ResponseEntity<Page<DocumentResponseDto>> searchHistory(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String nroDoc,
            @RequestParam(required = false) DocumentStatus estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @PageableDefault(size = 20, sort = "issueDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DocumentResponseDto> result = documentService.searchHistoriales(
                nombre, nroDoc, estado, fechaDesde, fechaHasta, pageable);
        return ResponseEntity.ok(result);
    }
    */
}
