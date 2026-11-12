ALTER TABLE staff ADD COLUMN IF NOT EXISTS branch_id BIGINT REFERENCES branch(branch_id);

UPDATE staff s
SET branch_id = b.branch_id
FROM branch b
WHERE s.branch_id IS NULL AND b.branch_code = '01';

CREATE INDEX IF NOT EXISTS idx_staff_branch_id ON staff(branch_id);
