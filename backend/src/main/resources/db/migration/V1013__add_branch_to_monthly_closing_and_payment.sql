-- Add branch references before reviewing and backfilling historical rows.
ALTER TABLE monthly_commission_closing
    ADD COLUMN branch_id BIGINT;

ALTER TABLE staff_monthly_payment
    ADD COLUMN branch_id BIGINT;

ALTER TABLE monthly_commission_closing
    ADD CONSTRAINT fk_monthly_closing_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id);

ALTER TABLE staff_monthly_payment
    ADD CONSTRAINT fk_staff_monthly_payment_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id);


-- Stop rather than guess the branch of historical records.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM monthly_commission_closing
        WHERE branch_id IS NULL
    ) OR EXISTS (
        SELECT 1 FROM staff_monthly_payment
        WHERE branch_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Historical closing/payment rows require branch review before migration';
END IF;
END
$$;

-- Every monthly record must belong to a branch.
ALTER TABLE monthly_commission_closing
    ALTER COLUMN branch_id SET NOT NULL;

ALTER TABLE staff_monthly_payment
    ALTER COLUMN branch_id SET NOT NULL;

-- Allow separate monthly records for the same staff in different branches.
ALTER TABLE monthly_commission_closing
    DROP CONSTRAINT uq_monthly_closing_month_staff,
    ADD CONSTRAINT uq_monthly_closing_branch_month_staff
        UNIQUE (branch_id, closing_month, staff_id);

ALTER TABLE staff_monthly_payment
    DROP CONSTRAINT uq_staff_monthly_payment_staff_month,
    ADD CONSTRAINT uq_staff_monthly_payment_branch_staff_month
        UNIQUE (branch_id, staff_id, payment_month);


-- Provide the referenced key for the payment-to-closing consistency check.
ALTER TABLE monthly_commission_closing
    ADD CONSTRAINT uq_monthly_closing_id_staff_branch_month
        UNIQUE (closing_id, staff_id, branch_id, closing_month);

-- A linked closing must belong to the same staff, branch and month.
ALTER TABLE staff_monthly_payment
    ADD CONSTRAINT fk_staff_payment_matching_closing
        FOREIGN KEY (closing_id, staff_id, branch_id, payment_month)
            REFERENCES monthly_commission_closing (
                                                   closing_id, staff_id, branch_id, closing_month
                );