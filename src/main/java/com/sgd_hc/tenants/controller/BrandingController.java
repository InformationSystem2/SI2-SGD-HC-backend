package com.sgd_hc.tenants.controller;

import com.sgd_hc.tenants.service.BrandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class BrandingController {

    private final BrandingService brandingService;

    // Public endpoint: reads tenant from X-Tenant-ID header (slug)
    @GetMapping("/api/branding")
    public ResponseEntity<Map<String, Object>> getBranding(@RequestHeader(value = "X-Tenant-ID", required = false) String tenantSlug) {
        Map<String, Object> branding = brandingService.getBrandingByTenantSlug(tenantSlug);
        return ResponseEntity.ok(branding);
    }

    @PostMapping(value = "/api/branding/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERUSER','ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> uploadLogo(@RequestHeader(value = "X-Tenant-ID", required = true) String tenantSlug,
                                                            @RequestParam("file") MultipartFile file) throws IOException {
        Map<String, Object> result = brandingService.uploadLogo(tenantSlug, file);
        return ResponseEntity.ok(result);
    }


    @PatchMapping("/api/branding")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERUSER','ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateBrandingBySlug(@RequestHeader(value = "X-Tenant-ID", required = true) String tenantSlug,
                                                                    @RequestBody Map<String, Object> payload) {
        Map<String, Object> updated = brandingService.updateBrandingBySlug(tenantSlug, payload);
        return ResponseEntity.ok(updated);
    }
}
