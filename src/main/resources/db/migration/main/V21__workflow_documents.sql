-- =============================================================================
-- V25 — WORKFLOW_DOCUMENTS: documentos dentro de un workflow (M:N)
-- =============================================================================

CREATE TABLE workflow_documents (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id  UUID        NOT NULL,
    document_id  UUID        NOT NULL,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_wf_docs_workflow FOREIGN KEY (workflow_id) REFERENCES workflows(id) ON DELETE CASCADE,
    CONSTRAINT fk_wf_docs_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT uq_workflow_document UNIQUE (workflow_id, document_id)
);

CREATE INDEX idx_wf_docs_workflow ON workflow_documents(workflow_id);
CREATE INDEX idx_wf_docs_document ON workflow_documents(document_id);
