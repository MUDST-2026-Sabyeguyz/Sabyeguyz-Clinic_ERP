-- Preserve historical rows whose branch was never recorded.
ALTER TABLE monthly_commission_closing ADD COLUMN branch_id BIGINT;
ALTER TABLE staff_monthly_payment ADD COLUMN branch_id BIGINT;
ALTER TABLE monthly_commission_closing ADD CONSTRAINT fk_monthly_closing_branch FOREIGN KEY (branch_id) REFERENCES branch(branch_id);
ALTER TABLE staff_monthly_payment ADD CONSTRAINT fk_staff_monthly_payment_branch FOREIGN KEY (branch_id) REFERENCES branch(branch_id);

-- New rows require a branch. Historical rows remain unchanged.
DO $$
DECLARE legacy_closing_id BIGINT;
DECLARE legacy_payment_id BIGINT;
BEGIN
  SELECT COALESCE(MAX(closing_id), 0) INTO legacy_closing_id FROM monthly_commission_closing;
  SELECT COALESCE(MAX(salary_payment_id), 0) INTO legacy_payment_id FROM staff_monthly_payment;
  EXECUTE format('ALTER TABLE monthly_commission_closing ADD CONSTRAINT chk_new_closing_branch CHECK (branch_id IS NOT NULL OR closing_id <= %s)', legacy_closing_id);
  EXECUTE format('ALTER TABLE staff_monthly_payment ADD CONSTRAINT chk_new_staff_payment_branch CHECK (branch_id IS NOT NULL OR salary_payment_id <= %s)', legacy_payment_id);
END $$;

ALTER TABLE monthly_commission_closing
  DROP CONSTRAINT uq_monthly_closing_month_staff,
  ADD CONSTRAINT uq_monthly_closing_branch_month_staff UNIQUE (branch_id, closing_month, staff_id);
ALTER TABLE staff_monthly_payment
  DROP CONSTRAINT uq_staff_monthly_payment_staff_month,
  ADD CONSTRAINT uq_staff_monthly_payment_branch_staff_month UNIQUE (branch_id, staff_id, payment_month);
ALTER TABLE monthly_commission_closing
  ADD CONSTRAINT uq_monthly_closing_id_staff_branch_month UNIQUE (closing_id, staff_id, branch_id, closing_month);
ALTER TABLE staff_monthly_payment
  ADD CONSTRAINT fk_staff_payment_matching_closing
  FOREIGN KEY (closing_id, staff_id, branch_id, payment_month)
  REFERENCES monthly_commission_closing (closing_id, staff_id, branch_id, closing_month);
