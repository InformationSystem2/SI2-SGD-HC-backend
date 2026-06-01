package com.sgd_hc.security.config.tenant;

import com.sgd_hc.tenants.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.sgd_hc.security.details.SecurityUser;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

/**
 * Aspecto AOP que activa el filtro Hibernate {@code tenantFilter} antes de
 * ejecutar cualquier método de un repositorio Spring Data JPA.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TenantFilterAspect {

    private final EntityManager entityManager;
    private final TenantRepository tenantRepository;

    @Value("${app.seed.system.slug}")
    private String systemSlug;

    // Ejecuta antes de cualquier método de un repositorio Spring Data JPA que no sea del tenant
    @Around("execution(* org.springframework.data.repository.Repository+.*(..)) && !target(com.sgd_hc.tenants.repository.TenantRepository)")
    public Object applyTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        
        // 1. Bypass check
        if (TenantContext.isBypassFilter()) {
            log.trace("[TenantFilter] Bypass activo. Procediendo sin filtro.");
            return joinPoint.proceed();
        }

        Session session = entityManager.unwrap(Session.class);
        UUID tenantId = TenantContext.getCurrentTenantId();
        String tenantSlug = TenantContext.getCurrentTenantSlug();
        boolean superUser = isSuperUser();

        // 2. Superuser check (system tenant access)
        if (superUser && systemSlug.equals(tenantSlug)) {
            log.trace("[TenantFilter] Superusuario detectado en tenant '{}'. Procediendo sin filtro.", systemSlug);
            return joinPoint.proceed();
        }

        // 3. Fast path: tenantId already resolved (e.g. from JWT)
        if (tenantId != null) {
            activateFilter(session, tenantId);
            return joinPoint.proceed();
        }

        // 4. Slow path: resolve tenantId from slug (e.g. from Header or Login process)
        if (tenantSlug != null && !tenantSlug.isBlank()) {
            tenantId = tenantRepository.findBySlug(tenantSlug)
                    .map(t -> t.getId())
                    .orElse(null);

            if (tenantId == null) {
                log.error("[TenantFilter] Clínica con slug '{}' no encontrada en la base de datos.", tenantSlug);
                throw new SecurityException("Clínica inválida.");
            }

            // Save to context to avoid re-querying in the same request
            TenantContext.setCurrentTenantId(tenantId);
            activateFilter(session, tenantId);
            return joinPoint.proceed();
        }

        // 5. Hard block: No tenant identified and not in bypass mode
        log.warn("[TenantFilter] Intento de acceso a repositorio sin identificación de clínica. Método: {}", joinPoint.getSignature().getName());
        throw new SecurityException("Acceso denegado: No se ha identificado la clínica.");
    }

    private void activateFilter(Session session, UUID tenantId) {
        Filter filter = session.enableFilter("tenantFilter");
        filter.setParameter("tenantId", tenantId);
        log.trace("[TenantFilter] Filtro Hibernate activado para ID: {}", tenantId);
    }

    private boolean isSuperUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;

        boolean hasRole = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPERUSER".equals(a.getAuthority()));
        
        if (!hasRole) return false;

        if (auth.getPrincipal() instanceof SecurityUser su) {
            String userTenantSlug = su.getUser().getTenant().getSlug();
            return systemSlug.equals(userTenantSlug);
        }

        return false;
    }
}
