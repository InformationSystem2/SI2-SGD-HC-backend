-- =============================================================================
-- V14 — HISTORIAL DE VERSIONES INMUTABLES
-- =============================================================================

CREATE TABLE document_versions
(
    id               UUID                  PRIMARY KEY DEFAULT uuidv7(),
    tenant_id        UUID                  NOT NULL,
    document_id      UUID                  NOT NULL,
    version_number   INTEGER               NOT NULL,
    author_id        UUID                  NOT NULL,
    clinical_content JSONB,
    status           document_status_enum  NOT NULL,
    change_reason    VARCHAR(1000)         NOT NULL,
    file_url         VARCHAR(512),
    created_at       TIMESTAMPTZ           NOT NULL DEFAULT now(),

    CONSTRAINT fk_document_versions_document
        FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,

    CONSTRAINT uk_document_versions_doc_version
        UNIQUE (document_id, version_number)
);

CREATE INDEX idx_document_versions_document ON document_versions(document_id);
CREATE INDEX idx_document_versions_tenant   ON document_versions(tenant_id);

-- ── Guardas de inmutabilidad: prohibir UPDATE y DELETE sobre document_versions
CREATE OR REPLACE FUNCTION document_versions_block_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'document_versions es append-only: no se permite % sobre filas existentes', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_document_versions_no_update
    BEFORE UPDATE ON document_versions
    FOR EACH ROW EXECUTE FUNCTION document_versions_block_mutation();

CREATE OR REPLACE TRIGGER trg_document_versions_no_delete
    BEFORE DELETE ON document_versions
    FOR EACH ROW EXECUTE FUNCTION document_versions_block_mutation();
