CREATE TABLE workflow_events (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID        NOT NULL,
    document_id    UUID        NOT NULL,
    review_task_id UUID,
    event_type     VARCHAR(50) NOT NULL,
    performed_by   UUID,
    performed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    details_json   JSONB,

    CONSTRAINT fk_workflow_events_tenant      FOREIGN KEY (tenant_id)      REFERENCES tenants(id)      ON DELETE CASCADE,
    CONSTRAINT fk_workflow_events_document    FOREIGN KEY (document_id)    REFERENCES documents(id)    ON DELETE CASCADE,
    CONSTRAINT fk_workflow_events_review_task FOREIGN KEY (review_task_id) REFERENCES review_tasks(id) ON DELETE SET NULL,
    CONSTRAINT fk_workflow_events_performer   FOREIGN KEY (performed_by)   REFERENCES users(id)        ON DELETE SET NULL
);

CREATE INDEX idx_workflow_events_document ON workflow_events(document_id, performed_at);
