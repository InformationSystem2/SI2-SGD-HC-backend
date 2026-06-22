-- =============================================================================
-- V26 — REVIEW_TASKS: tareas individuales dentro de un workflow
-- =============================================================================

CREATE TABLE review_tasks (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL,
    workflow_id  UUID,
    document_id  UUID        NOT NULL,
    assigned_to  UUID        NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    outcome      VARCHAR(20),
    priority     INTEGER     NOT NULL DEFAULT 3,
    due_date     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    completed_by UUID,

    CONSTRAINT fk_review_tasks_tenant   FOREIGN KEY (tenant_id)    REFERENCES tenants(id)    ON DELETE CASCADE,
    CONSTRAINT fk_review_tasks_workflow FOREIGN KEY (workflow_id)  REFERENCES workflows(id)   ON DELETE SET NULL,
    CONSTRAINT fk_review_tasks_document FOREIGN KEY (document_id)  REFERENCES documents(id)   ON DELETE CASCADE,
    CONSTRAINT fk_review_tasks_assigned FOREIGN KEY (assigned_to)  REFERENCES users(id)       ON DELETE CASCADE,
    CONSTRAINT fk_review_tasks_completed FOREIGN KEY (completed_by) REFERENCES users(id)      ON DELETE SET NULL
);

CREATE INDEX idx_review_tasks_assigned  ON review_tasks(assigned_to, status);
CREATE INDEX idx_review_tasks_document  ON review_tasks(document_id, status);
CREATE INDEX idx_review_tasks_tenant    ON review_tasks(tenant_id, status);
CREATE INDEX idx_review_tasks_workflow  ON review_tasks(workflow_id, status);
