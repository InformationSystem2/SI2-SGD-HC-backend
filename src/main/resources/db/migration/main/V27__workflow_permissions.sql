-- =============================================================================
-- PERMISOS DEL SISTEMA DE WORKFLOW Y NOTIFICACIONES
-- =============================================================================

INSERT INTO permissions (name, module, action, description) VALUES

-- ── Workflows ────────────────────────────────────────────────────────────────
('workflow:create',        'workflow', 'create', 'Iniciar un flujo de trabajo'),
('workflow:read',          'workflow', 'read',   'Ver flujos de trabajo propios y asignados'),
('workflow:update',        'workflow', 'update', 'Actualizar estado de un flujo de trabajo'),
('workflow:cancel',        'workflow', 'cancel', 'Cancelar un flujo de trabajo'),

-- ── Review Tasks (heredados) ─────────────────────────────────────────────────
('review-task:create',     'review-task', 'create', 'Iniciar revisión de un documento'),
('review-task:read',       'review-task', 'read',   'Ver tareas de revisión propias'),
('review-task:update',     'review-task', 'update', 'Reclamar o completar una tarea de revisión'),

-- ── Workflow historial y comentarios ─────────────────────────────────────────
('workflow:comment:create',     'workflow', 'create', 'Agregar comentarios al hilo de revisión'),

-- ── Notifications ─────────────────────────────────────────────────────────────
('notification:read',           'notification', 'read',   'Ver notificaciones propias'),
('notification:update',         'notification', 'update', 'Marcar notificaciones como leídas'),
('notification:push:create',    'notification', 'create', 'Registrar token push para notificaciones móviles'),
('notification:push:delete',    'notification', 'delete', 'Eliminar token push')
ON CONFLICT (name) DO NOTHING;
