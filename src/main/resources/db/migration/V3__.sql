-- V3: Align schema with updated entity definitions

-- 1. Create missing enum type
CREATE TYPE gender_enum AS ENUM ('MALE', 'FEMALE');

-- 2. permissions: add module, description, is_active; drop redundant unique on action
ALTER TABLE permissions
    ADD COLUMN module      varchar(50)  NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN description varchar(255),
    ADD COLUMN is_active   boolean      NOT NULL DEFAULT true;

ALTER TABLE permissions DROP CONSTRAINT IF EXISTS uq_permissions_action;

-- 3. roles: add is_active
ALTER TABLE roles
    ADD COLUMN is_active boolean NOT NULL DEFAULT true;

-- 4. users: rename national_id → document_number, add username, phone, gender
ALTER TABLE users RENAME COLUMN national_id TO document_number;

ALTER TABLE users
    ADD COLUMN username varchar(10),
    ADD COLUMN phone    varchar(20),
    ADD COLUMN gender   varchar(50);

UPDATE users
SET username = 'USR-' || LPAD(floor(random() * 9000 + 1000)::text, 4, '0')
WHERE username IS NULL;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_username UNIQUE (username);

-- 5. patients: rename columns, add document_type, change gender to gender_enum
ALTER TABLE patients RENAME COLUMN national_id TO document_number;
ALTER TABLE patients RENAME COLUMN first_names  TO first_name;
ALTER TABLE patients RENAME COLUMN last_names   TO last_name;

ALTER TABLE patients
    ADD COLUMN document_type document_type_enum;

ALTER TABLE patients
    ALTER COLUMN gender TYPE gender_enum USING (
        CASE
            WHEN upper(gender) IN ('MALE',   'M') THEN 'MALE'::gender_enum
            WHEN upper(gender) IN ('FEMALE', 'F') THEN 'FEMALE'::gender_enum
            ELSE NULL
        END
    );

-- 6. Rename join table to match entity @JoinTable(name = "role_permission")
ALTER TABLE role_permissions RENAME TO role_permission;
