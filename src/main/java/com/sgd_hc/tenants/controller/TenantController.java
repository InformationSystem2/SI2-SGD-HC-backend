package com.sgd_hc.tenants.controller;

import com.sgd_hc.tenants.dto.*;
import com.sgd_hc.tenants.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/public/init-session")
    public ResponseEntity<TenantSessionResponseDto> initSession(
            @Valid @RequestBody TenantInitSessionDto dto) {
        return ResponseEntity.ok(tenantService.initSession(dto));
    }

    @PostMapping("/public/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody TenantRegisterRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.startRegistration(dto));
    }

    @PostMapping("/public/pay")
    public ResponseEntity<Map<String, Object>> pay(
            @Valid @RequestBody TenantPaymentRequestDto dto) {
        return ResponseEntity.ok(tenantService.processPayment(dto));
    }

    // ── SETTINGS (por slug - header X-Tenant-ID) ────────────────────────────

    @GetMapping("/current/settings")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPERUSER')")
    public ResponseEntity<Map<String, Object>> getSettingsBySlug(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantSlug) {
        return ResponseEntity.ok(tenantService.getSettingsBySlug(tenantSlug));
    }

    @PutMapping("/current/settings")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPERUSER')")
    public ResponseEntity<Map<String, Object>> updateSettingsBySlug(
            @RequestHeader(value = "X-Tenant-ID", required = true) String tenantSlug,
            @RequestBody Map<String, Object> settings) {
        return ResponseEntity.ok(tenantService.updateSettingsBySlug(tenantSlug, settings));
    }

    // ── INFORMACIÓN BÁSICA DEL TENANT (por slug) ────────────────────────────

    @GetMapping("/current/info")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPERUSER')")
    public ResponseEntity<TenantInfoDto> getTenantInfo(
            @RequestHeader(value = "X-Tenant-ID", required = true) String tenantSlug) {
        return ResponseEntity.ok(tenantService.getTenantInfoBySlug(tenantSlug));
    }

    @PutMapping("/current/info")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPERUSER')")
    public ResponseEntity<TenantInfoDto> updateTenantInfo(
            @RequestHeader(value = "X-Tenant-ID", required = true) String tenantSlug,
            @RequestBody Map<String, Object> data) {
        return ResponseEntity.ok(tenantService.updateTenantBasicInfo(tenantSlug, data));
    }

    @GetMapping("/current/stats")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPERUSER')")
    public ResponseEntity<TenantStatsDto> getTenantStats(
            @RequestHeader(value = "X-Tenant-ID", required = true) String tenantSlug) {
        return ResponseEntity.ok(tenantService.getTenantStats(tenantSlug));
    }

    // ── SUSCRIPCIÓN: Renovación y Cambio de Plan ─────────────────────────────

    @PostMapping("/current/renew")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPERUSER')")
    public ResponseEntity<RenewSubscriptionResponseDto> renewSubscription(
            @RequestHeader(value = "X-Tenant-ID", required = true) String tenantSlug,
            @RequestBody String plan) {
        return ResponseEntity.ok(tenantService.renewSubscription(tenantSlug, plan));
    }

    @PostMapping("/current/change-plan")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPERUSER')")
    public ResponseEntity<ChangePlanResponseDto> changePlan(
            @RequestHeader(value = "X-Tenant-ID", required = true) String tenantSlug,
            @RequestBody String newPlan) {
        return ResponseEntity.ok(tenantService.changePlan(tenantSlug, newPlan));
    }

    // ── GESTIÓN SUPERADMIN - HU-17 (Listado, Detalle, Suspensión, Eliminación) ──

    @GetMapping("/admin/list")
    @PreAuthorize("hasAuthority('ROLE_SUPERUSER')")
    public ResponseEntity<PageResponseDto<TenantListItemDto>> getTenantsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(tenantService.getTenantsPaged(page, size, search));
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPERUSER')")
    public ResponseEntity<TenantDetailDto> getTenantDetails(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.getTenantDetails(id));
    }

    @PutMapping("/admin/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_SUPERUSER')")
    public ResponseEntity<TenantDetailDto> updateTenantStatus(
            @PathVariable UUID id,
            @RequestBody String action) {
        return ResponseEntity.ok(tenantService.updateTenantStatus(id, action));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPERUSER')")
    public ResponseEntity<Map<String, String>> deleteTenant(
            @PathVariable UUID id,
            @RequestParam(required = false) String confirmText) {
        if (confirmText == null || confirmText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Para eliminar un tenant se requiere escribir el nombre del tenant para confirmar."
            ));
        }
        tenantService.hardDeleteTenant(id, confirmText);
        return ResponseEntity.ok(Map.of("message", "Tenant eliminado permanentemente."));
    }
}