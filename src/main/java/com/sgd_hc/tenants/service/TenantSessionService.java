package com.sgd_hc.tenants.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSessionService {

    private static final String SESSION_PREFIX = "tenant_session:";
    private static final long SESSION_TTL_MINUTES = 15;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private ObjectMapper getSessionMapper() {
        ObjectMapper mapper = objectMapper.copy();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        mapper.registerModule(javaTimeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        return mapper;
    }

    public String createSession(String plan) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(SESSION_TTL_MINUTES * 60);

        SessionData data = new SessionData(plan, expiresAt);
        String key = buildKey(token);
        try {
            String json = getSessionMapper().writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(SESSION_TTL_MINUTES));
            log.info("Created new tenant session with token: {}", token.substring(0, 8));
            return token;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize session data", e);
            throw new RuntimeException("Failed to create session", e);
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

    public void saveRegistrationData(String token, RegistrationData regData) {
        String key = buildKey(token);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return;

        try {
            SessionData data = getSessionMapper().readValue(json, SessionData.class);
            data.setRegistrationData(regData);
            Long ttl = redisTemplate.getExpire(key, TimeUnit.MINUTES);
            long expireMinutes = ttl != null && ttl > 0 ? ttl : SESSION_TTL_MINUTES;
            redisTemplate.opsForValue().set(key, getSessionMapper().writeValueAsString(data), Duration.ofMinutes(expireMinutes));
        } catch (JsonProcessingException e) {
            log.error("Failed to update session data", e);
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
    public static class SessionData {
        private String plan;
        private RegistrationData registrationData;
        private Instant expiresAt;

        public SessionData() {}

        public SessionData(String plan, Instant expiresAt) {
            this.plan = plan;
            this.expiresAt = expiresAt;
        }

        public void setRegistrationData(RegistrationData registrationData) {
            this.registrationData = registrationData;
        }
    }

    @Getter
    public static class RegistrationData {
        private String tenantName;
        private String adminFirstName;
        private String adminLastName;
        private String adminEmail;
        private String adminPassword;
        private String adminPhone;
        private String adminDocumentType;
        private String adminDocumentNumber;
        private String adminGender;

        public RegistrationData() {}

        public RegistrationData(String tenantName, String adminFirstName, String adminLastName,
                               String adminEmail, String adminPassword, String adminPhone,
                               String adminDocumentType, String adminDocumentNumber, String adminGender) {
            this.tenantName = tenantName;
            this.adminFirstName = adminFirstName;
            this.adminLastName = adminLastName;
            this.adminEmail = adminEmail;
            this.adminPassword = adminPassword;
            this.adminPhone = adminPhone;
            this.adminDocumentType = adminDocumentType;
            this.adminDocumentNumber = adminDocumentNumber;
            this.adminGender = adminGender;
        }
    }
}