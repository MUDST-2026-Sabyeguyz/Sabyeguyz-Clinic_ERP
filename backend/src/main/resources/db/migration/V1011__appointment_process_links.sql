-- Restore the appointment document links migration under a new version.
-- V1010 is already occupied by fee-rule/contact changes; never rewrite it.
CREATE TABLE appointment_process (
    appointment_id bigint PRIMARY KEY REFERENCES appointment(appointment_id),
    receipt_id bigint UNIQUE REFERENCES receipt(receipt_id),
    usage_id bigint UNIQUE REFERENCES course_visit_usage(visit_usage_id),
    payment_fingerprint varchar(64),
    requires_review boolean NOT NULL DEFAULT false
);

-- Existing appointments may have unlinked bills/usages. Do not guess matches
-- or treat the absence of a link as proof that no transaction occurred.
INSERT INTO appointment_process (appointment_id, requires_review)
SELECT appointment_id, true FROM appointment;
