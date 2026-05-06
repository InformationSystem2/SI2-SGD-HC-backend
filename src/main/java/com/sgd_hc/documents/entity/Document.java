package com.sgd_hc.documents.entity;

import com.sgd_hc.patients.entity.Patient;
import com.sgd_hc.users.entity.BaseEntity;
import com.sgd_hc.users.entity.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Documento clínico del sistema.
 *
 * <p>{@code tenant_id} heredado de {@link BaseEntity} — no se redeclara.
 *
 * <p>Ciclo de vida: DRAFT → PENDING_SIGNATURE → COMPLETED.
 */
@Entity(name = "documents")
@Table(name = "documents")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Document extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "patient_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_documents_patient"))
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_documents_uploader"))
    private User uploader;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_documents_template"))
    private DocumentTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30,
            columnDefinition = "document_status_enum")
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Column(name = "is_external_source", nullable = false)
    @Builder.Default
    private Boolean isExternalSource = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clinical_content", columnDefinition = "jsonb")
    private Map<String, Object> clinicalContent;

    private String fileUrl;

    @Column(nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;
}
