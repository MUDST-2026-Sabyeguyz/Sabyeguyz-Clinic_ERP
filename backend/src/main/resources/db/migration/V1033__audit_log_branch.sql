-- The audit page can show one branch's history: each row remembers the branch it belongs to when there is one.
ALTER TABLE audit_log ADD COLUMN branch_id BIGINT;
ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_log_branch FOREIGN KEY (branch_id) REFERENCES branch(branch_id);
CREATE INDEX idx_audit_log_branch ON audit_log (branch_id, changed_at DESC);

-- Existing rows: a branch's own changes belong to that branch; others carry the branch in their before/after JSON.
UPDATE audit_log SET branch_id = record_id::bigint
WHERE table_name = 'branch' AND record_id ~ '^[0-9]+$'
  AND EXISTS (SELECT 1 FROM branch b WHERE b.branch_id = record_id::bigint);

UPDATE audit_log a SET branch_id = (a.new_value::jsonb ->> 'branch_id')::bigint
WHERE a.branch_id IS NULL AND a.new_value ~ '^\s*\{' AND a.new_value::jsonb ->> 'branch_id' ~ '^[0-9]+$'
  AND EXISTS (SELECT 1 FROM branch b WHERE b.branch_id = (a.new_value::jsonb ->> 'branch_id')::bigint);

UPDATE audit_log a SET branch_id = (a.old_value::jsonb ->> 'branch_id')::bigint
WHERE a.branch_id IS NULL AND a.old_value ~ '^\s*\{' AND a.old_value::jsonb ->> 'branch_id' ~ '^[0-9]+$'
  AND EXISTS (SELECT 1 FROM branch b WHERE b.branch_id = (a.old_value::jsonb ->> 'branch_id')::bigint);
