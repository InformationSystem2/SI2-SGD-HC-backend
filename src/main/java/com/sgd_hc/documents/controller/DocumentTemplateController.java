package com.sgd_hc.documents.controller;

import com.sgd_hc.documents.dto.DocumentTemplateRequestDto;
import com.sgd_hc.documents.dto.DocumentTemplateResponseDto;
import com.sgd_hc.documents.service.DocumentTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents/templates")
@RequiredArgsConstructor
public class DocumentTemplateController {

    private final DocumentTemplateService documentTemplateService;

    @PostMapping
    @PreAuthorize("hasAuthority('template:create')")
    public ResponseEntity<DocumentTemplateResponseDto> create(@Valid @RequestBody DocumentTemplateRequestDto dto) {
        return new ResponseEntity<>(documentTemplateService.create(dto), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('template:read')")
    public ResponseEntity<List<DocumentTemplateResponseDto>> getAllActive() {
        return ResponseEntity.ok(documentTemplateService.getAllActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('template:read')")
    public ResponseEntity<DocumentTemplateResponseDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentTemplateService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('template:update')")
    public ResponseEntity<DocumentTemplateResponseDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentTemplateRequestDto dto) {
        return ResponseEntity.ok(documentTemplateService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('template:delete')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        documentTemplateService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
