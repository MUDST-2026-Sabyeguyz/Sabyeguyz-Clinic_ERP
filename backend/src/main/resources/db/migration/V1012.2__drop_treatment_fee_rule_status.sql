-- Disabled rules require review before switching to date-based selection.
DO $$
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM treatment_fee_rule
            WHERE treatment_fee_rule_status = FALSE
        ) THEN
            RAISE EXCEPTION
                'Cannot remove treatment fee rule status: disabled rules require review';
        END IF;
    END
$$;

-- Fee rules are selected by their effective date ranges.
ALTER TABLE treatment_fee_rule
    DROP COLUMN treatment_fee_rule_status;
