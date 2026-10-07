ALTER TABLE sales_transaction
    ADD COLUMN receipt_id BIGINT;

ALTER TABLE sales_transaction
    ADD CONSTRAINT fk_sales_transaction_receipt
        FOREIGN KEY (receipt_id) REFERENCES receipt (receipt_id);

CREATE INDEX idx_sales_transaction_receipt_id
    ON sales_transaction (receipt_id);

-- Preserve the explicit course-to-receipt relationship for historical course sales.
UPDATE sales_transaction st
SET receipt_id = c.receipt_id
FROM course c
WHERE st.course_id = c.course_id
  AND st.receipt_id IS NULL;

-- Older single-visit rows were looked up by matching transaction and receipt IDs.
-- Keep that legacy association only when patient and branch also agree.
UPDATE sales_transaction st
SET receipt_id = r.receipt_id
FROM receipt r
WHERE st.receipt_id IS NULL
  AND st.transaction_id = r.receipt_id
  AND st.patient_id = r.patient_id
  AND st.branch_id = r.branch_id;
