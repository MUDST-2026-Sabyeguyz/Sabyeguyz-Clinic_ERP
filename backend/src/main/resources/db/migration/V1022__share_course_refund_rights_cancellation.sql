-- Share one rights-cancellation event across multiple payment refunds.
ALTER TABLE course_refund_item
    ADD COLUMN rights_cancellation_item_id BIGINT,
    ALTER COLUMN cancelled_visits DROP NOT NULL,
    DROP CONSTRAINT chk_course_refund_item_cancelled_visits;

ALTER TABLE course_refund_item
    ADD CONSTRAINT fk_course_refund_item_rights_cancellation
        FOREIGN KEY (rights_cancellation_item_id)
        REFERENCES course_refund_item (course_refund_item_id),

    ADD CONSTRAINT chk_course_refund_item_rights_cancellation
        CHECK (
            (
                rights_cancellation_item_id IS NULL
                AND cancelled_visits IS NOT NULL
                AND cancelled_visits >= 1
            )
            OR
            (
                rights_cancellation_item_id IS NOT NULL
                AND cancelled_visits IS NULL
                AND rights_cancellation_item_id <> course_refund_item_id
            )
        );

-- Find all related items belonging to one main cancellation item.
CREATE INDEX idx_course_refund_item_rights_cancellation
    ON course_refund_item (rights_cancellation_item_id);

-- Backend validation must require a main item on the same course.
-- Group operations must lock the main item before checking state or ledger.

-- Refund cancellation reversals retain the shared main item as their source.
-- All other ledger source rules remain unchanged.
ALTER TABLE course_ledger
    DROP CONSTRAINT chk_course_ledger_entry_source,
    ADD CONSTRAINT chk_course_ledger_entry_source
        CHECK (
            (entry_type IN ('TransferOut', 'TransferIn')
                AND transfer_id IS NOT NULL
                AND visit_usage_id IS NULL
                AND sales_transaction_id IS NULL
                AND reverses_ledger_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL
                AND course_refund_item_id IS NULL
                AND cancellation_id IS NULL)
            OR
            (entry_type = 'Treatment'
                AND visit_usage_id IS NOT NULL
                AND transfer_id IS NULL
                AND sales_transaction_id IS NULL
                AND reverses_ledger_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL
                AND course_refund_item_id IS NULL
                AND cancellation_id IS NULL)
            OR
            (entry_type = 'Purchase'
                AND sales_transaction_id IS NOT NULL
                AND transfer_id IS NULL
                AND visit_usage_id IS NULL
                AND reverses_ledger_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL
                AND course_refund_item_id IS NULL
                AND cancellation_id IS NULL)
            OR
            (entry_type = 'Adjustment'
                AND NULLIF(BTRIM(remark), '') IS NOT NULL
                AND adjustment_approved_by IS NOT NULL
                AND adjustment_approved_at IS NOT NULL
                AND transfer_id IS NULL
                AND visit_usage_id IS NULL
                AND sales_transaction_id IS NULL
                AND reverses_ledger_id IS NULL
                AND course_refund_item_id IS NULL
                AND cancellation_id IS NULL)
            OR
            (entry_type = 'Reversal'
                AND reverses_ledger_id IS NOT NULL
                AND NOT (transfer_id IS NOT NULL AND visit_usage_id IS NOT NULL)
                AND sales_transaction_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL
                AND cancellation_id IS NULL
                AND (
                    course_refund_item_id IS NULL
                    OR (transfer_id IS NULL AND visit_usage_id IS NULL)
                ))
            OR
            (entry_type = 'Cancellation'
                AND num_nonnulls(course_refund_item_id, cancellation_id) = 1
                AND transfer_id IS NULL
                AND visit_usage_id IS NULL
                AND sales_transaction_id IS NULL
                AND reverses_ledger_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL)
        );
