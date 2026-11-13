-- Scheme versions must be unique across all scheme names.
-- Existing duplicate versions require review; never renumber history.
DO $$
BEGIN
    IF EXISTS (
        SELECT scheme_version
        FROM commission_scheme
        GROUP BY scheme_version
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce scheme version uniqueness: duplicate versions require review';
END IF;
END
$$;

ALTER TABLE commission_scheme
    ADD CONSTRAINT uq_commission_scheme_version
        UNIQUE (scheme_version);