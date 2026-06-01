CREATE TABLE role_attribute_permissions (
    id UUID PRIMARY KEY,
    role_id UUID NOT NULL,
    entity_name VARCHAR(100) NOT NULL,
    attribute_name VARCHAR(100) NOT NULL,
    access_level VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_role_attr_perm_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT uq_role_entity_attr UNIQUE (role_id, entity_name, attribute_name)
);
