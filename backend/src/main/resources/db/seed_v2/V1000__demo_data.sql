-- Development / demo data for the v2 schema. Never loaded in production: the location is only
-- added when APP_V2_FLYWAY_EXTRA_LOCATIONS=,classpath:db/seed_v2 (see application-v2.properties).
-- Demo logins: manager / manager123, therapist / therapist123, therapist2 / therapist123.
INSERT INTO app_user (username, password_hash, user_status, last_login_at, user_create_by)
SELECT a.username, a.password_hash, TRUE, CURRENT_TIMESTAMP, s.user_id
FROM app_user s
CROSS JOIN (VALUES
    ('manager', '$2a$10$26pLsRLyoUN2ft4i72Scr.hEVVCFljFK6ao8aFyKHRupJMP5LilKm'),
    ('therapist', '$2a$10$ni3Y/yJ714sLk21b9s5wH.qWjDfBfHYeig2I6kEvxV0ud8Ch1.s9u'),
    ('therapist2', '$2a$10$ni3Y/yJ714sLk21b9s5wH.qWjDfBfHYeig2I6kEvxV0ud8Ch1.s9u')
) AS a(username, password_hash)
WHERE s.username = 'system';

INSERT INTO branch (branch_code, branch_name, address, phone, branch_create_by)
SELECT b.branch_code, b.branch_name, b.address, b.phone, u.user_id
FROM app_user u
CROSS JOIN (VALUES
    ('01', 'สาขาพระราม 9', 'สาขาพระราม 9 (ที่อยู่ตัวอย่าง)', '02-000-0001'),
    ('02', 'สาขาแบริ่ง', 'สาขาแบริ่ง (ที่อยู่ตัวอย่าง)', '02-000-0002')
) AS b(branch_code, branch_name, address, phone)
WHERE u.username = 'system';

INSERT INTO staff (staff_code, staff_fname, staff_lname, position, branch_id, hire_date, staff_create_by)
SELECT s.staff_code, s.staff_fname, s.staff_lname, s.staff_position::position_enum, b.branch_id,
       DATE '2025-01-01', u.user_id
FROM app_user u
CROSS JOIN (VALUES
    ('MGR-001', 'Manager', 'Demo', 'Manager', '01'),
    ('THP-001', 'Therapist', 'Demo', 'Therapist', '01'),
    ('THP-002', 'Therapist', 'Two', 'Therapist', '02')
) AS s(staff_code, staff_fname, staff_lname, staff_position, branch_code)
JOIN branch b ON b.branch_code = s.branch_code
WHERE u.username = 'system';

UPDATE app_user SET staff_id = (SELECT staff_id FROM staff WHERE staff_code = 'MGR-001') WHERE username = 'manager';
UPDATE app_user SET staff_id = (SELECT staff_id FROM staff WHERE staff_code = 'THP-001') WHERE username = 'therapist';
UPDATE app_user SET staff_id = (SELECT staff_id FROM staff WHERE staff_code = 'THP-002') WHERE username = 'therapist2';

INSERT INTO user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM app_user u
JOIN role r ON r.role_name = (CASE WHEN u.username = 'manager' THEN 'Manager' ELSE 'Therapist' END)::role_name_enum
WHERE u.username IN ('manager', 'therapist', 'therapist2');

INSERT INTO user_branch_access (user_id, branch_id)
SELECT u.user_id, b.branch_id
FROM app_user u
CROSS JOIN branch b
WHERE u.username IN ('manager', 'therapist', 'therapist2');

INSERT INTO room (branch_id, room_name, room_type, room_create_by)
SELECT b.branch_id, r.room_name, 'Treatment', u.user_id
FROM branch b
CROSS JOIN (VALUES ('ห้องกายภาพ 1'), ('ห้องกายภาพ 2')) AS r(room_name)
CROSS JOIN app_user u
WHERE u.username = 'system';

INSERT INTO service (service_type, service_name, service_price, service_code, service_create_by,
                     default_total_visits, default_bonus_visits, default_validity_value, default_validity_unit)
SELECT v.service_type::service_type_enum, v.service_name, v.service_price, v.service_code, u.user_id,
       v.total_visits, v.bonus_visits, v.validity_days, CASE WHEN v.validity_days IS NULL THEN NULL ELSE 'Day'::validity_unit_enum END
FROM app_user u
CROSS JOIN (VALUES
    ('SingleVisit', 'กายภาพบำบัดทั่วไป 30 นาที', 800.00, 'PHYSIO_30', NULL::int, NULL::int, NULL::int),
    ('SingleVisit', 'กายภาพบำบัดทั่วไป 60 นาที', 1200.00, 'PHYSIO_60', NULL::int, NULL::int, NULL::int),
    ('Course', 'คอร์สกายภาพ 10 ครั้ง', 10000.00, 'COURSE_10', 10, 0, 365),
    ('Course', 'คอร์สกายภาพ 5 ครั้ง', 5500.00, 'COURSE_5', 5, 1, 180)
) AS v(service_type, service_name, service_price, service_code, total_visits, bonus_visits, validity_days)
WHERE u.username = 'system';

-- Tier table of the Commission_System_Requirement_Dev examples (rates by monthly sales).
INSERT INTO commission_scheme (scheme_name, scheme_version, permission_id, effective_from, scheme_create_by)
SELECT 'Default Tier Scheme', 1, p.permission_id, DATE '2026-01-01', u.user_id
FROM permission p, app_user u
WHERE p.permission_name = 'COMMISSION_SCHEME_MANAGE' AND u.username = 'system';

INSERT INTO commission_tier (commission_scheme_id, permission_id, tier_order, minimum_monthly_sales,
                             maximum_monthly_sales, commission_rate, effective_from, tier_create_by)
SELECT cs.commission_scheme_id, cs.permission_id, t.tier_order, t.lo, t.hi, t.rate, cs.effective_from,
       cs.scheme_create_by
FROM commission_scheme cs
CROSS JOIN (VALUES
    (1, 0.00, 59999.00, 0.05),
    (2, 60000.00, 69999.00, 0.06),
    (3, 70000.00, 79999.00, 0.07),
    (4, 80000.00, 89999.00, 0.08),
    (5, 90000.00, NULL::numeric, 0.10)
) AS t(tier_order, lo, hi, rate)
WHERE cs.scheme_name = 'Default Tier Scheme';

-- Default treatment fee for a substitute PT (doc example: fixed 45 THB).
INSERT INTO treatment_fee_rule (permission_id, fee_type, fee_value, effective_from, treatment_fee_rule_create_by)
SELECT p.permission_id, 'Fixed'::fee_type_enum, 45, DATE '2026-01-01', u.user_id
FROM permission p, app_user u
WHERE p.permission_name = 'COMMISSION_SCHEME_MANAGE' AND u.username = 'system';

-- Sample patients (HN = YY + branch + MM + running number) for the first branch.
INSERT INTO patient (patient_hn, customer_type, title, patient_fname, patient_lname, gender, phone, national_id,
                     branch_id, registered_by)
SELECT to_char(now(), 'YY') || '01' || to_char(now(), 'MM') || lpad(v.running_no::text, 4, '0'),
       'Thai'::customer_type_enum, v.title::title_enum, v.fname, v.lname, v.gender::gender_enum, v.phone,
       v.national_id, b.branch_id, u.user_id
FROM branch b
JOIN app_user u ON u.username = 'manager'
CROSS JOIN (VALUES
    (1, 'นาย', 'สมชาย', 'ใจดี', 'Male', '0811111111', '1100000000101'),
    (2, 'นางสาว', 'สมหญิง', 'รักสุข', 'Female', '0822222222', '1100000000102'),
    (3, 'นาง', 'มานี', 'มีสุข', 'Female', '0833333333', '1100000000103')
) AS v(running_no, title, fname, lname, gender, phone, national_id)
WHERE b.branch_code = '01';

INSERT INTO hn_sequence (branch_id, year_month, last_running_no)
SELECT branch_id, to_char(now(), 'YYMM'), 3
FROM branch
WHERE branch_code = '01';
