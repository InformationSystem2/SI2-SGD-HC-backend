package com.sgd_hc.tenants.service;

import com.sgd_hc.security.config.tenant.TenantContext;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantResolverService {

    private final TenantRepository tenantRepository;

    /**
     * Resuelve el Tenant actual basándose en el slug almacenado en el {@link TenantContext}.
     * @return El objeto Tenant correspondiente al slug del contexto.
     */
    public Tenant resolve() {
        UUID id = TenantContext.getCurrentTenantId();
        if (id != null) {
            return tenantRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Clínica no encontrada para el ID: " + id));
        }

        String slug = TenantContext.getCurrentTenantSlug();
        
        if (slug == null || slug.isBlank()) {
            throw new IllegalStateException("No se pudo resolver el Tenant: El contexto de tenant está vacío.");
        }

        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Clínica no encontrada para el slug: " + slug));
    }
}
