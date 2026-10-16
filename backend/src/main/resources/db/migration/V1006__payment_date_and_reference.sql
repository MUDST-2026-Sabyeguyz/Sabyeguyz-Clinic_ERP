-- The Manager records when a monthly payment was really paid and a reference for it (transfer or slip number).
-- paid_at stays the time the confirmation was recorded; paid_date is the payment date the Manager enters.
ALTER TABLE staff_monthly_payment
    ADD COLUMN paid_date         DATE,
    ADD COLUMN payment_reference VARCHAR(100);

UPDATE staff_monthly_payment
SET paid_date = CAST(paid_at AS DATE)
WHERE payment_status = 'Paid' AND paid_at IS NOT NULL;

-- A Reopen of a closed month keeps who did it, when and why on the closing itself (the audit log keeps every one).
ALTER TABLE monthly_commission_closing
    ADD COLUMN reopen_reason VARCHAR(255),
    ADD COLUMN reopened_by   BIGINT,
    ADD COLUMN reopened_at   TIMESTAMP,
    ADD CONSTRAINT fk_monthly_closing_reopened_by
        FOREIGN KEY (reopened_by) REFERENCES app_user (user_id);
