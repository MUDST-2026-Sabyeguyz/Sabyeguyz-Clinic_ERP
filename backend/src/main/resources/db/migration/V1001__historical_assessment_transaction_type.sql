-- Retain historical Assessment rows for receipt reads; new Assessment sales remain blocked in service validation.
ALTER TYPE transaction_type_enum ADD VALUE IF NOT EXISTS 'Assessment';
