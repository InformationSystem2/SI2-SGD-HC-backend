package com.sgd_hc.documents.repository;

import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByPatientIdAndTenantId(UUID patientId, UUID tenantId);

    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Document> findByTenantIdAndStatus(UUID tenantId, DocumentStatus status);

    /**
     * Busca documentos de un tenant filtrando por una clave y valor dentro
     * del JSONB {@code clinical_content} usando el operador {@code ->>'key'}.
     *
     * <p>Ejemplo: buscar todos los documentos donde
     * {@code clinical_content->>'diagnostico' = 'Hipertensión'}.
     *
     * @param tenantId  Identificador del tenant (aislamiento SaaS).
     * @param jsonKey   Nombre del campo dentro del JSON clínico.
     * @param jsonValue Valor a buscar para esa clave.
     * @return Lista de documentos que coinciden.
     */
    @Query(
        value = """
                SELECT d.*
                FROM documents d
                WHERE d.tenant_id = :tenantId
                  AND d.clinical_content ->> :jsonKey = :jsonValue
                """,
        nativeQuery = true
    )
    List<Document> findByTenantIdAndClinicalContentField(
            @Param("tenantId") UUID tenantId,
            @Param("jsonKey")  String jsonKey,
            @Param("jsonValue") String jsonValue
    );
}
