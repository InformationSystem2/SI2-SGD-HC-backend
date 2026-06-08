package com.sgd_hc.audit.filter;

import com.sgd_hc.audit.entity.AuditLog;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.entity.enums.SeverityLevel;
import com.sgd_hc.audit.service.AuditLogService;
import java.time.OffsetDateTime;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.users.entity.User;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogFilter extends OncePerRequestFilter {

    private final AuditLogService auditLogService;

    @Value("${audit.trusted-proxies:}")
    private String trustedProxiesConfig;

    @Value("${audit.exclude.paths:}")
    private String excludedPathsConfig;

    private List<InetAddress> trustedProxies = Collections.emptyList();
    private Set<String> excludedPaths = Set.of(
            "/api/schema", "/api-docs", "/swagger", "/health", "/metrics",
            "/api/auth/login", "/api/auth/refresh"
    );

    @PostConstruct
    void init() {
        this.trustedProxies = parseTrustedProxies();
        if (excludedPathsConfig != null && !excludedPathsConfig.isBlank()) {
            this.excludedPaths = Arrays.stream(excludedPathsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());
        }
    }

    private static final Set<String> AUDITABLE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!shouldAudit(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();
        boolean isMultipart = request.getContentType() != null && 
                              request.getContentType().toLowerCase().startsWith("multipart/");
                              
        HttpServletRequest requestToUse = isMultipart ? request : new CachedBodyHttpServletRequest(request);

        try {
            try {
                filterChain.doFilter(requestToUse, response);
            } catch (Exception e) {
                log.error("Error processing request for audit", e);
                buildAndSaveAuditLog(requestToUse, 500, e.getMessage(), System.currentTimeMillis() - startTime);
                throw e;
            }

            if (!AuditContext.isAspectActive()) {
                String errorMsg = (String) requestToUse.getAttribute("auditErrorMessage");
                buildAndSaveAuditLog(requestToUse, response.getStatus(), errorMsg, System.currentTimeMillis() - startTime);
            } else {
                log.debug("Skipping filter audit log: already captured by @Auditable aspect");
            }
        } finally {
            AuditContext.clear();
        }
    }

    private boolean shouldAudit(HttpServletRequest request) {
        String method = request.getMethod();
        if (!AUDITABLE_METHODS.contains(method)) {
            return false;
        }

        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return false;
        }

        return excludedPaths.stream().noneMatch(path::startsWith);
    }

    private void buildAndSaveAuditLog(HttpServletRequest request,
                                      int statusCode,
                                      String errorMessage,
                                      long durationMs) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            AuditLog entry = new AuditLog();
            entry.setRequestMethod(request.getMethod());
            String queryString = request.getQueryString();
            entry.setRequestPath(request.getRequestURI() + (queryString != null ? "?" + queryString : ""));
            entry.setIpAddress(getClientIp(request));
            entry.setUserAgent(request.getHeader("User-Agent"));
            entry.setResponseStatus(statusCode);
            entry.setErrorMessage(errorMessage);
            entry.setExecutionTimeMs(durationMs);
            
            SeverityLevel severity = SeverityLevel.INFO;
            if (statusCode >= 500) {
                severity = SeverityLevel.CRITICAL;
            } else if (statusCode >= 400) {
                severity = SeverityLevel.WARNING;
            }
            entry.setSeverity(severity);

            String clientTimeHeader = request.getHeader("X-Client-Time");
            if (clientTimeHeader != null && !clientTimeHeader.isBlank()) {
                try {
                    entry.setClientTime(OffsetDateTime.parse(clientTimeHeader));
                } catch (Exception e) {
                    log.warn("Invalid X-Client-Time header format: {}", clientTimeHeader);
                }
            }

            String sessionIdHeader = request.getHeader("X-Session-ID");
            if (sessionIdHeader != null && !sessionIdHeader.isBlank()) {
                try {
                    entry.setSessionId(UUID.fromString(sessionIdHeader.trim()));
                } catch (Exception ignored) {}
            } else if (request.getSession(false) != null) {
                try {
                    entry.setSessionId(UUID.nameUUIDFromBytes(request.getSession(false).getId().getBytes()));
                } catch (Exception ignored) {}
            }

            if (request instanceof CachedBodyHttpServletRequest cachedReq) {
                String body = cachedReq.getCachedBodyAsString();
                if (body != null && !body.isBlank()) {
                    entry.setRequestBody(body.getBytes());
                }
            }

            String uri = request.getRequestURI();
            entry.setResourceType(extractResourceType(uri));
            entry.setResourceId(extractResourceId(uri));
            entry.setActionType(inferActionType(request.getMethod()));

            if (auth != null && auth.getPrincipal() instanceof SecurityUser securityUser) {
                User user = securityUser.getUser();
                entry.setUserId(user.getId());
                entry.setUserEmail(user.getEmail());
                entry.setUserName(user.getUsername());
                if (user.getTenant() != null) {
                    entry.setTenantId(user.getTenant().getId());
                }
            }

            auditLogService.logAction(entry);

        } catch (Exception e) {
            log.error("Error building audit log entry", e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] ips = xForwardedFor.split(",");
            if (trustedProxies.isEmpty()) {
                return ips[0].trim();
            }
            for (int i = ips.length - 1; i >= 0; i--) {
                String ip = ips[i].trim();
                if (!ip.isBlank() && !isTrustedProxy(ip)) {
                    return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isTrustedProxy(String ip) {
        if (trustedProxies.isEmpty()) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            for (InetAddress trusted : trustedProxies) {
                if (trusted.equals(addr)) {
                    return true;
                }
            }
        } catch (UnknownHostException ignored) {
        }
        return false;
    }

    private List<InetAddress> parseTrustedProxies() {
        if (trustedProxiesConfig == null || trustedProxiesConfig.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(trustedProxiesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(ip -> {
                    try {
                        return InetAddress.getByName(ip);
                    } catch (UnknownHostException e) {
                        log.warn("Invalid trusted proxy IP: {}", ip);
                        return null;
                    }
                })
                .filter(a -> a != null)
                .collect(Collectors.toList());
    }

    private String extractResourceType(String uri) {
        if (uri == null) return "UNKNOWN";
        String[] segments = uri.replaceFirst("^/api/", "").split("/");
        if (segments.length >= 1) {
            String primary = segments[0].toUpperCase();
            if ("MODULE_USERS".equals(primary) && segments.length >= 2) {
                return segments[1].toUpperCase();
            }
            return primary;
        }
        return "UNKNOWN";
    }

    private String extractResourceId(String uri) {
        if (uri == null) return null;
        String[] segments = uri.replaceFirst("^/api/", "").split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i];
            try {
                UUID.fromString(segment);
                return segment;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private ActionType inferActionType(String method) {
        return switch (method.toUpperCase()) {
            case "POST" -> ActionType.CREATE;
            case "PUT", "PATCH" -> ActionType.UPDATE;
            case "DELETE" -> ActionType.DELETE;
            default -> ActionType.READ;
        };
    }
}
