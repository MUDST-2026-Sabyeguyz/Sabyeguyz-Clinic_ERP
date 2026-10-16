-- Optional name of a treatment fee rule, shown on the settings page.
ALTER TABLE treatment_fee_rule ADD COLUMN IF NOT EXISTS rule_name VARCHAR(100);
