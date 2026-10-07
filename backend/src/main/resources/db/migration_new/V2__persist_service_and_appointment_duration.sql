-- Preserve the active v2 60-minute historical contract. New bookings lock the
-- configured service duration; subsequent service edits do not alter old visits.
ALTER TABLE service ADD COLUMN default_duration_minutes INTEGER NOT NULL DEFAULT 60;
ALTER TABLE service ADD CONSTRAINT chk_service_duration_positive CHECK (default_duration_minutes > 0);
ALTER TABLE appointment ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 60;
ALTER TABLE appointment ADD CONSTRAINT chk_appointment_duration_positive CHECK (duration_minutes > 0);
