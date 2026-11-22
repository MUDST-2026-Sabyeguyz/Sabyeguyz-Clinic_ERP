-- Allow prepaid SingleVisit sales created before an appointment/visit (V1030),
-- while keeping treating_staff_id mandatory when a SingleVisit is already linked to a visit.
ALTER TABLE sales_transaction
    DROP CONSTRAINT IF EXISTS chk_sales_transaction_required_staff_visit;

ALTER TABLE sales_transaction
    ADD CONSTRAINT chk_sales_transaction_required_staff_visit
        CHECK (
            (transaction_type <> 'CoursePurchase' OR salesperson_staff_id IS NOT NULL)
            AND (
                transaction_type <> 'SingleVisit'
                OR (visit_id IS NULL AND treating_staff_id IS NULL)
                OR (visit_id IS NOT NULL AND (transaction_status = FALSE OR treating_staff_id IS NOT NULL))
            )
        );
