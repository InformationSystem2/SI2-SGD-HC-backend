-- =============================================================================
-- V28 — Agregar workflow_id a workflow_comments y hacer document_id opcional
-- =============================================================================

ALTER TABLE workflow_comments ADD COLUMN workflow_id UUID;
ALTER TABLE workflow_comments ALTER COLUMN document_id DROP NOT NULL;

ALTER TABLE workflow_comments
    ADD CONSTRAINT fk_workflow_comments_workflow
    FOREIGN KEY (workflow_id) REFERENCES workflows(id) ON DELETE CASCADE;

CREATE INDEX idx_workflow_comments_workflow ON workflow_comments(workflow_id, created_at);
