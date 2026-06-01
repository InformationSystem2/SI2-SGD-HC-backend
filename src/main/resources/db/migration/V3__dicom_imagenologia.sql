-- =============================================================================
-- V3 — IMAGENOLOGÍA DICOM: Estudios, Series, Instancias (Multi-Tenant)
-- =============================================================================

-- 1. NIVEL ESTUDIO: Agrupador principal del examen

CREATE TABLE dicom_studies (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    tenant_id            UUID         NOT NULL,
    patient_id           UUID         NOT NULL,
    uploader_id          UUID         NOT NULL,

    study_instance_uid   VARCHAR(100) NOT NULL,    -- Tag (0020,000D)
    study_date           DATE,                     -- Tag (0008,0020)
    study_description    VARCHAR(255),             -- Tag (0008,1030)
    accession_number     VARCHAR(50),              -- ID del workflow (PACS)

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_study_uid        UNIQUE (study_instance_uid),
    CONSTRAINT fk_study_tenant     FOREIGN KEY (tenant_id)   REFERENCES tenants(id),
    CONSTRAINT fk_study_patient    FOREIGN KEY (patient_id)  REFERENCES patients(id),
    CONSTRAINT fk_study_uploader   FOREIGN KEY (uploader_id) REFERENCES users(id)
);

-- 2. NIVEL SERIE: Agrupador por modalidad o toma

CREATE TABLE dicom_series (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    tenant_id            UUID         NOT NULL,    -- Denormalización para rendimiento
    study_id             UUID         NOT NULL,

    series_instance_uid  VARCHAR(100) NOT NULL,    -- Tag (0020,000E)
    modality             VARCHAR(20)  NOT NULL,    -- CR, CT, MR, US, etc.
    series_number        INTEGER,                  -- Orden en el visor
    series_description   VARCHAR(255),
    body_part            VARCHAR(100),             -- Tag (0018,0015)

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_series_uid       UNIQUE (series_instance_uid),
    CONSTRAINT fk_series_tenant    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_series_study     FOREIGN KEY (study_id)  REFERENCES dicom_studies(id) ON DELETE CASCADE
);

-- 3. NIVEL INSTANCIA: Cada imagen individual (.dcm)

CREATE TABLE dicom_instances (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    tenant_id            UUID         NOT NULL,    -- Denormalización para seguridad
    series_id            UUID         NOT NULL,

    sop_instance_uid     VARCHAR(100) NOT NULL,    -- Tag (0008,0018)
    instance_number      INTEGER,                  -- Orden del corte en el stack
    file_path            TEXT         NOT NULL,     -- Ruta en Cloud Storage

    -- Metadatos de renderizado (Cruciales para el visor Angular/Cornerstone)
    rows                 INTEGER,                  -- Altura px
    columns              INTEGER,                  -- Ancho px
    bits_allocated       INTEGER,                  -- Profundidad de color
    window_center        NUMERIC,                  -- Brillo (Level)
    window_width         NUMERIC,                  -- Contraste (Width)
    pixel_spacing        NUMERIC[],                -- [x, y] para mediciones

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_instance_uid     UNIQUE (sop_instance_uid),
    CONSTRAINT fk_instance_tenant  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_instance_series  FOREIGN KEY (series_id) REFERENCES dicom_series(id) ON DELETE CASCADE
);

CREATE INDEX idx_dicom_studies_tenant_patient   ON dicom_studies(tenant_id, patient_id);
CREATE INDEX idx_dicom_series_tenant_study      ON dicom_series(tenant_id, study_id);
CREATE INDEX idx_dicom_instances_tenant_series  ON dicom_instances(tenant_id, series_id, instance_number);
