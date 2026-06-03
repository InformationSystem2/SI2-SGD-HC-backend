-- =============================================================================
-- V10 — REFACTORIZACIÓN DE ROLES Y PERMISOS A ID SERIAL (INTEGER)
-- =============================================================================

-- 1. Drop existing tables that depend on UUID roles/permissions
DROP TABLE IF EXISTS role_user CASCADE;
DROP TABLE IF EXISTS role_permission CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS permissions CASCADE;

-- 2. Recreate permissions with serial ID
CREATE TABLE permissions
(
    id          SERIAL PRIMARY KEY,
    name        varchar(50)  NOT NULL,
    module      varchar(50)  NOT NULL DEFAULT 'SYSTEM',
    action      varchar(50)  NOT NULL,
    description varchar(255),
    is_active   boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_permissions_name UNIQUE (name)
);

-- 3. Recreate roles with serial ID
CREATE TABLE roles
(
    id          SERIAL PRIMARY KEY,
    tenant_id   uuid        NOT NULL,
    name        varchar(50) NOT NULL,
    description varchar(255),
    is_active   boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_roles_tenant       FOREIGN KEY (tenant_id) REFERENCES tenants (id) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT uq_roles_name_tenant  UNIQUE (tenant_id, name)
);

-- 4. Recreate junction tables with integer references
CREATE TABLE role_permission
(
    role_id       int NOT NULL,
    permission_id int NOT NULL,

    CONSTRAINT pk_role_permission   PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role           FOREIGN KEY (role_id)       REFERENCES roles (id)       ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT fk_rp_permission     FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE role_user
(
    user_id uuid NOT NULL,
    role_id int NOT NULL,

    CONSTRAINT pk_role_user PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ru_user   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT fk_ru_role   FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE
);
