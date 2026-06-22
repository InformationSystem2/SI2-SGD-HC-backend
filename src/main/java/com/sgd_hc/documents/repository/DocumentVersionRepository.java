package com.sgd_hc.documents.repository;

import com.sgd_hc.documents.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio del historial inmutable de versiones de documentos.
 */
@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findByDocumentIdAndTenantIdOrderByVersionNumberDesc(
            UUID documentId, UUID tenantId);

    Optional<DocumentVersion> findFirstByDocumentIdAndTenantIdOrderByVersionNumberDesc(
            UUID documentId, UUID tenantId);

    long countByDocumentIdAndTenantId(UUID documentId, UUID tenantId);

    long countByDocumentId(UUID documentId);

    @Query("""
        SELECT COUNT(dv) FROM DocumentVersion dv
        WHERE dv.tenantId = :tenantId
        AND dv.createdAt >= :since
    """)
    long countByTenantIdSince(@Param("tenantId") UUID tenantId, @Param("since") OffsetDateTime since);
}
