-- V3 intentionally left patient/branch-mismatched ID matches unlinked. Keep
-- that HOLD and append the coincident receipt as a review candidate only.
-- This does not establish an authoritative association or alter V3/V4 history.
INSERT INTO sales_transaction_receipt_reconciliation (
    transaction_id, candidate_receipt_id, reason_code, source_snapshot
)
SELECT st.transaction_id,
       candidate.receipt_id,
       'CROSSED_SCOPE_ID_CANDIDATE',
       to_jsonb(st) || jsonb_build_object(
           'candidateReceiptId', candidate.receipt_id,
           'candidateReason', 'transaction_id coincidence; patient/branch mismatch'
       )
FROM sales_transaction st
JOIN receipt candidate ON candidate.receipt_id = st.transaction_id
LEFT JOIN course c ON c.course_id = st.course_id
WHERE st.receipt_id IS NULL
  AND c.receipt_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM sales_transaction_receipt_reconciliation existing
      WHERE existing.transaction_id = st.transaction_id
        AND existing.reason_code = 'CROSSED_SCOPE_ID_CANDIDATE'
  );
