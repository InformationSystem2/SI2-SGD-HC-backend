-- =============================================================================
-- V15 — ESTRUCTURA DE PLANES: Planes, Límites, Features, Billing Cycle
-- =============================================================================

-- ── Tabla principal de planes ─────────────────────────────────────────────────

CREATE TABLE plans
(
    id            UUID PRIMARY KEY      DEFAULT uuidv7(),
    name          VARCHAR(50)  NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    description   TEXT,
    price_monthly DECIMAL(10,2) NOT NULL DEFAULT 0,
    price_yearly  DECIMAL(10,2) NOT NULL DEFAULT 0,
    cycle_days    INT          NOT NULL DEFAULT 30,
    grace_period_days INT      NOT NULL DEFAULT 3,
    sort_order    INT          NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_plans_name UNIQUE (name)
);

COMMENT ON TABLE  plans                       IS 'Planes de suscripción disponibles con precios y ciclo de facturación.';
COMMENT ON COLUMN plans.name                  IS 'Identificador interno: BASIC, PRO, ENTERPRISE';
COMMENT ON COLUMN plans.display_name          IS 'Nombre mostrado en UI';
COMMENT ON COLUMN plans.price_monthly         IS 'Precio mensual en USD';
COMMENT ON COLUMN plans.price_yearly          IS 'Precio anual en USD (con descuento)';
COMMENT ON COLUMN plans.cycle_days            IS 'Días del ciclo (30 para mensual)';
COMMENT ON COLUMN plans.grace_period_days     IS 'Días de gracia antes de suspender';

-- ── Límites cuantitativos por plan ────────────────────────────────────────────

CREATE TABLE plan_limits
(
    id             UUID PRIMARY KEY DEFAULT uuidv7(),
    plan_id        UUID         NOT NULL,
    resource_key   VARCHAR(50)  NOT NULL,
    resource_value BIGINT       NOT NULL,
    unit           VARCHAR(20),

    CONSTRAINT fk_limit_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE,
    CONSTRAINT uq_limit_plan_resource UNIQUE (plan_id, resource_key)
);

COMMENT ON TABLE  plan_limits               IS 'Límites cuantitativos por recurso para cada plan.';
COMMENT ON COLUMN plan_limits.resource_key  IS 'Clave del recurso: maxUsers, maxStorageMB, maxPatients, etc.';
COMMENT ON COLUMN plan_limits.resource_value IS 'Valor del límite (-1 = ilimitado)';
COMMENT ON COLUMN plan_limits.unit          IS 'Unidad de medida: users, MB, pages/mes, studies, docs';

-- ── Features booleanas por plan ───────────────────────────────────────────────

CREATE TABLE plan_features
(
    id             UUID PRIMARY KEY DEFAULT uuidv7(),
    plan_id        UUID         NOT NULL,
    feature_key    VARCHAR(50)  NOT NULL,
    is_enabled     BOOLEAN      NOT NULL DEFAULT false,
    description    VARCHAR(255),

    CONSTRAINT fk_feature_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE,
    CONSTRAINT uq_feature_plan_key UNIQUE (plan_id, feature_key)
);

COMMENT ON TABLE  plan_features               IS 'Features encendidas/apagadas por plan.';
COMMENT ON COLUMN plan_features.feature_key   IS 'Ej: dicom_imaging, ocr_scanning, custom_branding, api_access';

-- ── Alter tenants: billing cycle y end date persistido ────────────────────────

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS billing_cycle VARCHAR(10) NOT NULL DEFAULT 'MONTHLY';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS subscription_end_date DATE;

COMMENT ON COLUMN tenants.billing_cycle          IS 'MONTHLY | YEARLY';
COMMENT ON COLUMN tenants.subscription_end_date  IS 'Fecha de finalización efectiva según el ciclo del plan';

-- Poblar end_date para tenants existentes (30 días desde start_date)
UPDATE tenants
SET subscription_end_date = subscription_start_date + INTERVAL '30 days'
WHERE subscription_end_date IS NULL
  AND subscription_start_date IS NOT NULL;

-- ── File size tracking para métricas de almacenamiento real ───────────────────

ALTER TABLE documents ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT DEFAULT 0;
ALTER TABLE dicom_instances ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT DEFAULT 0;

COMMENT ON COLUMN documents.file_size_bytes       IS 'Tamaño real del archivo subido en bytes';
COMMENT ON COLUMN dicom_instances.file_size_bytes IS 'Tamaño real del archivo DICOM subido en bytes';

-- =============================================================================
-- SEED DATA: Planes BASE
-- =============================================================================

-- ── BASIC ─────────────────────────────────────────────────────────────────────

INSERT INTO plans (id, name, display_name, description, price_monthly, price_yearly, cycle_days, grace_period_days, sort_order)
VALUES
(gen_random_uuid(), 'BASIC', 'Básico',
 'Plan ideal para clínicas pequeñas. Incluye funcionalidades esenciales de gestión documental y workflow básico.',
 5, 50, 30, 3, 1);

INSERT INTO plan_limits (plan_id, resource_key, resource_value, unit)
SELECT id, 'maxUsers', 10, 'users' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxStorageMB', 1024, 'MB' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxApiCallsPerMonth', 500, 'calls/month' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxPatients', 100, 'patients' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxDocuments', 500, 'docs' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxDocumentTemplates', 5, 'templates' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxReportTemplates', 3, 'reports' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxDicomStudies', 0, 'studies/month' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxOcrPagesPerMonth', 0, 'pages/month' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxBackupsPerYear', 3, 'backups/year' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxStaffRoles', 5, 'roles' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxActiveReviewTasks', 50, 'count' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxReviewTasksPerMonth', 100, 'count' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxVersionsPerDocument', 50, 'count' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'maxVersionsPerMonth', 200, 'count' FROM plans WHERE name = 'BASIC';

INSERT INTO plan_features (plan_id, feature_key, is_enabled, description)
SELECT id, 'dicom_imaging', false, 'Módulo de radiología DICOM' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'ocr_scanning', false, 'Escaneo OCR automático de documentos' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'online_editing', false, 'Edición online de documentos (OnlyOffice)' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'custom_branding', false, 'Personalización de colores y logo' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'advanced_analytics', false, 'Dashboard de analíticas avanzadas' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'report_builder', false, 'Constructor de informes personalizados' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'api_access', false, 'Acceso a API pública' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'email_notifications', false, 'Notificaciones por email' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'push_notifications', false, 'Notificaciones push' FROM plans WHERE name = 'BASIC'
UNION ALL SELECT id, 'custom_roles', false, 'Roles de usuario personalizados' FROM plans WHERE name = 'BASIC';

-- ── PRO ───────────────────────────────────────────────────────────────────────

INSERT INTO plans (id, name, display_name, description, price_monthly, price_yearly, cycle_days, grace_period_days, sort_order)
VALUES
(gen_random_uuid(), 'PRO', 'Profesional',
 'Plan avanzado para clínicas en crecimiento. Incluye radiología DICOM, OCR, analíticas y personalización de marca.',
 25, 250, 30, 7, 2);

INSERT INTO plan_limits (plan_id, resource_key, resource_value, unit)
SELECT id, 'maxUsers', 50, 'users' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxStorageMB', 10240, 'MB' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxApiCallsPerMonth', 10000, 'calls/month' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxPatients', 1000, 'patients' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxDocuments', 5000, 'docs' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxDocumentTemplates', 20, 'templates' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxReportTemplates', 10, 'reports' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxDicomStudies', 50, 'studies/month' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxOcrPagesPerMonth', 100, 'pages/month' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxBackupsPerYear', 52, 'backups/year' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxStaffRoles', 50, 'roles' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxActiveReviewTasks', 200, 'count' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxReviewTasksPerMonth', 500, 'count' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxVersionsPerDocument', 200, 'count' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'maxVersionsPerMonth', 1000, 'count' FROM plans WHERE name = 'PRO';

INSERT INTO plan_features (plan_id, feature_key, is_enabled, description)
SELECT id, 'dicom_imaging', true, 'Módulo de radiología DICOM' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'ocr_scanning', true, 'Escaneo OCR automático de documentos' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'online_editing', true, 'Edición online de documentos (OnlyOffice)' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'custom_branding', true, 'Personalización de colores y logo' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'advanced_analytics', true, 'Dashboard de analíticas avanzadas' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'report_builder', true, 'Constructor de informes personalizados' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'api_access', true, 'Acceso a API pública (rate limit estándar)' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'email_notifications', true, 'Notificaciones por email' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'push_notifications', false, 'Notificaciones push' FROM plans WHERE name = 'PRO'
UNION ALL SELECT id, 'custom_roles', true, 'Roles de usuario personalizados' FROM plans WHERE name = 'PRO';

-- ── ENTERPRISE ────────────────────────────────────────────────────────────────

INSERT INTO plans (id, name, display_name, description, price_monthly, price_yearly, cycle_days, grace_period_days, sort_order)
VALUES
(gen_random_uuid(), 'ENTERPRISE', 'Empresarial',
 'Plan completo sin límites. Para hospitales y grupos médicos. Incluye todas las funcionalidades, soporte 24/7 y account manager dedicado.',
 45, 450, 30, 15, 3);

INSERT INTO plan_limits (plan_id, resource_key, resource_value, unit)
SELECT id, 'maxUsers', -1, 'users' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxStorageMB', -1, 'MB' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxApiCallsPerMonth', -1, 'calls/month' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxPatients', -1, 'patients' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxDocuments', -1, 'docs' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxDocumentTemplates', -1, 'templates' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxReportTemplates', -1, 'reports' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxDicomStudies', -1, 'studies/month' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxOcrPagesPerMonth', -1, 'pages/month' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxBackupsPerYear', -1, 'backups/year' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxStaffRoles', -1, 'roles' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxActiveReviewTasks', -1, 'count' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxReviewTasksPerMonth', -1, 'count' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxVersionsPerDocument', -1, 'count' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'maxVersionsPerMonth', -1, 'count' FROM plans WHERE name = 'ENTERPRISE';

INSERT INTO plan_features (plan_id, feature_key, is_enabled, description)
SELECT id, 'dicom_imaging', true, 'Módulo de radiología DICOM (ilimitado)' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'ocr_scanning', true, 'Escaneo OCR automático (ilimitado)' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'online_editing', true, 'Edición online de documentos (OnlyOffice)' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'custom_branding', true, 'Personalización de colores y logo' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'advanced_analytics', true, 'Dashboard avanzado con reportes custom' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'report_builder', true, 'Report Builder avanzado' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'api_access', true, 'API pública con rate limits extendidos' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'email_notifications', true, 'Notificaciones por email' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'push_notifications', true, 'Notificaciones push' FROM plans WHERE name = 'ENTERPRISE'
UNION ALL SELECT id, 'custom_roles', true, 'Roles personalizados ilimitados' FROM plans WHERE name = 'ENTERPRISE';
