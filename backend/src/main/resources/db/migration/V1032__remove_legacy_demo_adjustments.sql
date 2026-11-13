-- Remove only the obsolete demo adjustment examples. Keep real historical adjustments.
DELETE FROM adjustment_application
WHERE adjustment_id IN (
    SELECT adjustment_id FROM staff_pay_adjustment
    WHERE automatic = FALSE
      AND (reason LIKE 'DEMO adjustment %' OR reason = 'DEMO: cancelled'
           OR reason = 'ปรับย้อนหลัง: ค่ามือเดือน ก.ย. คำนวณขาด')
);

DELETE FROM staff_pay_adjustment
WHERE automatic = FALSE
  AND (reason LIKE 'DEMO adjustment %' OR reason = 'DEMO: cancelled'
       OR reason = 'ปรับย้อนหลัง: ค่ามือเดือน ก.ย. คำนวณขาด');
