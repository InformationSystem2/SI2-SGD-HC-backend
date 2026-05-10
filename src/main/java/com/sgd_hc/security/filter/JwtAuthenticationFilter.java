package com.sgd_hc.security.filter;

import com.sgd_hc.security.config.tenant.TenantContext;
import com.sgd_hc.security.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro JWT que:
 * 1. Valida el token Bearer en cada petición.
 * 2. Establece el SecurityContext de Spring (autenticación).
 * 3. Puebla {@link TenantContext} con el slug del tenant extraído del token,
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Intentar capturar el Tenant desde el Header (esencial para el Login sin token)
        String tenantHeader = request.getHeader("X-Tenant-ID");
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            TenantContext.setCurrentTenantSlug(tenantHeader);
        }

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }

        final String jwt = authHeader.substring(7);
        final String username;

        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtService.isTokenValid(jwt, userDetails)) {
                // 1. Establecer autenticación en Spring Security
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(request);
                SecurityContextHolder.getContext().setAuthentication(authToken);

                // 2. Poblar TenantContext con los datos del JWT.
                String tenantSlug = jwtService.extractTenantSlug(jwt);
                String tenantIdStr = jwtService.extractTenantId(jwt);

                if (tenantSlug != null && !tenantSlug.isBlank()) {
                    TenantContext.setCurrentTenantSlug(tenantSlug);
                }
                if (tenantIdStr != null && !tenantIdStr.isBlank()) {
                    try {
                        TenantContext.setCurrentTenantId(UUID.fromString(tenantIdStr));
                    } catch (IllegalArgumentException e) {
                        logger.error("Tenant ID inválido en JWT: " + tenantIdStr);
                    }
                }
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
