-- =============================================================================
-- V32 — HISTORIAS CLÍNICAS: Estructurar alergias y agregar condiciones crónicas
--        y medicamentos actuales
-- =============================================================================

-- Nuevas columnas
ALTER TABLE clinical_histories ADD COLUMN chronic_conditions  text;
ALTER TABLE clinical_histories ADD COLUMN current_medications jsonb;

-- Migrar alergias existentes (arreglo de strings) a arreglo de objetos
-- { allergen, severity, reaction }
UPDATE clinical_histories
SET allergies = (
        SELECT jsonb_agg(
                   jsonb_build_object(
                       'allergen', elem,
                       'severity', NULL,
                       'reaction', NULL
                   )
               )
        FROM jsonb_array_elements_text(allergies) AS elem
    )
WHERE allergies IS NOT NULL
  AND jsonb_typeof(allergies) = 'array'
  AND jsonb_array_length(allergies) > 0
  AND jsonb_typeof(allergies -> 0) = 'string';
