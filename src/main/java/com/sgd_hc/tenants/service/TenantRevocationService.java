package com.sgd_hc.tenants.service;

import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.enums.ActionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantRevocationService {

    private static final String REVOKED_TOKEN_PREFIX = "revoked:";
    private static final String SUSPENDED_TENANT_PREFIX = "suspended:";
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, String> redisTemplate;

    @Auditable(resourceType = "TENANT_REVOCATION", actionType = ActionType.UPDATE)
    public void revokeAllTokensForTenant(UUID tenantId, Instant suspendedAt) {
        String key = suspendedKey(tenantId);
        redisTemplate.opsForValue().set(key, suspendedAt.toString());
        redisTemplate.expire(key, Duration.ofDays(30));

        log.info("Tenant {} suspendido. Todos los tokens anteriores a {} serán rechazados.",
                tenantId, suspendedAt);
    }

    @Auditable(resourceType = "TENANT_REVOCATION", actionType = ActionType.UPDATE)
    public void reactivateTenant(UUID tenantId) {
        String key = suspendedKey(tenantId);
        redisTemplate.delete(key);

        String pattern = revokedKeyPattern(tenantId);
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        log.info("Tenant {} reactivado. Todos los tokens han sido restaurados.", tenantId);
    }

    public boolean isTokenRevoked(String tokenJti, UUID tenantId, Instant tokenIssuedAt) {
        String suspendedKey = suspendedKey(tenantId);
        String suspendedAtStr = redisTemplate.opsForValue().get(suspendedKey);

        if (suspendedAtStr != null) {
            Instant suspendedAt = Instant.parse(suspendedAtStr);
            if (tokenIssuedAt.isBefore(suspendedAt)) {
                log.debug("Token {} revocado: emitido antes de la suspensión del tenant {}",
                        tokenJti, tenantId);
                return true;
            }
        }

        String revokedKey = revokedKey(tenantId, tokenJti);
        return Boolean.TRUE.equals(redisTemplate.hasKey(revokedKey));
    }

    @Auditable(resourceType = "TENANT_REVOCATION", actionType = ActionType.UPDATE)
    public void revokeToken(String tokenJti, UUID tenantId, long expirationEpoch) {
        String key = revokedKey(tenantId, tokenJti);
        long ttlSeconds = expirationEpoch - Instant.now().getEpochSecond();

        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(ttlSeconds));
            log.debug("Token {} marcado como revocado (TTL: {}s)", tokenJti, ttlSeconds);
        }
    }

    private String suspendedKey(UUID tenantId) {
        return SUSPENDED_TENANT_PREFIX + tenantId.toString();
    }

    private String revokedKey(UUID tenantId, String jti) {
        return REVOKED_TOKEN_PREFIX + tenantId.toString() + ":" + jti;
    }

    private String revokedKeyPattern(UUID tenantId) {
        return REVOKED_TOKEN_PREFIX + tenantId.toString() + ":*";
    }
}