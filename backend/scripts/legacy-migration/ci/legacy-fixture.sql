-- Extra data in the OLD schema, on top of the demo seed of the old migrations (V20-V25),
-- so the migration script is exercised on every kind of row. Used only by the CI workflow.

-- patients: a valid HN + national id, a foreigner with passport, blood group and master-data text
INSERT INTO patients (hn, branch_id, username, email, password_hash, title, full_name, birth_date, gender, phone,
                      identity_document_type, identity_number, address, emergency_contact_name,
                      emergency_contact_relationship, emergency_contact_phone, privacy_consent,
                      nickname, blood_group, nationality, customer_type, customer_source, insurance_company)
VALUES
 ('2601010001', (SELECT branch_id FROM branch WHERE branch_code = '01'), 'fx-thai', 'fx-thai@x.local', 'x', 'นาย', 'สมชาย ใจดี',
  DATE '1990-05-01', 'ชาย', '0811111111', 'NATIONAL_ID', '1103700000011', 'กรุงเทพฯ', 'แม่', 'ญาติ', '0822222222', TRUE,
  'ชาย', 'O', 'ไทย', 'VIP', 'Facebook', 'บริษัทประกัน เอ'),
 ('2601010002', (SELECT branch_id FROM branch WHERE branch_code = '01'), 'fx-foreign', 'fx-foreign@x.local', 'x', 'Mr', 'John Smith',
  DATE '1985-02-03', 'Male', '+4412345678', 'PASSPORT', 'P1234567', 'London', 'Jane', 'wife', '+4400000000', TRUE,
  NULL, 'AB+', 'British', NULL, NULL, NULL),
 ('2601010003', (SELECT branch_id FROM branch WHERE branch_code = '01'), 'fx-child', 'fx-child@x.local', 'x', 'เด็กหญิง', 'น้องฟ้า ใจดี',
  DATE '2018-07-07', 'หญิง', '0833333333', 'NATIONAL_ID', '1103700000029', 'กรุงเทพฯ', 'แม่', 'ญาติ', '0822222222', TRUE,
  NULL, NULL, NULL, NULL, NULL, NULL);

INSERT INTO master_data (category, code, name) VALUES ('Referral Channel', 'LINE', 'Line OA'), ('Hobby', 'RUN', 'วิ่ง');
INSERT INTO clinic_resources (branch_id, code, name, type, status)
VALUES ((SELECT branch_id FROM branch WHERE branch_code = '01'), 'ROOM-1', 'ห้องกายภาพ 1', 'ROOM', 'AVAILABLE');

-- course masters (one with a code too long for the new service_code)
INSERT INTO courses (code, name, price, sessions, bonus_sessions, validity_days)
VALUES ('CRS10', 'คอร์ส 10 ครั้ง', 10000, 10, 1, 365),
       ('A-VERY-LONG-COURSE-CODE-OVER-20', 'คอร์สโค้ดยาว', 5000, 5, 0, 180);

-- a treatment fee rule and a locked commission scheme usage
INSERT INTO treatment_fee_rules (employee_id, fee_type, fee_value, effective_from)
VALUES ((SELECT id FROM staff WHERE employee_code = 'THP-001'), 'FIXED', 45, DATE '2026-01-01');

-- purchase 1: sold on a receipt, locked commission, two usages (one reversed), a transfer out
INSERT INTO sales (receipt_no, patient_id, branch_id, salesperson_staff_id, total, status, note, sold_at)
VALUES ('RC2601010001', (SELECT id FROM patients WHERE username = 'fx-thai'), (SELECT branch_id FROM branch WHERE branch_code = '01'),
        (SELECT id FROM staff WHERE employee_code = 'MGR-001'), 10000, 'PAID', 'course sale', TIMESTAMPTZ '2026-01-05 10:00+07');
INSERT INTO course_purchases (patient_id, course_id, branch_id, course_code, course_name, price, sessions, bonus_sessions,
        purchased_at, expires_at, status, sale_month, seller_employee_id, case_owner_employee_id, commission_status,
        commission_scheme_id, commission_scheme_version, provisional_commission_rate, locked_commission_rate,
        total_course_commission_pool, commission_allocation_per_visit, commission_generated_date)
VALUES ((SELECT id FROM patients WHERE username = 'fx-thai'), (SELECT id FROM courses WHERE code = 'CRS10'),
        (SELECT branch_id FROM branch WHERE branch_code = '01'), 'CRS10', 'คอร์ส 10 ครั้ง', 10000, 10, 1,
        TIMESTAMPTZ '2026-01-05 10:00+07', TIMESTAMPTZ '2027-01-05 10:00+07', 'ACTIVE', DATE '2026-01-01',
        (SELECT id FROM staff WHERE employee_code = 'MGR-001'), (SELECT id FROM staff WHERE employee_code = 'THP-001'),
        'LOCKED', (SELECT id FROM commission_schemes WHERE name = 'DEFAULT'), '1', 0.05, 0.05, 500, 50,
        TIMESTAMPTZ '2026-02-01 00:00+07');
INSERT INTO sale_items (sale_id, item_type, course_id, course_purchase_id, description, unit_price, quantity, line_total)
VALUES ((SELECT id FROM sales WHERE receipt_no = 'RC2601010001'), 'COURSE', (SELECT id FROM courses WHERE code = 'CRS10'),
        (SELECT id FROM course_purchases WHERE course_code = 'CRS10'), 'คอร์ส 10 ครั้ง', 10000, 1, 10000);
INSERT INTO sale_payments (sale_id, payment_method_id, payment_method_code, type, amount)
VALUES ((SELECT id FROM sales WHERE receipt_no = 'RC2601010001'), (SELECT id FROM payment_methods WHERE code = 'CASH'), 'CASH', 'PAYMENT', 6000),
       ((SELECT id FROM sales WHERE receipt_no = 'RC2601010001'), (SELECT id FROM payment_methods WHERE code = 'QR'), 'QR', 'PAYMENT', 4000);

INSERT INTO course_movements (course_purchase_id, type, quantity, reason)
VALUES ((SELECT id FROM course_purchases WHERE course_code = 'CRS10'), 'PURCHASE', 11, NULL);
INSERT INTO course_movements (course_purchase_id, type, quantity, reason, appointment_id)
VALUES ((SELECT id FROM course_purchases WHERE course_code = 'CRS10'), 'USAGE', -1, 'ครั้งที่ 1',
        (SELECT id FROM appointments WHERE note = 'ข้อมูลนัดหมายจำลอง 4'));
INSERT INTO course_movements (course_purchase_id, type, quantity, reason)
VALUES ((SELECT id FROM course_purchases WHERE course_code = 'CRS10'), 'USAGE', -1, 'ครั้งที่ 2 (ผิดคน)');
INSERT INTO course_movements (course_purchase_id, type, quantity, reason, reverses_movement_id)
VALUES ((SELECT id FROM course_purchases WHERE course_code = 'CRS10'), 'REVERSAL', 1, 'ลงผิด',
        (SELECT max(id) FROM course_movements WHERE type = 'USAGE'));
INSERT INTO commission_allocation_ledger (course_purchase_id, visit_date, case_owner_employee_id, treating_employee_id,
        gross_commission_allocation, treatment_fee_amount, owner_net_commission)
VALUES ((SELECT id FROM course_purchases WHERE course_code = 'CRS10'), DATE '2026-02-10',
        (SELECT id FROM staff WHERE employee_code = 'THP-001'), (SELECT id FROM staff WHERE employee_code = 'THP-001'), 50, 0, 50),
       ((SELECT id FROM course_purchases WHERE course_code = 'CRS10'), DATE '2026-02-11',
        (SELECT id FROM staff WHERE employee_code = 'THP-001'), (SELECT id FROM staff WHERE employee_code = 'MGR-001'), 50, 45, 5);

-- purchase 2: no sale line (made through the course API), later cancelled with the balance cleared
INSERT INTO course_purchases (patient_id, course_id, branch_id, course_code, course_name, price, sessions, bonus_sessions,
        purchased_at, expires_at, status)
VALUES ((SELECT id FROM patients WHERE username = 'fx-foreign'), (SELECT id FROM courses WHERE code LIKE 'A-VERY%'),
        (SELECT branch_id FROM branch WHERE branch_code = '01'), 'A-VERY-LONG-COURSE-CODE-OVER-20', 'คอร์สโค้ดยาว', 5000, 5, 0,
        TIMESTAMPTZ '2026-03-01 09:00+07', TIMESTAMPTZ '2026-09-01 09:00+07', 'CANCELLED');
INSERT INTO course_movements (course_purchase_id, type, quantity)
VALUES ((SELECT id FROM course_purchases WHERE course_code LIKE 'A-VERY%'), 'PURCHASE', 5);
INSERT INTO course_movements (course_purchase_id, type, quantity, reason)
VALUES ((SELECT id FROM course_purchases WHERE course_code LIKE 'A-VERY%'), 'REVERSAL', -5, 'ลูกค้าขอยกเลิก');

-- purchase 3: a transfer from purchase 1 (3 visits) into a purchase of another patient
INSERT INTO course_purchases (patient_id, course_id, branch_id, course_code, course_name, price, sessions, bonus_sessions,
        purchased_at, expires_at, status)
VALUES ((SELECT id FROM patients WHERE username = 'fx-child'), (SELECT id FROM courses WHERE code = 'CRS10'),
        (SELECT branch_id FROM branch WHERE branch_code = '01'), 'CRS10', 'คอร์ส 10 ครั้ง', 0, 3, 0,
        TIMESTAMPTZ '2026-03-10 09:00+07', TIMESTAMPTZ '2027-03-10 09:00+07', 'ACTIVE');
INSERT INTO course_movements (course_purchase_id, type, quantity, reason)
VALUES ((SELECT min(id) FROM course_purchases WHERE course_code = 'CRS10'), 'TRANSFER_OUT', -3, 'โอนให้ลูก'),
       ((SELECT max(id) FROM course_purchases WHERE course_code = 'CRS10'), 'TRANSFER_IN', 3, 'รับโอนจากแม่');
INSERT INTO course_transfers (source_purchase_id, destination_purchase_id, quantity, reason)
VALUES ((SELECT min(id) FROM course_purchases WHERE course_code = 'CRS10'),
        (SELECT max(id) FROM course_purchases WHERE course_code = 'CRS10'), 3, 'โอนให้ลูก');

-- a voided sale with its payment and a refund
INSERT INTO sales (receipt_no, patient_id, branch_id, salesperson_staff_id, total, status, note, sold_at, voided_at, void_reason)
VALUES ('RC2601010002', (SELECT id FROM patients WHERE username = 'fx-foreign'), (SELECT branch_id FROM branch WHERE branch_code = '01'),
        (SELECT id FROM staff WHERE employee_code = 'THP-001'), 700, 'VOIDED', NULL, TIMESTAMPTZ '2026-02-02 11:00+07',
        TIMESTAMPTZ '2026-02-02 12:00+07', 'บันทึกผิด');
INSERT INTO sale_items (sale_id, item_type, service_id, description, unit_price, quantity, line_total)
VALUES ((SELECT id FROM sales WHERE receipt_no = 'RC2601010002'), 'SINGLE_VISIT',
        (SELECT id FROM services WHERE code = 'PHYSIO_30'), 'กายภาพบำบัดทั่วไป 30 นาที', 700, 1, 700);
INSERT INTO sale_payments (sale_id, payment_method_id, payment_method_code, type, amount)
VALUES ((SELECT id FROM sales WHERE receipt_no = 'RC2601010002'), (SELECT id FROM payment_methods WHERE code = 'CASH'), 'CASH', 'PAYMENT', 700);
INSERT INTO sale_payments (sale_id, payment_method_id, payment_method_code, type, amount, reason, reverses_payment_id)
VALUES ((SELECT id FROM sales WHERE receipt_no = 'RC2601010002'), (SELECT id FROM payment_methods WHERE code = 'CASH'), 'CASH', 'REFUND', -700,
        'บันทึกผิด', (SELECT id FROM sale_payments WHERE sale_id = (SELECT id FROM sales WHERE receipt_no = 'RC2601010002') AND type = 'PAYMENT'));

-- treatments: one tied to an appointment, one with a service text that matches nothing
INSERT INTO treatments (patient_id, appointment_id, staff_id, service, note)
VALUES ((SELECT patient_id FROM appointments WHERE note = 'ข้อมูลนัดหมายจำลอง 4'), (SELECT id FROM appointments WHERE note = 'ข้อมูลนัดหมายจำลอง 4'),
        (SELECT id FROM staff WHERE employee_code = 'THP-001'), 'กายภาพบำบัดทั่วไป 30 นาที', 'ปวดหลัง ดีขึ้น'),
       ((SELECT id FROM patients WHERE username = 'fx-thai'), NULL, (SELECT id FROM staff WHERE employee_code = 'THP-001'),
        'บริการที่ไม่มีในรายการ', 'ให้คำแนะนำ');

INSERT INTO commission_closings (closing_month, employee_id, monthly_course_sales, commission_scheme_id, commission_scheme_version,
        calculated_commission_rate, locked_commission_rate, closing_status, closed_at, closed_by)
VALUES (DATE '2026-01-01', (SELECT id FROM staff WHERE employee_code = 'MGR-001'), 10000,
        (SELECT id FROM commission_schemes WHERE name = 'DEFAULT'), '1', 0.05, 0.05, 'CLOSED', TIMESTAMPTZ '2026-02-01 00:00+07',
        (SELECT id FROM staff WHERE employee_code = 'MGR-001'));

INSERT INTO audit_logs (actor_id, action, entity_type, entity_id)
VALUES ((SELECT id FROM staff WHERE employee_code = 'MGR-001'), 'COURSE_TRANSFER', 'course_transfers', 1);

-- These generated legacy demo rows are synthetic CI data, not patient records.
-- Give the pre-existing demo rows valid-shaped test IDs for the latest Thai constraints.
UPDATE patients SET identity_number = lpad((9000000000000 + id)::text, 13, '0')
WHERE identity_document_type = 'NATIONAL_ID'
  AND (identity_number IS NULL OR identity_number !~ '^[0-9]{13}$');
