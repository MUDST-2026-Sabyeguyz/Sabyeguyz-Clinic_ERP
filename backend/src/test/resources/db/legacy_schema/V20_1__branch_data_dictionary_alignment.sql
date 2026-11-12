-- Align `branches` with the confirmed Branch data dictionary.
-- Runs right after V20 (before the Postgres-only demo seed migrations V21+)
-- so it stays testable under H2.
--
-- Consolidates the legacy alpha `code` (e.g. 'RP') and the temporary
-- `numeric_code` (V3) into a single 2-digit `branch_code`, which is the
-- value the data dictionary defines and the one already used to build HN
-- and receipt numbers. Adds address/phone and create/update audit columns.

ALTER TABLE branches RENAME TO branch;
ALTER TABLE branch RENAME COLUMN id TO branch_id;
ALTER TABLE branch RENAME COLUMN name TO branch_name;
ALTER TABLE branch RENAME COLUMN active TO branch_status;
ALTER TABLE branch RENAME COLUMN created_at TO branch_create_at;

ALTER TABLE branch ADD COLUMN branch_code CHAR(2);
UPDATE branch SET branch_code = numeric_code WHERE numeric_code IS NOT NULL;
-- Fallback for any historical/local row that never got a numeric_code
-- (should not happen outside ad-hoc local data); keeps the NOT NULL/UNIQUE
-- constraints below from failing the migration.
UPDATE branch SET branch_code = lpad((branch_id % 100)::text, 2, '0') WHERE branch_code IS NULL;
ALTER TABLE branch ALTER COLUMN branch_code SET NOT NULL;
ALTER TABLE branch ADD CONSTRAINT uq_branch_branch_code UNIQUE (branch_code);
-- Digit-only format ("00"-"99") is validated in application code (see Branch),
-- matching how HN/receipt numbering already validates this elsewhere.

ALTER TABLE branch DROP COLUMN code;
ALTER TABLE branch DROP COLUMN numeric_code;

ALTER TABLE branch ADD COLUMN address VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE branch ADD COLUMN phone VARCHAR(20) NOT NULL DEFAULT '';
ALTER TABLE branch ALTER COLUMN address DROP DEFAULT;
ALTER TABLE branch ALTER COLUMN phone DROP DEFAULT;

ALTER TABLE branch ADD COLUMN branch_create_by BIGINT REFERENCES app_users(id);
ALTER TABLE branch ADD COLUMN branch_update_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE branch ADD COLUMN branch_update_by BIGINT REFERENCES app_users(id);
