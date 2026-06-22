-- =============================================================================
-- V28 — WORKFLOW_COMMENTS: comentarios en el hilo de revisión
-- =============================================================================

CREATE TABLE workflow_comments (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID        NOT NULL,
    document_id    UUID        NOT NULL,
    review_task_id UUID,
    author_id      UUID        NOT NULL,
    comment_text   TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_workflow_comments_tenant      FOREIGN KEY (tenant_id)      REFERENCES tenants(id)      ON DELETE CASCADE,
    CONSTRAINT fk_workflow_comments_document    FOREIGN KEY (document_id)    REFERENCES documents(id)    ON DELETE CASCADE,
    CONSTRAINT fk_workflow_comments_review_task FOREIGN KEY (review_task_id) REFERENCES review_tasks(id) ON DELETE SET NULL,
    CONSTRAINT fk_workflow_comments_author      FOREIGN KEY (author_id)      REFERENCES users(id)        ON DELETE CASCADE
);

CREATE INDEX idx_workflow_comments_document ON workflow_comments(document_id, created_at);
