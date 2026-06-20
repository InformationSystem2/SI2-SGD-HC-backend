-- =============================================================================
-- V30 — PERMISOS DEL SISTEMA DE WORKFLOW Y NOTIFICACIONES
-- =============================================================================

INSERT INTO permissions (name, module, action, description) VALUES

-- ── Review Tasks ─────────────────────────────────────────────────────────────
('review-task:create',  'review-task', 'create', 'Iniciar revisión de un documento'),
('review-task:read',    'review-task', 'read',   'Ver tareas de revisión propias y de documentos'),
('review-task:update',  'review-task', 'update', 'Reclamar o completar una tarea de revisión'),

-- ── Workflow (historial y comentarios) ───────────────────────────────────────
('workflow:read',            'workflow', 'read',   'Ver historial de eventos del ciclo de vida'),
('workflow:comment:create',  'workflow', 'create', 'Agregar comentarios al hilo de revisión'),

-- ── Notifications ─────────────────────────────────────────────────────────────
('notification:read',         'notification', 'read',   'Ver notificaciones propias'),
('notification:update',       'notification', 'update', 'Marcar notificaciones como leídas'),
('notification:push:create',  'notification', 'create', 'Registrar token push para notificaciones móviles'),
('notification:push:delete',  'notification', 'delete', 'Eliminar token push');
