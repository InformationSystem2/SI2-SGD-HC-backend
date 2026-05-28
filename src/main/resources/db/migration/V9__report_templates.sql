-- ── Plantillas de reportes QBE (Query by Example) ────────────────────────────
-- Cada departamento/usuario guarda su propia configuración de reporte:
-- tipo, columnas seleccionadas, filtros y orden. Multi-tenant.

CREATE TABLE report_templates
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    tenant_id       uuid          NOT NULL,
    owner_id        uuid          NOT NULL,
    name            varchar(150)  NOT NULL,
    description     varchar(500),
    department      varchar(150),
    report_type     varchar(80)   NOT NULL,
    selected_fields jsonb         NOT NULL,
    filters         jsonb         NOT NULL DEFAULT '[]'::jsonb,
    sort_field      varchar(80),
    sort_order      varchar(4)             DEFAULT 'asc',
    is_shared       boolean       NOT NULL DEFAULT false,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_report_templates_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT fk_report_templates_owner  FOREIGN KEY (owner_id)  REFERENCES users (id)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE INDEX idx_report_templates_tenant ON report_templates (tenant_id);
CREATE INDEX idx_report_templates_owner  ON report_templates (owner_id);
