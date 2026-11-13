-- Opt-in synthetic demo only: V1101's condition examples must not overlap the
-- active global fee rule, otherwise real service sales fail with HTTP 409.
UPDATE treatment_fee_rule
SET effective_to = DATE '2026-12-31'
WHERE rule_version = 1;

UPDATE treatment_fee_rule
SET effective_from = DATE '2027-01-01'
WHERE rule_version = 2 AND rule_name = 'DEMO: ค่ามือ 7.5%';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM treatment_fee_rule a JOIN treatment_fee_rule b
          ON a.treatment_fee_rule_id < b.treatment_fee_rule_id
         AND daterange(a.effective_from, a.effective_to, '[]')
             && daterange(b.effective_from, b.effective_to, '[]')
    ) THEN
        RAISE EXCEPTION 'Synthetic fee rule effective ranges overlap';
    END IF;
END $$;
