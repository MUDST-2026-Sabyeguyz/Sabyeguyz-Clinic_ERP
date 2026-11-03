-- Historical booking intervals require review.
-- Never infer their end times from current service or branch templates.
DO $$
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM appointment
            WHERE appointment_end_at IS NULL
        ) THEN
            RAISE EXCEPTION
                'Cannot require appointment end time: existing appointments without end time require review';
        END IF;
    END
$$;

ALTER TABLE appointment
    ALTER COLUMN appointment_end_at SET NOT NULL;