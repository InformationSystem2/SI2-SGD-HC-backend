package com.sgd_hc.documents.repository;

import com.sgd_hc.documents.entity.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {

    /**
     * Recupera una lista de todas las plantillas de documentos que están actualmente activas
     * para una clínica (tenant) específica.
     *
     * @param tenantId El identificador único de la clínica (Tenant) sobre la cual se realiza la búsqueda.
     * @return Una lista de {@link DocumentTemplate} activas asociadas al tenant.
     * Retorna una lista vacía si no se encuentran plantillas, nunca retorna null.
     */
    List<DocumentTemplate> findByTenantIdAndIsActiveTrue(UUID tenantId);

    /**
     * Busca una plantilla de documento específica por su identificador, garantizando
     * a nivel de base de datos que pertenezca a la clínica (tenant) solicitante.
     * Esto actúa como un filtro de seguridad para evitar fuga de datos entre clínicas.
     *
     * @param id El identificador único de la plantilla de documento que se desea consultar.
     * @param tenantId El identificador único de la clínica (Tenant) a la que debe pertenecer la plantilla.
     * @return Un {@link Optional} que contiene la plantilla {@link DocumentTemplate} si se encuentra,
     * o un {@link Optional#empty()} si la plantilla no existe o pertenece a otra clínica.
     */
    Optional<DocumentTemplate> findByIdAndTenantId(UUID id, UUID tenantId);
}
