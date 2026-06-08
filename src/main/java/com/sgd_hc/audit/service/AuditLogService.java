package com.sgd_hc.audit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgd_hc.audit.dto.AuditFilterDto;
import com.sgd_hc.audit.dto.AuditLogResponseDto;
import com.sgd_hc.audit.entity.AuditLog;
import com.sgd_hc.audit.mapper.AuditLogMapper;
import com.sgd_hc.audit.repository.AuditLogRepository;
import com.sgd_hc.audit.repository.AuditLogSpecifications;
import com.sgd_hc.audit.util.AuditoriaUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
public class AuditLogService {

    private static final org.slf4j.Logger auditLogger = org.slf4j.LoggerFactory.getLogger("AUDIT");

    private final AuditLogRepository auditLogRepository;
    private final AuditEncryptionService encryptionService;
    private final AuditHashService hashService;
    private final ObjectMapper objectMapper;
    private final AuditLogMapper auditLogMapper;

    private final ConcurrentLinkedQueue<AuditLog> failedBuffer = new ConcurrentLinkedQueue<>();

    @Value("${audit.buffer.max-size:1000}")
    private int maxBufferSize;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           AuditEncryptionService encryptionService,
                           AuditHashService hashService,
                           ObjectMapper objectMapper,
                           AuditLogMapper auditLogMapper) {
        this.auditLogRepository = auditLogRepository;
        this.encryptionService = encryptionService;
        this.hashService = hashService;
        this.objectMapper = objectMapper;
        this.auditLogMapper = auditLogMapper;
    }

    @Async("auditTaskExecutor")
    @Transactional(transactionManager = "auditTransactionManager")
    public void logAction(AuditLog auditLog) {
        try {
            if (auditLog.getId() == null) {
                auditLog.setId(UUID.randomUUID());
            }
            if (auditLog.getCreatedAt() == null) {
                OffsetDateTime now = OffsetDateTime.now();
                auditLog.setCreatedAt(now);
                auditLog.setUpdatedAt(now);
            }

            auditLog.setResourceType(truncate(auditLog.getResourceType(), 100));
            auditLog.setResourceId(truncate(auditLog.getResourceId(), 100));
            auditLog.setResourceName(truncate(auditLog.getResourceName(), 255));
            auditLog.setIpAddress(truncate(auditLog.getIpAddress(), 45));
            auditLog.setRequestMethod(truncate(auditLog.getRequestMethod(), 10));
            auditLog.setRequestPath(truncate(auditLog.getRequestPath(), 500));
            auditLog.setUserEmail(truncate(auditLog.getUserEmail(), 255));
            auditLog.setUserName(truncate(auditLog.getUserName(), 255));

            encryptAndSanitizeFields(auditLog);
            auditLog.setIntegrityHash(hashService.generateHash(auditLog));

            AuditLog saved = auditLogRepository.save(auditLog);
            sendToGcpLogging(saved);

        } catch (Exception e) {
            log.error("Error saving audit log (non-blocking): {}", e.getMessage(), e);
            addToBuffer(auditLog);
        }
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional(transactionManager = "auditTransactionManager")
    public void retryFailedLogs() {
        if (failedBuffer.isEmpty()) return;

        log.info("Retrying {} failed audit logs from buffer", failedBuffer.size());
        List<AuditLog> toRetry = new ArrayList<>();
        AuditLog entry;
        while ((entry = failedBuffer.poll()) != null) {
            toRetry.add(entry);
        }

        int succeeded = 0;
        for (AuditLog e : toRetry) {
            try {
                AuditLog saved = auditLogRepository.save(e);
                sendToGcpLogging(saved);
                succeeded++;
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                log.error("Audit log failed with DataIntegrityViolationException, discarding it. Error: {}", ex.getMessage());
            } catch (Exception ex) {
                log.error("Retry failed for audit log, re-queuing", ex);
                failedBuffer.offer(e);
            }
        }

        if (succeeded > 0) {
            log.info("Recovered {} audit logs from buffer ({} remaining)",
                    succeeded, failedBuffer.size());
        }
    }

    private void addToBuffer(AuditLog auditLog) {
        if (failedBuffer.size() >= maxBufferSize) {
            failedBuffer.poll();
            log.warn("Audit buffer full ({}), oldest entry discarded", maxBufferSize);
        }
        failedBuffer.offer(auditLog);
        log.info("Audit log queued to buffer (size: {})", failedBuffer.size());
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }

    @Transactional(readOnly = true, transactionManager = "auditTransactionManager")
    public Page<AuditLogResponseDto> findAll(AuditFilterDto filter, Pageable pageable) {
        return auditLogRepository.findAll(AuditLogSpecifications.build(filter), pageable)
                .map(auditLogMapper::toResponseDto);
    }

    @Transactional(readOnly = true, transactionManager = "auditTransactionManager")
    public AuditLogResponseDto findById(UUID id) {
        AuditLog entry = auditLogRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Audit log not found: " + id));

        if (!hashService.verifyHash(entry)) {
            log.warn("Audit log integrity violation detected: {}", id);
        }

        return auditLogMapper.toResponseDto(entry);
    }

    @Transactional(readOnly = true, transactionManager = "auditTransactionManager")
    public IntegrityCheckResult verifyIntegrity(UUID id) {
        AuditLog entry = auditLogRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Audit log not found: " + id));

        boolean isValid = hashService.verifyHash(entry);

        return new IntegrityCheckResult(id, isValid,
                isValid ? "Log integro" : "ADVERTENCIA: Log ha sido manipulado");
    }

    @Transactional(readOnly = true, transactionManager = "auditTransactionManager")
    public List<IntegrityCheckResult> verifyAll(int limit) {
        Page<AuditLog> page = auditLogRepository.findAll(
                PageRequest.of(0, Math.min(limit, 500)));

        List<IntegrityCheckResult> results = new ArrayList<>();
        for (AuditLog entry : page.getContent()) {
            boolean isValid = hashService.verifyHash(entry);
            results.add(new IntegrityCheckResult(entry.getId(), isValid,
                    isValid ? "Log integro" : "ADVERTENCIA: Log ha sido manipulado"));
        }

        long manipulationCount = results.stream().filter(r -> !r.valid()).count();
        log.info("Integrity check completed: {} logs checked, {} manipulations detected",
                results.size(), manipulationCount);

        return results;
    }

    @Transactional(readOnly = true, transactionManager = "auditTransactionManager")
    public byte[] exportCsv(AuditFilterDto filter) {
        List<AuditLog> logs = auditLogRepository.findAll(AuditLogSpecifications.build(filter),
                PageRequest.of(0, 100000, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))
                .getContent();

        StringBuilder sb = new StringBuilder();
        sb.append("id,tenantId,userId,userEmail,userName,actionType,");
        sb.append("resourceType,resourceId,resourceName,ipAddress,");
        sb.append("requestMethod,requestPath,responseStatus,errorMessage,createdAt,clientTime,integrityValid\n");

        for (AuditLog entry : logs) {
            sb.append(escapeCsv(entry.getId())).append(",");
            sb.append(escapeCsv(entry.getTenantId())).append(",");
            sb.append(escapeCsv(entry.getUserId())).append(",");
            sb.append(escapeCsv(entry.getUserEmail())).append(",");
            sb.append(escapeCsv(entry.getUserName())).append(",");
            sb.append(escapeCsv(entry.getActionType())).append(",");
            sb.append(escapeCsv(entry.getResourceType())).append(",");
            sb.append(escapeCsv(entry.getResourceId())).append(",");
            sb.append(escapeCsv(entry.getResourceName())).append(",");
            sb.append(escapeCsv(entry.getIpAddress())).append(",");
            sb.append(escapeCsv(entry.getRequestMethod())).append(",");
            sb.append(escapeCsv(entry.getRequestPath())).append(",");
            sb.append(entry.getResponseStatus()).append(",");
            sb.append(escapeCsv(entry.getErrorMessage())).append(",");
            sb.append(entry.getCreatedAt()).append(",");
            sb.append(escapeCsv(entry.getClientTime())).append(",");
            sb.append(hashService.verifyHash(entry) ? "VALID" : "MANIPULATED").append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(Object value) {
        if (value == null) return "\"\"";
        String str = value.toString().replace("\"", "\"\"");
        return "\"" + str + "\"";
    }

    private void encryptAndSanitizeFields(AuditLog auditLog) {
        if (auditLog.getRequestBody() != null && auditLog.getRequestBody().length > 0) {
            String bodyJson = sanitizeAndSerialize(auditLog.getRequestBody());
            auditLog.setRequestBody(encryptionService.encrypt(bodyJson));
        }

        if (auditLog.getChangesBefore() != null && auditLog.getChangesBefore().length > 0) {
            String beforeJson = sanitizeAndSerialize(auditLog.getChangesBefore());
            auditLog.setChangesBefore(encryptionService.encrypt(beforeJson));
        }

        if (auditLog.getChangesAfter() != null && auditLog.getChangesAfter().length > 0) {
            String afterJson = sanitizeAndSerialize(auditLog.getChangesAfter());
            auditLog.setChangesAfter(encryptionService.encrypt(afterJson));
        }
    }

    private String sanitizeAndSerialize(byte[] rawBody) {
        try {
            String bodyStr = new String(rawBody);
            Object body = objectMapper.readValue(bodyStr, Object.class);
            if (body instanceof List) {
                return objectMapper.writeValueAsString(body);
            } else if (body instanceof Map) {
                Map<String, Object> bodyMap = (Map<String, Object>) body;
                Map<String, Object> sanitized = AuditoriaUtils.sanitizeMap(bodyMap);
                return objectMapper.writeValueAsString(sanitized);
            }
            return bodyStr;
        } catch (Exception e) {
            return new String(rawBody);
        }
    }

    private void sendToGcpLogging(AuditLog entry) {
        try {
            auditLogger.info("AUDIT | tenant={} | user={} | action={} | resource={}/{} | status={} | id={}",
                    entry.getTenantId(),
                    entry.getUserName(),
                    entry.getActionType(),
                    entry.getResourceType(),
                    entry.getResourceId(),
                    entry.getResponseStatus(),
                    entry.getId());
        } catch (Exception e) {
            log.warn("Failed to send log to GCP Logging: {}", e.getMessage());
        }
    }

    public record IntegrityCheckResult(UUID id, boolean valid, String message) {}

}
