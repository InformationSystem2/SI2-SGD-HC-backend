-- =============================================================================
-- V31 — TASK_DELEGATIONS: delegación de tareas a otro usuario
-- =============================================================================

CREATE TABLE task_delegations (
    id           UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID    NOT NULL,
    delegator_id UUID    NOT NULL,
    delegate_id  UUID    NOT NULL,
    start_date   DATE    NOT NULL,
    end_date     DATE,
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_task_delegations_tenant    FOREIGN KEY (tenant_id)    REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_delegations_delegator FOREIGN KEY (delegator_id) REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_task_delegations_delegate  FOREIGN KEY (delegate_id)  REFERENCES users(id)   ON DELETE CASCADE
);

CREATE INDEX idx_task_delegations_delegator ON task_delegations(delegator_id, is_active);
CREATE INDEX idx_task_delegations_delegate  ON task_delegations(delegate_id, is_active);
