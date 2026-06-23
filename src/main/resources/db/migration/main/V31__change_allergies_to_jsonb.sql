-- =============================================================================
-- V31 — ALTER TABLE clinical_histories: Convert allergies column to jsonb
-- =============================================================================

ALTER TABLE clinical_histories DROP COLUMN allergies;
ALTER TABLE clinical_histories ADD COLUMN allergies jsonb;
