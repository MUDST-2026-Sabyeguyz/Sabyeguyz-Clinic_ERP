ALTER TABLE staff ADD COLUMN IF NOT EXISTS branch_id BIGINT REFERENCES branches(id);

UPDATE staff s
SET branch_id = b.id
FROM branches b
WHERE s.branch_id IS NULL AND b.code = 'RP';

CREATE INDEX IF NOT EXISTS idx_staff_branch_id ON staff(branch_id);
