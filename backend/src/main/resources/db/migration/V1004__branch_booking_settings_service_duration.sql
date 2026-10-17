-- Branch settings the appointment screens need (booking window, default length of an appointment, contact and licence)
-- and an optional appointment length per service. Every column is optional: nothing changes until a Manager sets them.
ALTER TABLE branch
    ADD COLUMN branch_email                VARCHAR(255),
    ADD COLUMN license_no                  VARCHAR(50),
    ADD COLUMN booking_open_time           TIME,
    ADD COLUMN booking_close_time          TIME,
    ADD COLUMN default_appointment_minutes INT;

ALTER TABLE branch
    ADD CONSTRAINT chk_branch_booking_window
        CHECK ((booking_open_time IS NULL AND booking_close_time IS NULL)
            OR (booking_open_time IS NOT NULL AND booking_close_time IS NOT NULL
                AND booking_open_time < booking_close_time)),
    ADD CONSTRAINT chk_branch_default_appointment_minutes
        CHECK (default_appointment_minutes IS NULL OR default_appointment_minutes BETWEEN 5 AND 480);

ALTER TABLE service
    ADD COLUMN default_appointment_minutes INT;

ALTER TABLE service
    ADD CONSTRAINT chk_service_default_appointment_minutes
        CHECK (default_appointment_minutes IS NULL OR default_appointment_minutes BETWEEN 5 AND 480);
