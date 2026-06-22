package com.sgd_hc.workflow.entity;

import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.users.entity.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workflow_comments")
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
public class WorkflowComment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_workflow_comments_document"))
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_task_id",
            foreignKey = @ForeignKey(name = "fk_workflow_comments_review_task"))
    private ReviewTask reviewTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id",
            foreignKey = @ForeignKey(name = "fk_workflow_comments_workflow"))
    private Workflow workflow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_workflow_comments_author"))
    private User author;

    @Column(name = "comment_text", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String commentText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
