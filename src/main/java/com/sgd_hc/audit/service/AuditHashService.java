package com.sgd_hc.audit.service;

import com.sgd_hc.audit.entity.AuditLog;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

@Slf4j
@Service
public class AuditHashService {

    @Value("${audit.integrity.key}")
    private String integrityKey;

    @PostConstruct
    public void init() {
        if (integrityKey == null || integrityKey.length() < 32) {
            log.warn("Audit integrity key is too short (< 32 chars). Generate a secure key.");
        }
    }

    public String generateHash(AuditLog auditLog) {
        try {
            TreeMap<String, String> hashData = new TreeMap<>();
            hashData.put("id", auditLog.getId() != null ? auditLog.getId().toString() : "");
            hashData.put("tenantId", auditLog.getTenantId() != null ? auditLog.getTenantId().toString() : "");
            hashData.put("userId", auditLog.getUserId() != null ? auditLog.getUserId().toString() : "");
            hashData.put("userEmail", auditLog.getUserEmail() != null ? auditLog.getUserEmail() : "");
            hashData.put("actionType", auditLog.getActionType() != null ? auditLog.getActionType().name() : "");
            hashData.put("resourceType", auditLog.getResourceType() != null ? auditLog.getResourceType() : "");
            hashData.put("resourceId", auditLog.getResourceId() != null ? auditLog.getResourceId() : "");
            hashData.put("createdAt", auditLog.getCreatedAt() != null ? auditLog.getCreatedAt().toString() : "");
            hashData.put("ipAddress", auditLog.getIpAddress() != null ? auditLog.getIpAddress() : "");
            hashData.put("clientTime", auditLog.getClientTime() != null ? auditLog.getClientTime().toString() : "");
            hashData.put("sessionId", auditLog.getSessionId() != null ? auditLog.getSessionId().toString() : "");
            hashData.put("severity", auditLog.getSeverity() != null ? auditLog.getSeverity().name() : "");
            hashData.put("executionTimeMs", auditLog.getExecutionTimeMs() != null ? auditLog.getExecutionTimeMs().toString() : "");

            if (auditLog.getRequestBody() != null) {
                hashData.put("requestBodyHash", sha256Hex(auditLog.getRequestBody()));
            }
            if (auditLog.getChangesBefore() != null) {
                hashData.put("changesBeforeHash", sha256Hex(auditLog.getChangesBefore()));
            }
            if (auditLog.getChangesAfter() != null) {
                hashData.put("changesAfterHash", sha256Hex(auditLog.getChangesAfter()));
            }

            String data = hashData.toString();

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    integrityKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);

            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));

        } catch (Exception e) {
            log.error("Error generating audit hash", e);
            throw new RuntimeException("Error generating audit hash", e);
        }
    }

    public boolean verifyHash(AuditLog auditLog) {
        String storedHash = auditLog.getIntegrityHash();
        if (storedHash == null) return false;

        auditLog.setIntegrityHash(null);
        String calculatedHash = generateHash(auditLog);
        auditLog.setIntegrityHash(storedHash);

        return storedHash.equals(calculatedHash);
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new RuntimeException("Error computing SHA-256", e);
        }
    }
}
