-- Default branches available on every fresh install, so a new manager never lands on an empty branch list.
-- Edit name/address/phone from the app (or replace this data) to match the real clinic.
-- Idempotent: existing branch codes are left untouched.
INSERT INTO branch (branch_code, branch_name, address, phone, opening_time, closing_time,
                    default_appointment_minutes, branch_create_by)
SELECT b.branch_code, b.branch_name, b.address, b.phone, TIME '09:00', TIME '20:00', 30, u.user_id
FROM app_user u
CROSS JOIN (VALUES
    ('01', 'สาขาพระราม 9', 'สาขาพระราม 9 (ที่อยู่ตัวอย่าง)', '02-000-0001'),
    ('02', 'สาขาแบริ่ง', 'สาขาแบริ่ง (ที่อยู่ตัวอย่าง)', '02-000-0002')
) AS b(branch_code, branch_name, address, phone)
WHERE u.username = 'system'
ON CONFLICT (branch_code) DO NOTHING;
