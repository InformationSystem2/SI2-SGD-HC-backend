CREATE TYPE document_type_enum AS ENUM ('CI', 'PASAPORTE');
CREATE TYPE subscription_plan_enum AS ENUM ('BASIC', 'PRO', 'ENTERPRISE');
CREATE TYPE subscription_status_enum AS ENUM ('ACTIVE', 'PAST_DUE', 'CANCELED');
CREATE TYPE payment_status_enum AS ENUM ('SUCCESS', 'PENDING', 'FAILED');
CREATE TYPE document_status_enum AS ENUM ('DRAFT', 'PENDING_SIGNATURE', 'COMPLETED');
CREATE TYPE alert_type_enum AS ENUM ('EXPIRING_SOON', 'EXPIRED');

CREATE TABLE tenants
(
    id                      UUID PRIMARY KEY      DEFAULT uuidv7(),
    name                    varchar(100) NOT NULL,
    slug                    varchar(50)  NOT NULL,
    email                   varchar(100) NOT NULL,
    phone                   varchar(20),
    address                 varchar(200),
    settings                jsonb,
    subscription_plan       subscription_plan_enum,
    subscription_status     subscription_status_enum,
    subscription_start_date date,
    created_at              timestamptz  NOT NULL DEFAULT now(),
    updated_at              timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_tenants_slug UNIQUE (slug)
);

COMMENT ON TABLE tenants IS 'Entidad raíz para la multitenencia; cada clínica es un inquilino.';
COMMENT ON COLUMN tenants.slug IS 'Identificador de subdominio, p. ej., clinic-south';
COMMENT ON COLUMN tenants.settings IS 'Configuración de la interfaz de usuario: logotipos, colores principales (JSONB)';
COMMENT ON COLUMN tenants.subscription_plan IS 'BÁSICO | PRO | EMPRESARIAL';
COMMENT ON COLUMN tenants.subscription_status IS 'ACTIVO | VENCIDO | CANCELADO';


CREATE TABLE roles
(
    id          UUID PRIMARY KEY     DEFAULT uuidv7(),
    tenant_id   uuid        NOT NULL,
    name        varchar(50) NOT NULL,
    description varchar(255),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE permissions
(
    id         UUID PRIMARY KEY     DEFAULT uuidv7(),
    name       varchar(50) NOT NULL,
    action     varchar(50) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_permissions_name UNIQUE (name),
    CONSTRAINT uq_permissions_action UNIQUE (action)
);

CREATE TABLE users
(
    id            UUID PRIMARY KEY            DEFAULT uuidv7(),
    tenant_id     uuid               NOT NULL,
    document_type document_type_enum NOT NULL,
    national_id   varchar(15)        NOT NULL,
    first_name    varchar(100)       NOT NULL,
    last_name     varchar(100)       NOT NULL,
    email         varchar(50)        NOT NULL,
    password      varchar(255)       NOT NULL,
    is_active     boolean            NOT NULL DEFAULT true,
    created_at    timestamptz        NOT NULL DEFAULT now(),
    updated_at    timestamptz        NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email_tenant UNIQUE (tenant_id, email),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        DEFERRABLE INITIALLY IMMEDIATE
);

COMMENT ON COLUMN users.document_type IS 'CI = Cédula de Identidad | PASAPORTE';

CREATE TABLE role_user
(
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,

    CONSTRAINT pk_role_user PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ru_user FOREIGN KEY (user_id) REFERENCES users (id) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT fk_ru_role FOREIGN KEY (role_id) REFERENCES roles (id) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE role_permissions
(
    role_id       uuid NOT NULL,
    permission_id uuid NOT NULL,

    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles (id) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE patients
(
    id                    UUID PRIMARY KEY      DEFAULT uuidv7(),
    tenant_id             uuid         NOT NULL,
    national_id           varchar(15)  NOT NULL,
    first_names           varchar(100) NOT NULL,
    last_names            varchar(100) NOT NULL,
    birth_date            date         NOT NULL,
    gender                varchar(20),
    address               varchar(200),
    phone                 varchar(20),
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_patients_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE INDEX idx_users_tenant            ON users(tenant_id);
CREATE INDEX idx_patients_tenant         ON patients(tenant_id);
