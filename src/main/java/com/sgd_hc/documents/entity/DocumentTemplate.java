package com.sgd_hc.documents.entity;

import com.sgd_hc.users.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Plantilla de documento clínico configurable por tenant.
 *
 * <p>{@code tenant_id} heredado de {@link BaseEntity}.
 *
 * <p>{@code uiSchema} define la estructura de campos del formulario:
 * <pre>{ "symptoms": "text", "blood_pressure": "number" }</pre>
 * Los valores reales se guardan en {@link Document#getClinicalContent()}.
 */
@Entity
@Table(name = "document_templates")
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTemplate extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui_schema", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> uiSchema;
}
