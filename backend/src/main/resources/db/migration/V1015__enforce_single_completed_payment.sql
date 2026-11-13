-- Keep historical multi-payment receipts; enforce one completed payment on new receipts.
DO $$
DECLARE legacy_receipt_id BIGINT;
BEGIN
  SELECT COALESCE(MAX(receipt_id), 0) INTO legacy_receipt_id FROM receipt;
  EXECUTE format('CREATE UNIQUE INDEX uq_payment_transaction_completed_new_receipt ON payment_transaction (receipt_id) WHERE payment_status = TRUE AND receipt_id > %s', legacy_receipt_id);
END $$;

UPDATE payment_method SET payment_method_status = FALSE
WHERE method_name::text NOT IN ('CreditCard', 'PromptPay', 'BankTransfer');
ALTER TABLE payment_method ADD CONSTRAINT chk_payment_method_enabled_channel
  CHECK (payment_method_status = FALSE OR method_name::text IN ('CreditCard', 'PromptPay', 'BankTransfer'));
