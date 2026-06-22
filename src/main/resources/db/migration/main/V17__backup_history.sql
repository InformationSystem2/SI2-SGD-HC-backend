-- V17: Backup history tracking per tenant per year
CREATE TABLE backup_history
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    backup_type    VARCHAR(20)  NOT NULL DEFAULT 'tenant',   -- 'tenant' or 'full'
    created_by     UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_backup_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_backup_history_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_backup_history_tenant_year ON backup_history (tenant_id, created_at);
