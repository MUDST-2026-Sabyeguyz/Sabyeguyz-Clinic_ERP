-- Temporary Assumption: RP -> numeric_code = 01.
-- Replace this mapping when the real branch master data is confirmed by Database/BA.
ALTER TABLE branches ADD COLUMN IF NOT EXISTS numeric_code VARCHAR(2);

UPDATE branches
SET numeric_code = '01'
WHERE code = 'RP';
