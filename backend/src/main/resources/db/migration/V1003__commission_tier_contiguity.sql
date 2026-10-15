-- Commission tiers are two-decimal ranges, inclusive at both ends, with no gap: the tier after a maximum of 50,000.00
-- starts at 50,000.01 (Data Dictionary: Commission_tier). The demo tier table was written in whole baht (59,999 then
-- 60,000), which left 59,999.01-59,999.99 in no tier now that sales carry satang. Close every such gap by moving the
-- maximum to one satang below the next tier's minimum. Rates and minimums are untouched.
UPDATE commission_tier t
SET maximum_monthly_sales = n.next_minimum - 0.01
FROM (
    SELECT tier_id,
           LEAD(minimum_monthly_sales) OVER (PARTITION BY commission_scheme_id ORDER BY tier_order) AS next_minimum
    FROM commission_tier
) n
WHERE t.tier_id = n.tier_id
  AND n.next_minimum IS NOT NULL
  AND t.maximum_monthly_sales IS NOT NULL
  AND t.maximum_monthly_sales <> n.next_minimum - 0.01
  AND n.next_minimum - 0.01 >= t.minimum_monthly_sales;
