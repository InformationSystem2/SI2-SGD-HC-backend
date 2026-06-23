-- =============================================================================
-- V30 — HISTORIAS CLÍNICAS: Crear tabla clinical_histories y relacionar documentos
-- =============================================================================

CREATE TABLE clinical_histories
(
    id                         UUID PRIMARY KEY              DEFAULT uuidv7(),
    tenant_id                  uuid                 NOT NULL,
    patient_id                 uuid                 NOT NULL UNIQUE,
    code                       varchar(50)          NOT NULL UNIQUE,
    blood_type                 varchar(10),
    pathological_antecedents   text,
    non_pathological_antecedents text,
    family_antecedents         text,
    allergies                  text,
    observations               text,
    created_at                 timestamptz          NOT NULL DEFAULT now(),
    updated_at                 timestamptz          NOT NULL DEFAULT now(),

    CONSTRAINT fk_clinical_histories_tenant  FOREIGN KEY (tenant_id)  REFERENCES tenants (id)             DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT fk_clinical_histories_patient FOREIGN KEY (patient_id) REFERENCES patients (id)            DEFERRABLE INITIALLY IMMEDIATE
);

CREATE INDEX idx_clinical_histories_tenant  ON clinical_histories(tenant_id);
CREATE INDEX idx_clinical_histories_patient ON clinical_histories(patient_id);
CREATE INDEX idx_clinical_histories_code    ON clinical_histories(code);

-- Relacionar documentos con historias clínicas
ALTER TABLE documents ADD COLUMN clinical_history_id UUID;
ALTER TABLE documents ADD CONSTRAINT fk_documents_clinical_history FOREIGN KEY (clinical_history_id) REFERENCES clinical_histories (id) DEFERRABLE INITIALLY IMMEDIATE;
CREATE INDEX idx_documents_clinical_history ON documents(clinical_history_id);
