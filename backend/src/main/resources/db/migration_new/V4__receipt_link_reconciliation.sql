ALTER TABLE sales_transaction
    ADD COLUMN receipt_link_status VARCHAR(20) NOT NULL DEFAULT 'VERIFIED'
        CHECK (receipt_link_status IN ('VERIFIED', 'REVIEW_REQUIRED'));

CREATE TABLE sales_transaction_receipt_reconciliation (
    reconciliation_id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES sales_transaction (transaction_id),
    candidate_receipt_id BIGINT NULL REFERENCES receipt (receipt_id),
    reason_code VARCHAR(40) NOT NULL,
    source_snapshot JSONB NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT current_timestamp
);

-- V3's ID + patient + branch match is only a candidate: the same patient can
-- make multiple purchases and IDs can coincide. Preserve the row and candidate
-- for review; never turn that coincidence into an authoritative receipt link.
INSERT INTO sales_transaction_receipt_reconciliation (
    transaction_id, candidate_receipt_id, reason_code, source_snapshot
)
SELECT st.transaction_id,
       st.receipt_id,
       CASE
           WHEN st.receipt_id = st.transaction_id AND st.course_id IS NULL
               THEN 'LEGACY_ID_COINCIDENCE'
           WHEN st.receipt_id IS NULL AND c.receipt_id IS NULL
               THEN 'UNMATCHED_LEGACY_TRANSACTION'
           WHEN c.receipt_id IS NOT NULL AND st.receipt_id IS NOT NULL
                AND c.receipt_id <> st.receipt_id
               THEN 'COURSE_RECEIPT_CONFLICT'
       END,
       to_jsonb(st)
FROM sales_transaction st
LEFT JOIN course c ON c.course_id = st.course_id
WHERE (st.receipt_id = st.transaction_id AND st.course_id IS NULL)
   OR (st.receipt_id IS NULL AND c.receipt_id IS NULL)
   OR (c.receipt_id IS NOT NULL AND st.receipt_id IS NOT NULL
       AND c.receipt_id <> st.receipt_id);

UPDATE sales_transaction st
SET receipt_link_status = 'REVIEW_REQUIRED'
WHERE (st.receipt_id = st.transaction_id AND st.course_id IS NULL)
   OR (st.receipt_id IS NULL AND NOT EXISTS (
       SELECT 1 FROM course c
       WHERE c.course_id = st.course_id AND c.receipt_id IS NOT NULL
   ))
   OR EXISTS (
       SELECT 1 FROM course c
       WHERE c.course_id = st.course_id
         AND c.receipt_id IS NOT NULL
         AND st.receipt_id IS NOT NULL
         AND c.receipt_id <> st.receipt_id
   );
