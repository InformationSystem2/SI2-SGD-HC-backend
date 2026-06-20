package com.sgd_hc.audit.controller;

import com.sgd_hc.audit.dto.AuditFilterDto;
import com.sgd_hc.audit.dto.AuditLogResponseDto;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.service.AuditLogService;
import com.sgd_hc.audit.service.AuditLogService.IntegrityCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('SUPERUSER')")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<Page<AuditLogResponseDto>> list(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String userIdentifier,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuditFilterDto filter = new AuditFilterDto(
                tenantId,
                userIdentifier,
                actionType,
                resourceType,
                dateFrom != null ? dateFrom.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime() : null,
                dateTo != null ? dateTo.atTime(23, 59, 59).atOffset(ZoneOffset.UTC) : null
        );

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(auditLogService.findAll(filter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponseDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(auditLogService.findById(id));
    }

    @GetMapping("/{id}/verify")
    public ResponseEntity<IntegrityCheckResult> verifyIntegrity(@PathVariable UUID id) {
        return ResponseEntity.ok(auditLogService.verifyIntegrity(id));
    }

    @PostMapping("/verify-all")
    public ResponseEntity<List<IntegrityCheckResult>> verifyAll(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(auditLogService.verifyAll(limit));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String userIdentifier,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo) {

        AuditFilterDto filter = new AuditFilterDto(
                tenantId,
                userIdentifier,
                actionType,
                resourceType,
                dateFrom != null ? dateFrom.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime() : null,
                dateTo != null ? dateTo.atTime(23, 59, 59).atOffset(ZoneOffset.UTC) : null
        );

        byte[] csv = auditLogService.exportCsv(filter);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=audit_log_" + LocalDate.now() + ".csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
