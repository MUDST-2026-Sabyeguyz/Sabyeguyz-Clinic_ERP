-- SaleStatus supports PAID/VOIDED; normalize the earlier demo seed value.
UPDATE sales SET status = 'PAID' WHERE receipt_no LIKE 'DEMO-%' AND status = 'COMPLETED';
