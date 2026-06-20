package com.sgd_hc.audit.mapper;

import com.sgd_hc.audit.dto.AuditLogResponseDto;
import com.sgd_hc.audit.entity.AuditLog;
import com.sgd_hc.audit.service.AuditEncryptionService;
import com.sgd_hc.audit.service.AuditHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditLogMapper {

    private final AuditEncryptionService encryptionService;
    private final AuditHashService hashService;

    public AuditLogResponseDto toResponseDto(AuditLog entry) {
        return new AuditLogResponseDto(
                entry.getId(),
                entry.getTenantId(),
                entry.getUserId(),
                entry.getUserEmail(),
                entry.getUserName(),
                entry.getActionType(),
                entry.getResourceType(),
                entry.getResourceId(),
                entry.getResourceName(),
                entry.getIpAddress(),
                entry.getUserAgent(),
                entry.getRequestMethod(),
                entry.getRequestPath(),
                entry.getRequestBody() != null ? encryptionService.decryptMap(entry.getRequestBody()) : null,
                entry.getChangesBefore() != null ? encryptionService.decryptMap(entry.getChangesBefore()) : null,
                entry.getChangesAfter() != null ? encryptionService.decryptMap(entry.getChangesAfter()) : null,
                entry.getResponseStatus(),
                entry.getErrorMessage(),
                entry.getIntegrityHash(),
                entry.getCreatedAt(),
                entry.getClientTime(),
                entry.getSessionId(),
                entry.getSeverity() != null ? entry.getSeverity().name() : null,
                entry.getExecutionTimeMs(),
                hashService.verifyHash(entry)
        );
    }
}
