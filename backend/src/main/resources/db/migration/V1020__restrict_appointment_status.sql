-- Existing legacy statuses must be reviewed before enforcing the current rules.
DO $$
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM appointment
            WHERE appointment_status::text NOT IN (
                'Unconfirmed', 'Confirmed', 'Cancelled'
            )
        ) THEN
            RAISE EXCEPTION
                'Cannot restrict appointment status: legacy statuses require review';
        END IF;
    END
$$;

-- Appointment statuses allowed by the current Data Dictionary.
ALTER TABLE appointment
    ADD CONSTRAINT chk_appointment_status_current
        CHECK (
            appointment_status::text IN (
    'Unconfirmed', 'Confirmed', 'Cancelled'
    )
);