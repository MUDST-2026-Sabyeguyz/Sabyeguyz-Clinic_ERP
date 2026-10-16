-- Read-only verification of the imported application data. Run BEFORE API smoke tests add new rows.
\set ON_ERROR_STOP on
CREATE TEMP VIEW migration_checks AS
SELECT 'patients' AS metric, (SELECT count(*) FROM legacy.patients)::text AS expected,
       (SELECT count(*) FROM public.patient)::text AS actual
UNION ALL SELECT 'appointments', (SELECT count(*) FROM legacy.appointments)::text,
       (SELECT count(*) FROM public.appointment)::text
UNION ALL SELECT 'courses', (SELECT count(*) FROM legacy.course_purchases)::text,
       (SELECT count(*) FROM public.course)::text
-- Existing importer policy uses line totals when present. Original header totals are reported below,
-- not silently treated as equal or approved for release.
UNION ALL SELECT 'receipt net total (existing line-total mapping)',
       (SELECT coalesce(sum(coalesce((SELECT sum(i.line_total) FROM legacy.sale_items i WHERE i.sale_id=s.id),s.total)),0) FROM legacy.sales s)::text,
       (SELECT coalesce(sum(net_amount),0) FROM public.receipt WHERE receipt_id IN (SELECT id FROM legacy.sales))::text
UNION ALL SELECT 'payment total', (SELECT coalesce(sum(amount),0) FROM legacy.sale_payments WHERE type='PAYMENT')::text,
       (SELECT coalesce(sum(amount),0) FROM public.payment_transaction)::text
UNION ALL SELECT 'refund total', (SELECT coalesce(sum(abs(amount)),0) FROM legacy.sale_payments WHERE type='REFUND')::text,
       (SELECT coalesce(sum(refund_amount),0) FROM public.refund_transaction WHERE refund_status='Completed')::text
UNION ALL SELECT 'course ledger balance', (SELECT coalesce(sum(quantity),0) FROM legacy.course_movements)::text,
       (SELECT coalesce(sum(qty_change),0) FROM public.course_ledger)::text
UNION ALL SELECT 'course expiry mismatches', '0',
       (SELECT count(*) FROM legacy.course_purchases p LEFT JOIN public.course c ON c.course_id=p.id
        WHERE c.course_id IS NULL OR c.expiry_date IS DISTINCT FROM (p.expires_at AT TIME ZONE 'Asia/Bangkok')::date)::text
UNION ALL SELECT 'void status mismatches', '0',
       (SELECT count(*) FROM legacy.sales s LEFT JOIN public.receipt r ON r.receipt_id=s.id
        WHERE r.receipt_id IS NULL OR (s.status='VOIDED') IS DISTINCT FROM (r.receipt_status='Voided'))::text
UNION ALL SELECT 'duplicate patient HNs', '0',
       (SELECT count(*) FROM (SELECT patient_hn FROM public.patient GROUP BY patient_hn HAVING count(*)>1) x)::text
UNION ALL SELECT 'duplicate branch receipt numbers', '0',
       (SELECT count(*) FROM (SELECT branch_id,receipt_no FROM public.receipt GROUP BY branch_id,receipt_no HAVING count(*)>1) x)::text
UNION ALL SELECT 'course remaining mismatches', '0',
       (SELECT count(*) FROM public.course c WHERE c.remaining_visits IS DISTINCT FROM
          (SELECT coalesce(sum(l.qty_change),0)::integer FROM public.course_ledger l WHERE l.course_id=c.course_id))::text
UNION ALL SELECT 'entitlement remaining mismatches', '0',
       (SELECT count(*) FROM public.course_entitlement e WHERE e.remaining_visits IS DISTINCT FROM
          (SELECT coalesce(sum(l.qty_change),0)::integer FROM public.course_ledger l WHERE l.course_id=e.course_id AND l.patient_id=e.patient_id))::text;

DO $$
DECLARE r record; predicates text; nonnulls text; orphans bigint;
BEGIN
    FOR r IN SELECT * FROM migration_checks LOOP
        IF r.expected::numeric <> r.actual::numeric THEN
            RAISE EXCEPTION 'reconciliation failed: %, expected %, actual %', r.metric, r.expected, r.actual;
        END IF;
    END LOOP;
    -- Scan every public FK, including composite ones; do not rely only on constraint metadata.
    FOR r IN SELECT c.* FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
             JOIN pg_namespace n ON n.oid=t.relnamespace WHERE c.contype='f' AND n.nspname='public' LOOP
        SELECT string_agg(format('child.%I = parent.%I', a.attname, b.attname), ' AND ' ORDER BY k.ord),
               string_agg(format('child.%I IS NOT NULL', a.attname), ' AND ' ORDER BY k.ord)
        INTO predicates, nonnulls FROM unnest(r.conkey,r.confkey) WITH ORDINALITY k(src,dst,ord)
        JOIN pg_attribute a ON a.attrelid=r.conrelid AND a.attnum=k.src
        JOIN pg_attribute b ON b.attrelid=r.confrelid AND b.attnum=k.dst;
        EXECUTE format('SELECT count(*) FROM %s child WHERE %s AND NOT EXISTS (SELECT 1 FROM %s parent WHERE %s)',
                       r.conrelid::regclass, nonnulls, r.confrelid::regclass, predicates) INTO orphans;
        IF orphans<>0 THEN RAISE EXCEPTION 'reconciliation failed: FK % has % orphans',r.conname,orphans; END IF;
    END LOOP;
END $$;

-- Exclude generated audit timestamps/password hashes; include identities, financials and per-course state.
SELECT jsonb_build_object(
  'checks', (SELECT jsonb_agg(to_jsonb(c) ORDER BY metric) FROM migration_checks c),
  'sourceReceiptHeaderTotal', (SELECT coalesce(sum(total),0) FROM legacy.sales),
  'receiptHeaderDifferences', (SELECT coalesce(jsonb_agg(jsonb_build_array(s.id,s.receipt_no,s.total,r.net_amount) ORDER BY s.id),'[]'::jsonb)
     FROM legacy.sales s JOIN public.receipt r ON r.receipt_id=s.id WHERE s.total<>r.net_amount),
  'patients', (SELECT jsonb_agg(jsonb_build_array(patient_id,patient_hn) ORDER BY patient_id) FROM public.patient),
  'receipts', (SELECT jsonb_agg(jsonb_build_array(receipt_id,branch_id,receipt_no,net_amount,receipt_status) ORDER BY receipt_id) FROM public.receipt),
  'courses', (SELECT jsonb_agg(jsonb_build_array(course_id,expiry_date,remaining_visits) ORDER BY course_id) FROM public.course),
  'issues', (SELECT jsonb_agg(jsonb_build_array(area,old_ref,issue,action) ORDER BY area,old_ref,issue,action) FROM legacy.migration_issue)
);
