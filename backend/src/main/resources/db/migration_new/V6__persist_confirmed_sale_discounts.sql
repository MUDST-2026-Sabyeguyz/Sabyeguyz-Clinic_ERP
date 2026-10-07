-- Preserve sale price, line discount, bill discount, amount due and the
-- commissionable transaction amount independently. Existing transactions
-- have no discounts; their amount remains the gross/commission base.
ALTER TABLE sales_transaction
    ADD COLUMN gross_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN line_discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

UPDATE sales_transaction
SET gross_amount = amount,
    line_discount_amount = 0;

ALTER TABLE sales_transaction
    ADD CONSTRAINT chk_sales_transaction_gross_amount CHECK (gross_amount >= 0),
    ADD CONSTRAINT chk_sales_transaction_line_discount CHECK (line_discount_amount >= 0);

ALTER TABLE receipt
    ADD COLUMN gross_total NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN line_discount_total NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN bill_discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN net_due NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_receipt_discount_totals CHECK (
        gross_total >= 0 AND line_discount_total >= 0
        AND bill_discount_amount >= 0 AND net_due >= 0
    );

UPDATE receipt r
SET gross_total = totals.gross_total,
    line_discount_total = 0,
    bill_discount_amount = 0,
    net_due = totals.gross_total
FROM (
    SELECT coalesce(st.receipt_id, c.receipt_id) AS receipt_id,
           coalesce(sum(st.gross_amount), 0) AS gross_total
    FROM sales_transaction st
    LEFT JOIN course c ON c.course_id = st.course_id
    WHERE st.receipt_link_status = 'VERIFIED'
      AND coalesce(st.receipt_id, c.receipt_id) IS NOT NULL
    GROUP BY coalesce(st.receipt_id, c.receipt_id)
) totals
WHERE r.receipt_id = totals.receipt_id;
