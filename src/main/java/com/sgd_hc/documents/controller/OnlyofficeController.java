package com.sgd_hc.documents.controller;

import com.sgd_hc.documents.dto.OnlyofficeCallbackDto;
import com.sgd_hc.documents.dto.OnlyofficeSessionRequestDto;
import com.sgd_hc.documents.dto.OnlyofficeSessionResponseDto;
import com.sgd_hc.documents.dto.OoDocType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import com.sgd_hc.documents.service.OnlyofficeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/documents/onlyoffice")
@RequiredArgsConstructor
public class OnlyofficeController {

    private final OnlyofficeService onlyofficeService;

    /**
     * Crea una sesión de edición: Document DRAFT + config para el editor Angular.
     * Requiere autenticación.
     */
    @PostMapping("/session")
    @PreAuthorize("hasAuthority('DOCUMENT_CREATE')")
    public ResponseEntity<OnlyofficeSessionResponseDto> createSession(
            @Valid @RequestBody OnlyofficeSessionRequestDto req) {
        return ResponseEntity.ok(onlyofficeService.createSession(req));
    }

    /**
     * Abre una sesión de edición para un documento existente.
     * OO DS descarga el archivo guardado previamente (si existe).
     */
    @PostMapping("/{docId}/session")
    @PreAuthorize("hasAuthority('DOCUMENT_UPDATE')")
    public ResponseEntity<OnlyofficeSessionResponseDto> openSession(@PathVariable UUID docId) {
        return ResponseEntity.ok(onlyofficeService.openSession(docId));
    }

    /**
     * Abre una sesión de solo lectura para un documento existente.
     */
    @PostMapping("/{docId}/view-session")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public ResponseEntity<OnlyofficeSessionResponseDto> openViewSession(@PathVariable UUID docId) {
        return ResponseEntity.ok(onlyofficeService.openViewSession(docId));
    }

    /**
     * Sirve un documento vacío del tipo solicitado.
     * Público (OO DS lo llama sin autenticación).
     * ?type=docx|xlsx|pptx  (por defecto docx)
     */
    @GetMapping("/empty-doc")
    public ResponseEntity<byte[]> emptyDoc(
            @RequestParam(defaultValue = "docx") String type) throws IOException {

        OoDocType ooType = OoDocType.fromExtension("file." + type);
        byte[] bytes;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            switch (ooType) {
                case CELL -> { try (XSSFWorkbook wb = new XSSFWorkbook()) { wb.createSheet(); wb.write(baos); } }
                case SLIDE -> { try (XMLSlideShow ppt = new XMLSlideShow()) { ppt.createSlide(); ppt.write(baos); } }
                default    -> { try (XWPFDocument doc = new XWPFDocument()) { doc.createParagraph(); doc.write(baos); } }
            }
            bytes = baos.toByteArray();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ooType.mimeType()))
                .header("Content-Disposition", "inline; filename=\"documento." + ooType.fileExt + "\"")
                .body(bytes);
    }

    /**
     * Callback invocado por OnlyOffice Document Server cuando el usuario guarda.
     * DEBE devolver {"error": 0} siempre, o OO DS reintenta indefinidamente.
     * Público (OO DS no envía JWT).
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Integer>> callback(
            @RequestBody OnlyofficeCallbackDto body,
            @RequestParam UUID docId) {
        try {
            onlyofficeService.processCallback(docId, body);
        } catch (Exception e) {
            log.error("Error procesando callback OO: docId={}", docId, e);
            // Devolver error=1 para que OO reintente
            return ResponseEntity.ok(Map.of("error", 1));
        }
        return ResponseEntity.ok(Map.of("error", 0));
    }
}
