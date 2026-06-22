-- V18: Add missing updated_at column to backup_history (required by RootEntity)
ALTER TABLE backup_history ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
