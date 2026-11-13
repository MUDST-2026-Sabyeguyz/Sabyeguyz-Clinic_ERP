-- Preserve voided payment history while allowing at most
-- one completed payment per receipt.
DO $$
    BEGIN
        IF EXISTS (
            SELECT receipt_id
            FROM payment_transaction
            WHERE payment_status = TRUE
            GROUP BY receipt_id
            HAVING COUNT(*) > 1
        ) THEN
            RAISE EXCEPTION
                'Cannot enforce payment uniqueness: duplicate completed payments require review';
        END IF;
    END
$$;

CREATE UNIQUE INDEX uq_payment_transaction_completed_receipt
    ON payment_transaction (receipt_id)
    WHERE payment_status = TRUE;


-- Disable unsupported channels without deleting payment history.
UPDATE payment_method
SET payment_method_status = FALSE
WHERE method_name::text NOT IN ('CreditCard', 'PromptPay', 'BankTransfer');

-- Only the three documented channels may be enabled.
ALTER TABLE payment_method
    ADD CONSTRAINT chk_payment_method_enabled_channel
        CHECK (
            payment_method_status = FALSE
            OR method_name::text IN ('CreditCard', 'PromptPay', 'BankTransfer')
        );