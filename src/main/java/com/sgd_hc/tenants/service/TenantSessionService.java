package com.sgd_hc.tenants.service;

import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.sgd_hc.audit.service.AuditableService;
import java.util.Map;

import com.sgd_hc.tenants.dto.TenantRegistrationDataDto;
import com.sgd_hc.tenants.mapper.TenantSessionMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSessionService implements AuditableService<String, TenantSessionService.SessionData> {

    private static final String SESSION_PREFIX = "tenant_session:";
    private static final long SESSION_TTL_MINUTES = 15;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final TenantSessionMapper tenantSessionMapper;

    private ObjectMapper getSessionMapper() {
        ObjectMapper mapper = objectMapper.copy();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        mapper.registerModule(javaTimeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        return mapper;
    }

    @Auditable(resourceType = "TENANT_SESSION", actionType = ActionType.CREATE)
    public String createSession(String plan, String billingCycle) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(SESSION_TTL_MINUTES * 60);

        SessionData data = new SessionData(plan, billingCycle != null ? billingCycle : "MONTHLY", expiresAt);
        saveSession(token, data);
        log.info("Created new tenant session with token: {}", token.substring(0, 8));
        return token;
    }

    private void saveSession(String token, SessionData data) {
        String key = buildKey(token);
        try {
            String json = getSessionMapper().writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(SESSION_TTL_MINUTES));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize session data", e);
            throw new RuntimeException("Failed to save session", e);
        }
    }

    public Optional<SessionData> getSession(String token) {
        String key = buildKey(token);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }

        try {
            SessionData data = getSessionMapper().readValue(json, SessionData.class);
            if (data.getExpiresAt() == null || Instant.now().isAfter(data.getExpiresAt())) {
                redisTemplate.delete(key);
                log.warn("Tenant session expired: {}", token.substring(0, 8));
                return Optional.empty();
            }
            return Optional.of(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize session data: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void saveRegistrationData(String token, TenantRegistrationDataDto regData) {
        var opt = getSession(token);
        if (opt.isPresent()) {
            SessionData data = opt.get();
            data.setRegistrationData(regData);
            saveSession(token, data);
        }
    }

    public void removeSession(String token) {
        String key = buildKey(token);
        redisTemplate.delete(key);
        log.info("Removed tenant session: {}", token.substring(0, 8));
    }

    public void cleanupExpired() {
        // Redis handles expiration automatically via TTL
    }

    private String buildKey(String token) {
        return SESSION_PREFIX + token;
    }

    @Getter
    @Setter
    public static class SessionData {
        private String plan;
        private String billingCycle;
        private Instant expiresAt;
        private TenantRegistrationDataDto registrationData;

        public SessionData() {}

        public SessionData(String plan, String billingCycle, Instant expiresAt) {
            this.plan = plan;
            this.billingCycle = billingCycle;
            this.expiresAt = expiresAt;
        }
    }

    @Override
    public SessionData getEntity(String id) {
        return getSession(id).orElse(null);
    }

    @Override
    public Map<String, Object> toAuditMap(SessionData entity) {
        return tenantSessionMapper.toAuditMap(entity);
    }

    @Override
    public Map<String, Object> toAuditMapFromResult(Object result) {
        if (result instanceof String token) {
            return getSession(token).map(this::toAuditMap).orElse(Map.of());
        }
        return Map.of();
    }
}