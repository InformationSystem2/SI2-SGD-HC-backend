//src/main/java/com/sgd_hc/documents/repository/DocumentRepository.java

package com.sgd_hc.documents.repository;

import com.sgd_hc.documents.entity.Document;
import com.sgd_hc.documents.entity.DocumentCategory;
import com.sgd_hc.documents.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByPatientIdAndTenantId(UUID patientId, UUID tenantId);

    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Document> findByTenantIdAndStatus(UUID tenantId, DocumentStatus status);

    /**
     * Busca documentos de un tenant filtrando por una clave y valor dentro
     * del JSONB {@code clinical_content} usando el operador {@code ->>'key'}.
     *
     * <p>
     * Ejemplo: buscar todos los documentos donde
     * {@code clinical_content->>'diagnostico' = 'Hipertensión'}.
     *
     * @param tenantId  Identificador del tenant (aislamiento SaaS).
     * @param jsonKey   Nombre del campo dentro del JSON clínico.
     * @param jsonValue Valor a buscar para esa clave.
     * @return Lista de documentos que coinciden.
     */
    @Query(value = """
            SELECT d.*
            FROM documents d
            WHERE d.tenant_id = :tenantId
              AND d.clinical_content ->> :jsonKey = :jsonValue
            """, nativeQuery = true)
    List<Document> findByTenantIdAndClinicalContentField(
            @Param("tenantId") UUID tenantId,
            @Param("jsonKey") String jsonKey,
            @Param("jsonValue") String jsonValue);

    /**
     * Busca documentos clínicos (historiales) aplicando filtros opcionales.
     * <p>
     * La consulta combina las tablas {@code documents} y {@code patients},
     * permitiendo
     * búsqueda por nombre del paciente, número de documento, estado del documento
     * y rango de fechas de emisión. Los filtros son todos opcionales.
     * </p>
     * <p>
     * El tenant se aplica automáticamente mediante {@code TenantFilterAspect},
     * por lo que no se incluye explícitamente en la consulta JPQL.
     * </p>
     *
     * @param nombre     Nombre parcial del paciente (concatenación de firstName +
     *                   lastName).
     *                   No sensible a mayúsculas/minúsculas.
     * @param nroDoc     Número de documento del paciente (búsqueda exacta).
     * @param estado     Estado del documento (DRAFT, PENDING_SIGNATURE, COMPLETED).
     *                   Opcional.
     * @param fechaDesde Fecha mínima de emisión del documento (inclusive).
     *                   Opcional.
     * @param fechaHasta Fecha máxima de emisión del documento (inclusive).
     *                   Opcional.
     * @param pageable   Objeto de paginación y ordenamiento.
     * @return Página de resultados con los documentos encontrados,
     *         incluyendo los datos del paciente y la plantilla (fetch join).
     */

    @Query(value = """
            SELECT d.*
            FROM documents d
            JOIN patients p ON d.patient_id = p.id
            LEFT JOIN document_templates t ON d.template_id = t.id
            WHERE
                (:nombre IS NULL OR
                    LOWER(p.first_name::text || ' ' || p.last_name::text) LIKE LOWER(CONCAT('%', :nombre, '%')))
                AND (:nroDoc IS NULL OR p.document_number = :nroDoc)
                AND (:estado IS NULL OR d.status = CAST(:estado AS document_status_enum))
                AND (:fechaDesde IS NULL OR d.issue_date >= CAST(:fechaDesde AS date))
                AND (:fechaHasta IS NULL OR d.issue_date <= CAST(:fechaHasta AS date))
            """, nativeQuery = true)
    Page<Document> searchHistoriales(
            @Param("nombre") String nombre,
            @Param("nroDoc") String nroDoc,
            @Param("estado") String estado,
            @Param("fechaDesde") String fechaDesde,
            @Param("fechaHasta") String fechaHasta,
            Pageable pageable);

    @Modifying
    @Query(value = "DELETE FROM documents WHERE tenant_id = :tenantId", nativeQuery = true)
    void deleteAllByTenantId(@Param("tenantId") UUID tenantId);            
}
