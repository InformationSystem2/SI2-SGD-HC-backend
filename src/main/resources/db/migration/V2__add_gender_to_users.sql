ALTER TABLE users ADD COLUMN gender VARCHAR(20);

UPDATE users SET gender = 'unknown' WHERE gender IS NULL;
ALTER TABLE users ALTER COLUMN gender SET NOT NULL;
