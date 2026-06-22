-- =============================================================================
-- V30 — USER_PUSH_TOKENS: tokens de dispositivos para notificaciones push
-- =============================================================================

CREATE TABLE user_push_tokens (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    platform     VARCHAR(20) NOT NULL,
    token        TEXT        NOT NULL,
    is_active    BOOLEAN     NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,

    CONSTRAINT fk_push_tokens_user  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_push_tokens_token UNIQUE (token)
);

CREATE INDEX idx_push_tokens_user ON user_push_tokens(user_id, is_active);
