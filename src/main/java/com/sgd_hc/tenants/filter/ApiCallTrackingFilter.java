package com.sgd_hc.tenants.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgd_hc.security.exception.PlanLimitExceededException;
import com.sgd_hc.tenants.repository.ApiCallUsageRepository;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.tenants.service.PlanLimitValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiCallTrackingFilter extends OncePerRequestFilter {

    private final PlanLimitValidator planLimitValidator;
    private final ApiCallUsageRepository apiCallUsageRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final Set<String> EXCLUDED_PATH_PREFIXES = Set.of(
            "/api/auth",
            "/api/public",
            "/api/schema",
            "/api-docs",
            "/swagger",
            "/health",
            "/metrics",
            "/error",
            "/uploads"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!path.startsWith("/api/") || isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID tenantId = resolveTenantId(request);
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String yearMonth = LocalDate.now().format(YEAR_MONTH);

        try {
            planLimitValidator.checkApiCallsLimit(tenantId);
        } catch (PlanLimitExceededException e) {
            writeLimitExceeded(response, e.getCurrentCount(), e.getMaxLimit());
            return;
        } catch (Exception e) {
            log.warn("Error checking API call limit for tenant {}: {}", tenantId, e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            throw e;
        }

        if (response.getStatus() >= 200 && response.getStatus() < 300) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                    apiCallUsageRepository.incrementCallCount(tenantId, yearMonth)
                );
            } catch (Exception e) {
                log.warn("Failed to track API call for tenant {}: {}", tenantId, e.getMessage());
            }
        }
    }

    private void writeLimitExceeded(HttpServletResponse response, long current, long max) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
                "error", "Plan Limit Exceeded",
                "resourceType", "llamadas API (este mes)",
                "currentCount", current,
                "maxLimit", max
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private boolean isExcluded(String path) {
        return EXCLUDED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private UUID resolveTenantId(HttpServletRequest request) {
        String tenantHeader = request.getHeader("X-Tenant-ID");
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                return UUID.fromString(tenantHeader.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String[] parts = token.split("\\.");
                if (parts.length == 3) {
                    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                    int tenantIdx = payload.indexOf("\"tenantId\"");
                    if (tenantIdx >= 0) {
                        int colonIdx = payload.indexOf(':', tenantIdx);
                        int start = colonIdx + 1;
                        while (start < payload.length() && payload.charAt(start) == ' ') start++;
                        int end = start;
                        while (end < payload.length() && payload.charAt(end) != ',' && payload.charAt(end) != '}') end++;
                        String value = payload.substring(start, end).replace("\"", "").trim();
                        return UUID.fromString(value);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }
}
