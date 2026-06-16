package com.sgd_hc.documents.controller;

import com.sgd_hc.documents.dto.OnlyofficeSessionResponseDto;
import com.sgd_hc.documents.dto.VersionHistoryResponseDto;
import com.sgd_hc.documents.service.DocumentVersioningService;
import com.sgd_hc.documents.service.OnlyofficeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de consulta del historial inmutable de versiones de un documento.
 */
@RestController
@RequestMapping("/api/documents/{documentId}/versions")
@RequiredArgsConstructor
public class DocumentVersionController {

    private final DocumentVersioningService versioningService;
    private final OnlyofficeService         onlyofficeService;

    /**
     * Devuelve el historial completo del documento, ordenado de la versión más
     * reciente a la más antigua.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<List<VersionHistoryResponseDto>> getHistory(@PathVariable UUID documentId) {
        return ResponseEntity.ok(versioningService.getHistory(documentId));
    }

    /**
     * Abre una sesión OnlyOffice en modo solo lectura sobre el archivo
     * snapshot de una versión específica.
     */
    @PostMapping("/{versionId}/onlyoffice-view-session")
    @PreAuthorize("hasAuthority('document:read')")
    public ResponseEntity<OnlyofficeSessionResponseDto> openVersionViewSession(
            @PathVariable UUID documentId,
            @PathVariable UUID versionId) {
        return ResponseEntity.ok(onlyofficeService.openVersionViewSession(documentId, versionId));
    }
}
