CREATE TABLE IF NOT EXISTS document_ocr_metadata (
    document_id UUID PRIMARY KEY,
    raw_text    TEXT,
    datos_estructurados JSONB,
    confidence_score    DECIMAL(4,2),
    pages_processed     INT,
    file_type           VARCHAR(20),
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT now(),
    CONSTRAINT fk_ocr_document FOREIGN KEY (document_id) 
        REFERENCES documents(id) ON DELETE CASCADE
);