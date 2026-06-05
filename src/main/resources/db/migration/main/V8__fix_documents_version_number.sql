-- V8 — Agregar columnmissing version_number a documents
-- Error: JDBC exception column d1_0.version_number does not exist

ALTER TABLE documents ADD COLUMN IF NOT EXISTS version_number integer NOT NULL DEFAULT 1;