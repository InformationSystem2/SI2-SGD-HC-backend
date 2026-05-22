package com.sgd_hc.documents.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "document_ocr_metadata")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentOcrMetadata {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_estructurados", columnDefinition = "jsonb")
    private Map<String, Object> datosEstructurados;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "pages_processed")
    private Integer pagesProcessed;

    @Column(name = "file_type", length = 20)
    private String fileType;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}