CREATE TABLE notifications (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    channel        VARCHAR(20)  NOT NULL DEFAULT 'IN_APP',
    type           VARCHAR(50)  NOT NULL,
    title          VARCHAR(200),
    message        TEXT,
    document_id    UUID,
    review_task_id UUID,
    is_read        BOOLEAN      NOT NULL DEFAULT false,
    read_at        TIMESTAMPTZ,
    archived_at    TIMESTAMPTZ,
    expires_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_notifications_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE
);

CREATE INDEX idx_notifications_user ON notifications(user_id, is_read, created_at DESC);
