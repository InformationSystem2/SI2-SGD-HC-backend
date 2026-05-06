package com.sgd_hc.security.config.tenant;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TenantFilter implements Filter {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String tenantId = req.getHeader(TENANT_HEADER);

        // Si viene el header, lo guardamos en el contexto
        if (tenantId != null && !tenantId.isEmpty()) {
            TenantContext.setCurrentTenant(tenantId);
        } else {
            TenantContext.clear(); // Si no viene, usa la BD por defecto
        }

        try {
            // Permite que la petición continúe hacia tu Controlador
            chain.doFilter(request, response);
        } finally {
            // ¡VITAL! Limpiar el contexto al terminar para evitar cruce de datos y fugas de memoria
            TenantContext.clear();
        }
    }
}