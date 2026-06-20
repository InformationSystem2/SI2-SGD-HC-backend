-- V16: API call usage tracking per tenant per month
CREATE TABLE api_call_usage
(
    id             UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id      UUID         NOT NULL,
    year_month     VARCHAR(7)   NOT NULL,   -- '2026-06'
    call_count     BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_api_usage_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT uq_api_usage_tenant_month UNIQUE (tenant_id, year_month)
);

CREATE INDEX idx_api_usage_tenant_month ON api_call_usage (tenant_id, year_month);
