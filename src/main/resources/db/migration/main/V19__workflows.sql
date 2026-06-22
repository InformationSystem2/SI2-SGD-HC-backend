-- =============================================================================
-- V24 — WORKFLOWS: contenedor principal del flujo de trabajo
-- =============================================================================

CREATE TABLE workflows (
    id                        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                 UUID        NOT NULL,
    title                     VARCHAR(255) NOT NULL,
    message                   TEXT,
    creator_id                UUID        NOT NULL,
    assignee_id               UUID        NOT NULL,
    status                    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    priority                  INTEGER     NOT NULL DEFAULT 3,
    due_date                  TIMESTAMPTZ,
    send_email_notifications  BOOLEAN     NOT NULL DEFAULT false,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_workflows_tenant   FOREIGN KEY (tenant_id)  REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_workflows_creator  FOREIGN KEY (creator_id) REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_workflows_assignee FOREIGN KEY (assignee_id) REFERENCES users(id)  ON DELETE CASCADE
);

CREATE INDEX idx_workflows_tenant   ON workflows(tenant_id, status);
CREATE INDEX idx_workflows_assignee ON workflows(assignee_id, status);
CREATE INDEX idx_workflows_creator  ON workflows(creator_id, status);
