-- V4__add_patient_type.sql

-- 1. Add discriminator column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS type VARCHAR(31) NOT NULL DEFAULT 'USER';
UPDATE users SET type = 'USER' WHERE type IS NULL OR type = '';

-- 2. Create patients table  (id = FK to users.id = PK)
CREATE TABLE IF NOT EXISTS patients (
    id         BIGINT  NOT NULL,
    birth_date DATE,
    CONSTRAINT pk_patients PRIMARY KEY (id),
    CONSTRAINT fk_patients_user_id FOREIGN KEY (id) REFERENCES users(id) ON DELETE CASCADE
);
