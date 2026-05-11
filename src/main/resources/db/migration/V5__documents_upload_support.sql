-- Permite documentos externos sin plantilla (template_id opcional)
ALTER TABLE documents ALTER COLUMN template_id DROP NOT NULL;

-- Ampliar file_url para paths más largos
ALTER TABLE documents ALTER COLUMN file_url TYPE varchar(500);

-- Índice para búsquedas de documentos externos
CREATE INDEX IF NOT EXISTS idx_documents_external ON documents(is_external_source);
