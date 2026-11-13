-- Keep historical statuses; require current statuses on new appointments.
DO $$
DECLARE legacy_appointment_id BIGINT;
BEGIN
  SELECT COALESCE(MAX(appointment_id), 0) INTO legacy_appointment_id FROM appointment;
  EXECUTE format('ALTER TABLE appointment ADD CONSTRAINT chk_new_appointment_status CHECK (appointment_status::text IN (''Unconfirmed'', ''Confirmed'', ''Cancelled'') OR appointment_id <= %s)', legacy_appointment_id);
END $$;
