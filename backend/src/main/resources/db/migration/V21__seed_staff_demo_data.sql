-- Development/demo data only. Every row is identifiable by the demo-* prefix.
-- The migration is idempotent so restarting the local stack does not duplicate data.

INSERT INTO patients (
    hn, branch_id, username, email, password_hash, title, full_name, birth_date, gender,
    phone, identity_document_type, identity_number, address, drug_allergy, food_allergy,
    chronic_disease, emergency_contact_name, emergency_contact_relationship,
    emergency_contact_phone, privacy_consent, active
)
SELECT
    '07' || lpad((700000000 + n)::text, 10, '0'),
    (SELECT id FROM branches WHERE code = 'RP'),
    'demo-patient-' || n,
    'demo-patient-' || n || '@clinic.local',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoO1cVhX4j7P6cQ0Q8Z9f3X7mX8Qw8W7Qe',
    CASE WHEN n % 2 = 0 THEN 'นาย' ELSE 'นางสาว' END,
    CASE WHEN n % 2 = 0 THEN 'ผู้ป่วยสาธิต ชาย ' || n ELSE 'ผู้ป่วยสาธิต หญิง ' || n END,
    DATE '1980-01-01' + (n % 9000),
    CASE WHEN n % 2 = 0 THEN 'ชาย' ELSE 'หญิง' END,
    '089' || lpad(n::text, 7, '0'), 'NATIONAL_ID',
    'demo-id-' || n, 'ที่อยู่ข้อมูลจำลอง ' || n,
    false, false, null, 'ผู้ติดต่อฉุกเฉิน ' || n, 'ญาติ', '086' || lpad(n::text, 7, '0'), true, true
FROM generate_series(1, 160) AS g(n)
WHERE NOT EXISTS (SELECT 1 FROM patients p WHERE p.username = 'demo-patient-' || n);

INSERT INTO appointments (branch_id, patient_id, therapist_id, service_id, start_at, end_at, status, note)
SELECT
    (SELECT id FROM branches WHERE code = 'RP'),
    p.id,
    t.id,
    s.id,
    (CURRENT_DATE + ((g.n - 1) % 60) * INTERVAL '1 day' + (9 + ((g.n - 1) % 6)) * INTERVAL '1 hour') AT TIME ZONE 'Asia/Bangkok',
    (CURRENT_DATE + ((g.n - 1) % 60) * INTERVAL '1 day' + (9 + ((g.n - 1) % 6)) * INTERVAL '1 hour' + INTERVAL '30 minutes') AT TIME ZONE 'Asia/Bangkok',
    CASE g.n % 4 WHEN 0 THEN 'ARRIVED' WHEN 1 THEN 'CONFIRMED' WHEN 2 THEN 'COMPLETED' ELSE 'RESCHEDULED' END,
    'ข้อมูลนัดหมายจำลอง ' || g.n
FROM generate_series(1, 60) AS g(n)
JOIN patients p ON p.username = 'demo-patient-' || g.n
JOIN therapists t ON t.code = CASE WHEN g.n % 2 = 0 THEN 'RP-T02' ELSE 'RP-T01' END
JOIN services s ON s.code = CASE WHEN g.n % 3 = 0 THEN 'ASSESSMENT' ELSE 'PHYSIO_30' END
WHERE NOT EXISTS (SELECT 1 FROM appointments a WHERE a.note = 'ข้อมูลนัดหมายจำลอง ' || g.n);

INSERT INTO sales (receipt_no, patient_id, branch_id, salesperson_staff_id, total, status, note, sold_at)
SELECT
    'DEMO-' || lpad(g.n::text, 6, '0'), p.id, b.id, st.id, s.price, 'COMPLETED', 'ธุรกรรมจำลอง', CURRENT_TIMESTAMP - g.n * INTERVAL '1 day'
FROM generate_series(1, 40) AS g(n)
JOIN patients p ON p.username = 'demo-patient-' || g.n
JOIN branches b ON b.code = 'RP'
JOIN services s ON s.code = CASE WHEN g.n % 3 = 0 THEN 'ASSESSMENT' ELSE 'PHYSIO_30' END
JOIN staff st ON st.employee_code = 'THP-001'
WHERE NOT EXISTS (SELECT 1 FROM sales x WHERE x.receipt_no = 'DEMO-' || lpad(g.n::text, 6, '0'));

INSERT INTO sale_items (sale_id, item_type, service_id, description, unit_price, quantity, line_total)
SELECT x.id, CASE WHEN s.code = 'ASSESSMENT' THEN 'ASSESSMENT' ELSE 'SINGLE_VISIT' END,
       s.id, s.name, s.price, 1, s.price
FROM sales x
JOIN patients p ON p.id = x.patient_id
JOIN services s ON s.code = CASE WHEN right(x.receipt_no, 1)::int % 3 = 0 THEN 'ASSESSMENT' ELSE 'PHYSIO_30' END
WHERE x.receipt_no LIKE 'DEMO-%'
  AND NOT EXISTS (SELECT 1 FROM sale_items i WHERE i.sale_id = x.id);

INSERT INTO sale_payments (sale_id, payment_method_id, payment_method_code, type, amount)
SELECT x.id, pm.id, pm.code, 'PAYMENT', x.total
FROM sales x
JOIN payment_methods pm ON pm.code = 'CASH'
WHERE x.receipt_no LIKE 'DEMO-%'
  AND NOT EXISTS (SELECT 1 FROM sale_payments sp WHERE sp.sale_id = x.id);
