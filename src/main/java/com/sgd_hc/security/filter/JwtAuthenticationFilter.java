package com.sgd_hc.security.filter;

import com.sgd_hc.security.config.tenant.TenantContext;
import com.sgd_hc.security.exception.TenantSuspendedException;
import com.sgd_hc.security.service.JwtService;
import com.sgd_hc.tenants.service.TenantRevocationService;
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
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TenantRevocationService revocationService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

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
                String tenantIdStr = jwtService.extractTenantId(jwt);
                String jti = jwtService.extractJti(jwt);
                Instant tokenIssuedAt = jwtService.extractIssuedAt(jwt);

                if (tenantIdStr != null && jti != null && tokenIssuedAt != null) {
                    UUID tenantId = UUID.fromString(tenantIdStr);
                    if (revocationService.isTokenRevoked(jti, tenantId, tokenIssuedAt)) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write(
                                "{\"error\":\"Token revoked\",\"message\":\"Su sesión ha sido invalidada.\"}"
                        );
                        return;
                    }
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(request);
                SecurityContextHolder.getContext().setAuthentication(authToken);

                String tenantSlug = jwtService.extractTenantSlug(jwt);
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