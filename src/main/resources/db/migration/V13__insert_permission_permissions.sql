-- =============================================================================
-- V12 — INSERTAR PERMISOS PARA EL MÓDULO DE PERMISOS
-- =============================================================================

INSERT INTO permissions (name, module, action, description) VALUES
-- Permission
('permission:read', 'permission', 'read', 'Permiso para permission:read'),
('permission:read:id', 'permission', 'read', 'Permiso para permission:read:id'),
('permission:read:name', 'permission', 'read', 'Permiso para permission:read:name'),
('permission:read:module', 'permission', 'read', 'Permiso para permission:read:module'),
('permission:read:action', 'permission', 'read', 'Permiso para permission:read:action'),
('permission:read:description', 'permission', 'read', 'Permiso para permission:read:description'),
('permission:create', 'permission', 'create', 'Permiso para permission:create'),
('permission:create:module', 'permission', 'create', 'Permiso para permission:create:module'),
('permission:create:action', 'permission', 'create', 'Permiso para permission:create:action'),
('permission:create:description', 'permission', 'create', 'Permiso para permission:create:description'),
('permission:update', 'permission', 'update', 'Permiso para permission:update'),
('permission:update:name', 'permission', 'update', 'Permiso para permission:update:name'),
('permission:update:module', 'permission', 'update', 'Permiso para permission:update:module'),
('permission:update:action', 'permission', 'update', 'Permiso para permission:update:action'),
('permission:update:description', 'permission', 'update', 'Permiso para permission:update:description'),
('permission:delete', 'permission', 'delete', 'Permiso para permission:delete')
ON CONFLICT (name) DO NOTHING;
