-- =============================================================================
-- V1 -- SISTEMA DE AUDITORÍA: Tabla audit_log
-- =============================================================================

CREATE TABLE audit_log
(
    id                UUID PRIMARY KEY     DEFAULT uuidv7(),
    tenant_id         uuid,
    user_id           uuid,
    user_email        varchar(255),
    user_name         varchar(255),
    action_type       varchar(50)  NOT NULL,
    resource_type     varchar(100) NOT NULL,
    resource_id       varchar(100),
    resource_name     varchar(255),
    ip_address        varchar(45),
    user_agent        text,
    request_method    varchar(10),
    request_path      varchar(500),
    request_body      bytea,
    changes_before    bytea,
    changes_after     bytea,
    response_status   integer,
    error_message     text,
    integrity_hash    varchar(64),
    client_time       timestamptz,
    session_id        uuid,
    severity          varchar(50)  DEFAULT 'INFO',
    execution_time_ms bigint,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  audit_log                 IS 'Bitácora de auditoría forense con cifrado AES-256 y verificación HMAC. Acceso exclusivo para SUPERUSER.';
COMMENT ON COLUMN audit_log.request_body    IS 'Cifrado AES-256-GCM: body del request sanitizado (passwords enmascaradas)';
COMMENT ON COLUMN audit_log.changes_before  IS 'Cifrado AES-256-GCM: estado anterior del recurso (JSON)';
COMMENT ON COLUMN audit_log.changes_after   IS 'Cifrado AES-256-GCM: estado nuevo del recurso (JSON)';
COMMENT ON COLUMN audit_log.integrity_hash  IS 'HMAC-SHA256: hash de integridad para detectar manipulación de logs';
COMMENT ON COLUMN audit_log.client_time     IS 'Fecha y hora capturada desde el dispositivo cliente';
COMMENT ON COLUMN audit_log.session_id      IS 'Identificador de sesión o correlación de la transacción';
COMMENT ON COLUMN audit_log.severity        IS 'Severidad de la acción: INFO, WARNING, ERROR, CRITICAL';
COMMENT ON COLUMN audit_log.execution_time_ms IS 'Tiempo de ejecución de la acción en milisegundos';

-- Índices para consultas frecuentes
CREATE INDEX idx_audit_tenant_timestamp ON audit_log(tenant_id, created_at DESC);
CREATE INDEX idx_audit_user_timestamp   ON audit_log(user_id, created_at DESC);
CREATE INDEX idx_audit_resource         ON audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_action_type      ON audit_log(action_type);
CREATE INDEX idx_audit_ip               ON audit_log(ip_address);
CREATE INDEX idx_audit_created_at       ON audit_log(created_at DESC);
CREATE UNIQUE INDEX idx_audit_hash      ON audit_log(integrity_hash);
