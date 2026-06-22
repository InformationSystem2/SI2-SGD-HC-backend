package com.sgd_hc.workflow.entity;

import com.sgd_hc.documents.entity.Document;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workflow_documents",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_workflow_document",
                columnNames = {"workflow_id", "document_id"}))
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDocument {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_wf_docs_workflow"))
    private Workflow workflow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_wf_docs_document"))
    private Document document;

    @Column(name = "added_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime addedAt = OffsetDateTime.now();
}
