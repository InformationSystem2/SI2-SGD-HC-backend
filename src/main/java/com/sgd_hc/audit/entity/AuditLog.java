package com.sgd_hc.audit.entity;

import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.entity.enums.SeverityLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onPrePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLog other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "user_name", length = 255)
    private String userName;

    @Column(name = "action_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    @Column(name = "resource_type", nullable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "resource_name", length = 255)
    private String resourceName;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    @Column(name = "request_body", columnDefinition = "BYTEA")
    private byte[] requestBody;

    @Column(name = "changes_before", columnDefinition = "BYTEA")
    private byte[] changesBefore;

    @Column(name = "changes_after", columnDefinition = "BYTEA")
    private byte[] changesAfter;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "integrity_hash", length = 64)
    private String integrityHash;

    @Column(name = "client_time")
    private OffsetDateTime clientTime;

    @Column(name = "session_id")
    private UUID sessionId;


    @Column(name = "severity", length = 50)
    @Enumerated(EnumType.STRING)
    private SeverityLevel severity = SeverityLevel.INFO;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;
}
