ALTER TABLE staff_pay_adjustment
    ADD COLUMN branch_id BIGINT REFERENCES branch(branch_id),
    ADD COLUMN automatic BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN approval_reference VARCHAR(255),
    ADD COLUMN cancellation_reason VARCHAR(255),
    ADD COLUMN cancelled_at TIMESTAMP;

CREATE INDEX idx_staff_pay_adjustment_automatic_due
    ON staff_pay_adjustment (branch_id, applied_month, staff_id)
    WHERE automatic = TRUE AND adjustment_status IN ('Approved', 'PartiallyApplied');

UPDATE adjustment_application application
SET application_status = 'Cancelled'
FROM staff_pay_adjustment adjustment
JOIN staff person ON person.staff_id = adjustment.staff_id
WHERE application.adjustment_id = adjustment.adjustment_id
  AND application.application_status = 'Draft'
  AND person.termination_date IS NOT NULL
  AND adjustment.remaining_amount > 0;

UPDATE staff_pay_adjustment adjustment
SET adjustment_status = 'Cancelled', cancellation_reason = 'นักกายภาพลาออก', cancelled_at = CURRENT_TIMESTAMP
FROM staff person
WHERE person.staff_id = adjustment.staff_id
  AND person.termination_date IS NOT NULL
  AND adjustment.remaining_amount > 0
  AND adjustment.adjustment_status IN ('Pending', 'Approved', 'PartiallyApplied', 'ExternalReview');
