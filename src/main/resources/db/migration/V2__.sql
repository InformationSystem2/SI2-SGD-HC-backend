CREATE TABLE document_templates
(
    id          UUID PRIMARY KEY      DEFAULT uuidv7(),
    tenant_id   uuid         NOT NULL,
    name        varchar(100) NOT NULL,
    description varchar(255),
    ui_schema   jsonb        NOT NULL,
    is_active   boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_document_templates_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE documents
(
    id                 UUID PRIMARY KEY              DEFAULT uuidv7(),
    tenant_id          uuid                 NOT NULL,
    patient_id         uuid                 NOT NULL,
    uploader_id        uuid                 NOT NULL,
    template_id        uuid                 NOT NULL,
    status             document_status_enum NOT NULL DEFAULT 'DRAFT',
    is_external_source boolean              NOT NULL DEFAULT false,
    clinical_content   jsonb,        -- Valores reales basados en ui_schema de la plantilla
    file_url           varchar(255), -- Path al PDF físico/impreso
    issue_date         date                 NOT NULL,
    expiry_date        date,
    created_at         timestamptz          NOT NULL DEFAULT now(),
    updated_at         timestamptz          NOT NULL DEFAULT now(),

    CONSTRAINT fk_documents_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT fk_documents_patient FOREIGN KEY (patient_id) REFERENCES patients (id) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT fk_documents_uploader FOREIGN KEY (uploader_id) REFERENCES users (id) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT fk_documents_template FOREIGN KEY (template_id) REFERENCES document_templates (id) DEFERRABLE INITIALLY IMMEDIATE
);


CREATE INDEX idx_documents_tenant        ON documents(tenant_id);
CREATE INDEX idx_documents_patient       ON documents(patient_id);
CREATE INDEX idx_documents_uploader      ON documents(uploader_id);
CREATE INDEX idx_documents_template      ON documents(template_id);
CREATE INDEX idx_documents_status        ON documents(status);