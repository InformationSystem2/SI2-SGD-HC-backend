//src/main/java/com/sgd_hc/documents/controller/HistorialController.java

package com.sgd_hc.documents.controller;

import com.sgd_hc.documents.dto.DocumentResponseDto;
import com.sgd_hc.documents.entity.DocumentStatus;
import com.sgd_hc.documents.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/historiales")
@RequiredArgsConstructor
public class HistorialController {

    private final DocumentService documentService;

    /**
     * Endpoint público para búsqueda de historiales clínicos.
     * <p>
     * Permite filtrar por nombre del paciente, número de documento,
     * estado del documento y rango de fechas de emisión.
     * Los resultados se devuelven paginados (20 por defecto) y ordenados
     * por fecha de emisión descendente (más reciente primero).
     * </p>
     * <p>
     * Ejemplo de llamada:
     * {@code GET /api/historiales/search?nombre=Mart&estado=COMPLETED&page=0&size=10}
     * </p>
     *
     * @param nombre     (opcional) Texto parcial del nombre del paciente.
     * @param nroDoc     (opcional) Número de documento exacto.
     * @param estado     (opcional) Estado del documento (DRAFT, PENDING_SIGNATURE,
     *                   COMPLETED).
     * @param fechaDesde (opcional) Fecha mínima en formato ISO (yyyy-MM-dd).
     * @param fechaHasta (opcional) Fecha máxima en formato ISO (yyyy-MM-dd).
     * @param pageable   Control de paginación y orden (se puede omitir, usa valores
     *                   por defecto).
     * @return Página con los documentos encontrados y metadatos de paginación.
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public ResponseEntity<Page<DocumentResponseDto>> searchHistoriales(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String nroDoc,
            @RequestParam(required = false) DocumentStatus estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @PageableDefault(size = 20, sort = "issue_date", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DocumentResponseDto> result = documentService.searchHistoriales(
                nombre, nroDoc, estado, fechaDesde, fechaHasta, pageable);
        return ResponseEntity.ok(result);
    }
}