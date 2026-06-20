package com.sgd_hc.workflow.entity;

import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.users.entity.User;
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

@Entity
@Table(name = "workflow_events",
        indexes = @Index(name = "idx_workflow_events_document", columnList = "document_id, performed_at"))
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = UUID.class),
        defaultCondition = "tenant_id = :tenantId"
)
@Filter(name = "tenantFilter")
public class WorkflowEvent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_workflow_events_document"))
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_task_id",
            foreignKey = @ForeignKey(name = "fk_workflow_events_review_task"))
    private ReviewTask reviewTask;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private WorkflowEventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by",
            foreignKey = @ForeignKey(name = "fk_workflow_events_performer"))
    private User performedBy;

    @Column(name = "performed_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime performedAt = OffsetDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> detailsJson;
}
