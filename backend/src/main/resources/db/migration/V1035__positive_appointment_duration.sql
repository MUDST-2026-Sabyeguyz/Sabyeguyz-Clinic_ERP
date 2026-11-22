-- AC 010.05, 011.04, 035.01 permit any positive integer number of minutes.
-- Preserve applied V1004 and replace its older 5..480 limitation additively.
ALTER TABLE branch DROP CONSTRAINT chk_branch_default_appointment_minutes;
ALTER TABLE branch ADD CONSTRAINT chk_branch_default_appointment_minutes
    CHECK (default_appointment_minutes IS NULL OR default_appointment_minutes >= 1);

-- The v2 Service entity/API uses V1007's default_duration_minutes column.
-- Reassert its positive-only rule here alongside the branch setting change.
ALTER TABLE service DROP CONSTRAINT chk_service_default_duration_minutes;
ALTER TABLE service ADD CONSTRAINT chk_service_default_duration_minutes
    CHECK (default_duration_minutes IS NULL OR default_duration_minutes >= 1);
