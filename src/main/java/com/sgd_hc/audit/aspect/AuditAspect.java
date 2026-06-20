package com.sgd_hc.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.AuditLog;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.entity.enums.SeverityLevel;
import com.sgd_hc.audit.filter.AuditContext;
import com.sgd_hc.audit.service.AuditableService;
import com.sgd_hc.audit.service.AuditLogService;
import com.sgd_hc.audit.util.AuditoriaUtils;
import com.sgd_hc.security.details.SecurityUser;
import com.sgd_hc.users.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Aspect
@Component
public class AuditAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /** ThreadLocal so AuthService can share the resolved User entity with the Aspect */
    public static final ThreadLocal<User> loginUserHolder = new ThreadLocal<>();

    public AuditAspect(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object target = joinPoint.getTarget();
        Object resourceId = extractId(joinPoint, auditable.idParamName());

        Map<String, Object> stateAntes = null;
        if (target instanceof AuditableService service
                && resourceId != null
                && (auditable.actionType() == ActionType.UPDATE
                    || auditable.actionType() == ActionType.DELETE)) {
            try {
                Object entity = service.getEntity(resourceId);
                if (entity != null) {
                    stateAntes = service.toAuditMap(entity);
                }
            } catch (Exception e) {
                log.debug("Could not get before state for {}: {}", auditable.resourceType(), e.getMessage());
            }
        }

        long start = System.currentTimeMillis();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            long executionTimeMs = System.currentTimeMillis() - start;
            buildAndSave(joinPoint, auditable, resourceId, target, stateAntes, null, executionTimeMs, t);
            throw t;
        }
        long executionTimeMs = System.currentTimeMillis() - start;

        Object idFinal = resourceId;
        if (idFinal == null && result != null && auditable.actionType() == ActionType.CREATE) {
            idFinal = extractIdFromResult(result);
        }

        Map<String, Object> stateDespues = null;
        if (target instanceof AuditableService service
                && result != null
                && auditable.actionType() != ActionType.DELETE) {
            try {
                stateDespues = service.toAuditMapFromResult(result);
                if ((stateDespues == null || stateDespues.isEmpty()) && idFinal != null) {
                    Object entityAfter = service.getEntity(idFinal);
                    if (entityAfter != null) {
                        stateDespues = service.toAuditMap(entityAfter);
                    }
                }
            } catch (Exception e) {
                log.debug("Could not get after state for {}: {}", auditable.resourceType(), e.getMessage());
            }
        }

        buildAndSave(joinPoint, auditable, idFinal, target, stateAntes, stateDespues, executionTimeMs, null);
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void buildAndSave(ProceedingJoinPoint joinPoint, Auditable auditable,
                               Object resourceId, Object target,
                               Map<String, Object> stateAntes,
                               Map<String, Object> stateDespues,
                               Long executionTimeMs,
                               Throwable error) {
        try {
            Map<String, Object> datosAntes = stateAntes;
            Map<String, Object> datosNuevos = stateDespues;

            if (auditable.actionType() == ActionType.UPDATE && stateAntes != null && stateDespues != null) {
                Map<String, Object>[] diff = AuditoriaUtils.calculateDiff(stateAntes, stateDespues);
                datosAntes = diff[0];
                datosNuevos = diff[1];
            }

            AuditLog entry = new AuditLog();
            entry.setActionType(auditable.actionType());
            entry.setResourceType(auditable.resourceType());
            entry.setResourceId(resourceId != null ? resourceId.toString() : null);
            entry.setResourceName(auditable.resourceType()
                    + (resourceId != null ? "/" + resourceId : ""));
            entry.setRequestMethod("ASPECT");
            entry.setRequestPath(joinPoint.getSignature().toShortString());
            entry.setResponseStatus(error != null ? 500 : 200);
            entry.setExecutionTimeMs(executionTimeMs);
            entry.setSeverity(error != null ? SeverityLevel.CRITICAL : SeverityLevel.INFO);

            // ── Extract HTTP context (IP, UserAgent, Session, ClientTime) ─────────────
            try {
                org.springframework.web.context.request.RequestAttributes attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
                if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
                    jakarta.servlet.http.HttpServletRequest request = servletAttrs.getRequest();

                    // IP address (respects X-Forwarded-For)
                    String ip = request.getHeader("X-Forwarded-For");
                    if (ip != null && !ip.isBlank()) {
                        entry.setIpAddress(ip.split(",")[0].trim());
                    } else {
                        entry.setIpAddress(request.getRemoteAddr());
                    }

                    // Extract actual HTTP Method and Path
                    entry.setRequestMethod(request.getMethod());
                    String queryString = request.getQueryString();
                    entry.setRequestPath(request.getRequestURI() + (queryString != null ? "?" + queryString : ""));

                    // User-Agent
                    String userAgent = request.getHeader("User-Agent");
                    if (userAgent != null && !userAgent.isBlank()) {
                        entry.setUserAgent(userAgent);
                    }

                    // X-Client-Time
                    String clientTimeHeader = request.getHeader("X-Client-Time");
                    if (clientTimeHeader != null && !clientTimeHeader.isBlank()) {
                        try {
                            entry.setClientTime(OffsetDateTime.parse(clientTimeHeader));
                        } catch (Exception ignored) {}
                    }

                    // X-Session-ID
                    String sessionIdHeader = request.getHeader("X-Session-ID");
                    if (sessionIdHeader != null && !sessionIdHeader.isBlank()) {
                        try {
                            entry.setSessionId(UUID.fromString(sessionIdHeader.trim()));
                        } catch (Exception ignored) {}
                    } else if (request.getSession(false) != null) {
                        try {
                            entry.setSessionId(UUID.nameUUIDFromBytes(
                                    request.getSession(false).getId().getBytes()));
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                log.debug("Could not extract HTTP context in aspect: {}", e.getMessage());
            }

            if (error != null) {
                entry.setErrorMessage(error.getMessage());
            }

            if (datosAntes != null && !datosAntes.isEmpty()) {
                entry.setChangesBefore(objectMapper.writeValueAsString(datosAntes)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            if (datosNuevos != null && !datosNuevos.isEmpty()) {
                entry.setChangesAfter(objectMapper.writeValueAsString(datosNuevos)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            if (target instanceof AuditableService service
                    && auditable.actionType() == ActionType.UPDATE && resourceId != null) {
                String reqJson = "{\"id\":\"" + resourceId + "\",\"action\":\""
                        + auditable.actionType() + "\"}";
                entry.setRequestBody(reqJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            // ── Resolve user: from SecurityContext (normal) or from method args (LOGIN) ──
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof SecurityUser securityUser) {
                User user = securityUser.getUser();
                entry.setUserId(user.getId());
                entry.setUserEmail(user.getEmail());
                entry.setUserName(user.getUsername());
                if (user.getTenant() != null) {
                    entry.setTenantId(user.getTenant().getId());
                }
            } else if (auditable.actionType() == ActionType.LOGIN) {
                // During login, SecurityContext is not yet populated — get user from method args
                resolveLoginUser(joinPoint, entry);
            }

            AuditContext.setAspectActive(true);
            auditLogService.logAction(entry);

        } catch (Exception e) {
            log.error("Error building @Auditable audit log", e);
        } finally {
            loginUserHolder.remove();
        }
    }

    /**
     * For LOGIN actions, the SecurityContext is not yet populated.
     * Try to find a username string in the method arguments and load the User entity.
     */
    private void resolveLoginUser(ProceedingJoinPoint joinPoint, AuditLog entry) {
        try {
            for (Object arg : joinPoint.getArgs()) {
                if (arg == null) continue;
                String username = null;
                // Check if the arg has a `username()` or `getUsername()` method
                for (java.lang.reflect.Method m : arg.getClass().getMethods()) {
                    if ((m.getName().equals("username") || m.getName().equals("getUsername"))
                            && m.getParameterCount() == 0) {
                        Object val = m.invoke(arg);
                        if (val instanceof String s && !s.isBlank()) {
                            username = s;
                        }
                        break;
                    }
                }
                if (username != null) {
                    // Look up user through a thread-local set by AuthService, or just record username
                    User resolvedUser = loginUserHolder.get();
                    if (resolvedUser != null) {
                        entry.setUserId(resolvedUser.getId());
                        entry.setUserEmail(resolvedUser.getEmail());
                        entry.setUserName(resolvedUser.getUsername());
                        if (resolvedUser.getTenant() != null) {
                            entry.setTenantId(resolvedUser.getTenant().getId());
                        }
                    } else {
                        entry.setUserName(username);
                        entry.setUserEmail(username);
                    }
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve login user from args: {}", e.getMessage());
        }
    }

    private Object extractId(ProceedingJoinPoint joinPoint, String idParamName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(idParamName)) {
                return args[i];
            }
        }
        return null;
    }

    private Object extractIdFromResult(Object result) {
        if (result == null) return null;
        try {
            for (java.lang.reflect.Method m : result.getClass().getMethods()) {
                if (m.getName().startsWith("get") && m.getName().toLowerCase().contains("id")
                        && m.getParameterCount() == 0) {
                    return m.invoke(result);
                }
            }
            for (java.lang.reflect.Field field : result.getClass().getDeclaredFields()) {
                if (field.getName().toLowerCase().contains("id")) {
                    try {
                        return result.getClass().getMethod(field.getName()).invoke(result);
                    } catch (NoSuchMethodException e) {
                        field.setAccessible(true);
                        return field.get(result);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warn("No se pudo extraer ID del resultado: {}", e.getMessage());
        }
        return null;
    }
}
