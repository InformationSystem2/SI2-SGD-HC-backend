package com.sgd_hc.documents.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Historial inmutable (append-only) de un {@link Document}.
 */
@Entity
@Table(
        name = "document_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_document_versions_doc_version",
                columnNames = {"document_id", "version_number"}
        ),
        indexes = {
                @Index(name = "idx_document_versions_document", columnList = "document_id"),
                @Index(name = "idx_document_versions_tenant",   columnList = "tenant_id")
        }
)
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = UUID.class),
        defaultCondition = "tenant_id = :tenantId"
)
@Filter(name = "tenantFilter")
public class DocumentVersion {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "document_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_document_versions_document"))
    private Document document;

    @Column(name = "version_number", nullable = false, updatable = false)
    private Integer versionNumber;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clinical_content", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> clinicalContent;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, updatable = false,
            columnDefinition = "document_status_enum")
    private DocumentStatus status;

    @Column(name = "change_reason", nullable = false, updatable = false, length = 1000)
    private String changeReason;

    @Column(name = "file_url", updatable = false, length = 512)
    private String fileUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
