-- Existing receipts without a selected label require review.
-- Never assign a default label to historical receipts.
DO $$
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM receipt
            WHERE payment_label_id IS NULL
        ) THEN
            RAISE EXCEPTION
                'Cannot require receipt payment label: existing receipts without a label require review';
        END IF;
    END
$$;

ALTER TABLE receipt
    ALTER COLUMN payment_label_id SET NOT NULL;