-- Keep missing historical end times; require an end time on new appointments.
DO $$
DECLARE legacy_appointment_id BIGINT;
BEGIN
  SELECT COALESCE(MAX(appointment_id), 0) INTO legacy_appointment_id FROM appointment;
  EXECUTE format('ALTER TABLE appointment ADD CONSTRAINT chk_new_appointment_end_at CHECK (appointment_end_at IS NOT NULL OR appointment_id <= %s)', legacy_appointment_id);
END $$;
