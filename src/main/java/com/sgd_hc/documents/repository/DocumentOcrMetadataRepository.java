package com.sgd_hc.documents.repository;

import com.sgd_hc.documents.entity.DocumentOcrMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface DocumentOcrMetadataRepository 
        extends JpaRepository<DocumentOcrMetadata, UUID> {

    Optional<DocumentOcrMetadata> findByDocumentId(UUID documentId);
    boolean existsByDocumentId(UUID documentId);

    @Query(value = "SELECT COALESCE(SUM(ocr.pages_processed), 0) FROM document_ocr_metadata ocr "
            + "JOIN documents d ON ocr.document_id = d.id "
            + "WHERE d.tenant_id = :tenantId "
            + "AND ocr.created_at >= date_trunc('month', CURRENT_DATE)", nativeQuery = true)
    long sumPagesProcessedByTenantIdCurrentMonth(UUID tenantId);
}