-- Payment label master
-- Keep existing receipt.payment_label and payment_status_option
-- until the backend migration is complete.

CREATE TABLE payment_label (
    payment_label_id        BIGSERIAL PRIMARY KEY,              -- auto-increment

    payment_label_code      VARCHAR(20) NOT NULL,
    payment_label_name      VARCHAR(50) NOT NULL,
    payment_label_status    BOOLEAN     NOT NULL DEFAULT TRUE,  --0=Disabled, 1=Enabled

    payment_label_create_at TIMESTAMP   NOT NULL
                                                 DEFAULT CURRENT_TIMESTAMP,
    payment_label_create_by BIGINT      NOT NULL,
    payment_label_update_at TIMESTAMP,
    payment_label_update_by BIGINT,

    CONSTRAINT uq_payment_label_code
        UNIQUE (payment_label_code),

    CONSTRAINT uq_payment_label_name
        UNIQUE (payment_label_name),

    CONSTRAINT chk_payment_label_code
        CHECK (
            payment_label_code IN ('FULL', 'INSTALLMENT', 'DEPOSIT')
        ),

    CONSTRAINT fk_payment_label_create_by
        FOREIGN KEY (payment_label_create_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_payment_label_update_by
        FOREIGN KEY (payment_label_update_by)
            REFERENCES app_user (user_id)
);

-- Fail the migration if the system user required for seeding is missing.
-- This prevents the INSERT from silently creating zero payment labels.
DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM app_user WHERE username = 'system'
        ) THEN
            RAISE EXCEPTION 'Cannot seed payment labels: system user is missing';
        END IF;
    END
$$;

-- Preserve availability from the existing payment-status settings.
-- Legacy PAID maps to the new FULL code.
INSERT INTO payment_label (
    payment_label_code,
    payment_label_name,
    payment_label_status,
    payment_label_create_by
)
SELECT
    labels.new_code,
    labels.display_name,
    COALESCE(options.enabled, TRUE),
    seed_actor.user_id
FROM (
         VALUES
             ('FULL', 'ชำระเต็ม', 'PAID'),
             ('INSTALLMENT', 'ผ่อน', 'INSTALLMENT'),
             ('DEPOSIT', 'มัดจำ', 'DEPOSIT')
     ) AS labels(new_code, display_name, old_code)
         LEFT JOIN payment_status_option AS options
                   ON options.status_code = labels.old_code
         CROSS JOIN (
    SELECT user_id
    FROM app_user
    WHERE username = 'system'
) AS seed_actor;

-- Add as nullable before backfilling existing receipts.
ALTER TABLE receipt
    ADD COLUMN payment_label_id BIGINT;

UPDATE receipt AS receipt_row
SET payment_label_id = labels.payment_label_id
    FROM payment_label AS labels
WHERE labels.payment_label_code =
    CASE receipt_row.payment_label::TEXT
        --  OLD                  NEW
    WHEN 'FullyPaid'    THEN 'FULL'
    WHEN 'Installment'  THEN 'INSTALLMENT'
    WHEN 'Deposit'      THEN 'DEPOSIT'
END;

ALTER TABLE receipt
    ADD CONSTRAINT fk_receipt_payment_label
        FOREIGN KEY (payment_label_id)
            REFERENCES payment_label (payment_label_id);

CREATE INDEX idx_receipt_payment_label_id
    ON receipt (payment_label_id);
