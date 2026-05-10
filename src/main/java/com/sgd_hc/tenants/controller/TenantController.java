package com.sgd_hc.tenants.controller;

import com.sgd_hc.tenants.dto.TenantPaymentRequestDto;
import com.sgd_hc.tenants.dto.TenantRegisterRequestDto;
import com.sgd_hc.tenants.dto.TenantResponseDto;
import com.sgd_hc.tenants.dto.TenantUpdateDto;
import com.sgd_hc.tenants.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador central para la gestión de Tenants.
 * Centraliza el onboarding público y la gestión privada de superadmin.
 */
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    // ── ENDPOINTS PÚBLICOS (Onboarding) ──────────────────────────────────────

    @GetMapping("/public/check-slug")
    public ResponseEntity<Map<String, Object>> checkSlug(@RequestParam String slug) {
        return ResponseEntity.ok(tenantService.checkSlug(slug));
    }

    @PostMapping("/public/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody TenantRegisterRequestDto dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.startRegistration(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/public/pay")
    public ResponseEntity<Map<String, Object>> pay(
            @Valid @RequestBody TenantPaymentRequestDto dto) {
        try {
            return ResponseEntity.ok(tenantService.processPayment(dto));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── GESTIÓN ADMINISTRATIVA (Requiere ROLE_SUPERUSER) ──────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_SUPERUSER')")
    public ResponseEntity<List<TenantResponseDto>> getAllTenants() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPERUSER')")
    public ResponseEntity<TenantResponseDto> getTenantById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(tenantService.getTenantById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPERUSER')")
    public ResponseEntity<TenantResponseDto> updateTenant(
            @PathVariable UUID id,
            @Valid @RequestBody TenantUpdateDto dto) {
        try {
            return ResponseEntity.ok(tenantService.updateTenant(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
