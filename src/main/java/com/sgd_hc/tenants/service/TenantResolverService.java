package com.sgd_hc.tenants.service;

import com.sgd_hc.security.config.tenant.TenantContext;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantResolverService {

    private static final String DEFAULT_TENANT_SLUG = "default";

    private final TenantRepository tenantRepository;

    public Tenant resolve() {
        String raw = TenantContext.getCurrentTenant();
        final String slug = (raw == null || raw.isBlank()) ? DEFAULT_TENANT_SLUG : raw;
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + slug));
    }
}
