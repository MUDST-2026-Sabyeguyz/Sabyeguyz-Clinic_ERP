-- Synthetic patients and upcoming appointments for the Bearing demo branch.
-- This migration is loaded only with the isolated legacy demo seed.

DO $$
DECLARE
    v_manager BIGINT;
    v_branch BIGINT;
    v_staff BIGINT;
BEGIN
    SELECT user_id INTO STRICT v_manager FROM app_user WHERE username = 'manager';
    SELECT branch_id INTO STRICT v_branch FROM branch WHERE branch_code = '02';
    SELECT staff_id INTO STRICT v_staff FROM staff WHERE staff_code = 'THP-002' AND branch_id = v_branch;

    WITH demo(running_no, title, first_name, last_name, gender, phone, national_id, email) AS (
        VALUES
            (101, 'นาย', 'ธนกร', 'แบริ่ง', 'Male', '0800000201', '1999900029001', 'demo.bearing.01@example.invalid'),
            (102, 'นางสาว', 'พิมพ์ชนก', 'สมมติ', 'Female', '0800000202', '1999900029002', 'demo.bearing.02@example.invalid'),
            (103, 'นาย', 'กิตติพงษ์', 'ตัวอย่าง', 'Male', '0800000203', '1999900029003', 'demo.bearing.03@example.invalid'),
            (104, 'นาง', 'สุภาวดี', 'จำลอง', 'Female', '0800000204', '1999900029004', 'demo.bearing.04@example.invalid'),
            (105, 'นาย', 'ณัฐวุฒิ', 'ทดสอบ', 'Male', '0800000205', '1999900029005', 'demo.bearing.05@example.invalid')
    ), prepared AS (
        SELECT to_char(CURRENT_DATE, 'YY') || '02' || to_char(CURRENT_DATE, 'MM') || lpad(running_no::text, 4, '0') AS hn,
               title, first_name, last_name, gender, phone, national_id, email
        FROM demo
    )
    INSERT INTO patient (patient_hn, customer_type, title, patient_fname, patient_lname, gender,
                         phone, national_id, branch_id, registered_at, address, registered_by, email)
    SELECT p.hn, 'Thai', p.title::title_enum, p.first_name, p.last_name, p.gender::gender_enum,
           p.phone, p.national_id, v_branch, CURRENT_TIMESTAMP, 'ข้อมูลจำลอง สาขาแบริ่ง', v_manager, p.email
    FROM prepared p
    WHERE NOT EXISTS (SELECT 1 FROM patient existing WHERE existing.patient_hn = p.hn)
    ON CONFLICT (patient_hn) DO NOTHING;

    INSERT INTO hn_sequence (branch_id, year_month, last_running_no)
    VALUES (v_branch, to_char(CURRENT_DATE, 'YYMM'), 105)
    ON CONFLICT (branch_id, year_month) DO UPDATE
        SET last_running_no = GREATEST(hn_sequence.last_running_no, EXCLUDED.last_running_no);

    WITH demo(running_no, day_offset, appointment_time, service_name, room_no, status) AS (
        VALUES
            (101, 1, TIME '09:00', 'กายภาพบำบัดทั่วไป 30 นาที', 1, 'Confirmed'::appointment_status_enum),
            (102, 1, TIME '10:00', 'กายภาพบำบัดทั่วไป 30 นาที', 2, 'Confirmed'::appointment_status_enum),
            (103, 2, TIME '09:00', 'กายภาพบำบัดทั่วไป 60 นาที', 1, 'Unconfirmed'::appointment_status_enum),
            (104, 2, TIME '11:00', 'ประเมินอาการครั้งแรก', 2, 'Confirmed'::appointment_status_enum)
    ), prepared AS (
        SELECT to_char(CURRENT_DATE, 'YY') || '02' || to_char(CURRENT_DATE, 'MM') || lpad(running_no::text, 4, '0') AS hn,
               CURRENT_DATE + day_offset AS appointment_date, appointment_time, service_name, room_no, status
        FROM demo
    )
    INSERT INTO appointment (patient_id, branch_id, staff_id, service_id, appointment_status,
                             appointment_date, appointment_time, appointment_create_by,
                             appointment_patient_type, requested_service_type, notes, room_id,
                             appointment_end_at)
    SELECT p.patient_id, v_branch, v_staff, s.service_id, d.status, d.appointment_date,
           d.appointment_time, v_manager, 'CURRENT', 'SingleVisit',
           'ข้อมูลนัดหมายจำลอง สาขาแบริ่ง', r.room_id,
           d.appointment_date + d.appointment_time + make_interval(mins => COALESCE(s.default_appointment_minutes, 30))
    FROM prepared d
    JOIN patient p ON p.patient_hn = d.hn
    JOIN service s ON s.service_name = d.service_name AND s.service_status
    JOIN room r ON r.branch_id = v_branch AND r.room_name = ('ห้องกายภาพ ' || d.room_no::text)
    WHERE NOT EXISTS (
        SELECT 1 FROM appointment existing
        WHERE existing.patient_id = p.patient_id
          AND existing.branch_id = v_branch
          AND existing.appointment_date = d.appointment_date
          AND existing.appointment_time = d.appointment_time
    );
END $$;
