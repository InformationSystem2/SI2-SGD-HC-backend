ALTER TABLE tenants ADD COLUMN logo_url VARCHAR(500);
CREATE INDEX IF NOT EXISTS idx_tenants_logo_url ON tenants(logo_url) WHERE logo_url IS NOT NULL;



DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_enum WHERE enumlabel = 'PENDING_PAYMENT'
  ) THEN
    ALTER TYPE subscription_status_enum ADD VALUE 'PENDING_PAYMENT';
  END IF;
END
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_enum WHERE enumlabel = 'SUSPENDED'
  ) THEN
    ALTER TYPE subscription_status_enum ADD VALUE 'SUSPENDED';
  END IF;
END
$$;