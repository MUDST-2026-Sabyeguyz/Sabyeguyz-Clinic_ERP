-- Synthetic condition-coverage additions for the opt-in legacy demo seed.
-- Load only in an isolated demo database after V1100. No real calendar credentials are used.
-- This is deliberately outside db/migration and is never part of normal/production startup.

DO $$
DECLARE
    v_manager BIGINT;
    v_pt BIGINT;
    v_branch BIGINT;
    v_other_branch BIGINT;
    v_course BIGINT;
    v_refund_course BIGINT;
    v_refund_patient BIGINT;
    v_refund_remaining INT;
    v_course_payment BIGINT;
    v_course_payment_method BIGINT;
    v_cancel_permission BIGINT;
    v_refund_cancellation BIGINT;
    v_course_refund BIGINT;
    v_course_refund_item BIGINT;
    v_course_service BIGINT;
    v_single_service BIGINT;
    v_group BIGINT;
    v_staff_group BIGINT;
    v_permission BIGINT;
    v_patient BIGINT;
    v_connection BIGINT;
BEGIN
    SELECT user_id INTO STRICT v_manager FROM app_user WHERE username = 'manager';
    SELECT staff_id INTO STRICT v_pt FROM staff WHERE staff_code = 'RP-T01';
    SELECT branch_id INTO STRICT v_branch FROM branch WHERE branch_code = '01';
    SELECT branch_id INTO STRICT v_other_branch FROM branch WHERE branch_code = '02';
    SELECT course_id INTO STRICT v_course FROM course ORDER BY course_id LIMIT 1;
    SELECT service_id INTO STRICT v_course_service FROM service WHERE service_type = 'Course' ORDER BY service_id LIMIT 1;
    SELECT service_id INTO STRICT v_single_service FROM service WHERE service_type = 'SingleVisit' ORDER BY service_id LIMIT 1;
    SELECT permission_id INTO STRICT v_permission FROM permission ORDER BY permission_id LIMIT 1;
    SELECT patient_id INTO STRICT v_patient FROM patient ORDER BY patient_id LIMIT 1;
    SELECT permission_id INTO STRICT v_cancel_permission FROM permission WHERE permission_name='SALE_CANCEL';

    -- Reference tables are normally populated by V3; include the system author and all method/role options
    -- when this optional seed is run on a manually prepared schema.
    INSERT INTO app_user (username, password_hash, user_status, email, user_create_at, user_create_by)
    SELECT 'system', '!login-disabled', FALSE, 'system@clinic.local', CURRENT_TIMESTAMP, v_manager
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username='system');
    INSERT INTO role (role_name, role_create_by)
    SELECT r.role_name::role_name_enum, v_manager FROM (VALUES ('Manager'),('Therapist')) r(role_name)
    WHERE NOT EXISTS (SELECT 1 FROM role x WHERE x.role_name=r.role_name::role_name_enum);
    INSERT INTO permission (permission_name, module_name, permission_create_by)
    SELECT p.permission_name, 'Demo', v_manager FROM (VALUES ('DEMO_READ'),('DEMO_WRITE')) p(permission_name)
    WHERE NOT EXISTS (SELECT 1 FROM permission x WHERE x.permission_name=p.permission_name);
    INSERT INTO payment_method (method_name, payment_method_create_by)
    SELECT m.method_name::method_name_enum, v_manager FROM (VALUES ('CreditCard'),('PromptPay'),('BankTransfer')) m(method_name)
    WHERE NOT EXISTS (SELECT 1 FROM payment_method x WHERE x.method_name=m.method_name::method_name_enum);
    INSERT INTO master_data_type (type_name, type_create_by)
    SELECT t.type_name, v_manager FROM (VALUES ('Customer Group'),('Referral Channel'),('Insurance Type'),('Insurance Company')) t(type_name)
    WHERE NOT EXISTS (SELECT 1 FROM master_data_type x WHERE x.type_name=t.type_name);
    INSERT INTO master_data_value (type_id, value_name, value_status, sort_order, value_create_by)
    SELECT t.type_id, 'DEMO: disabled example', FALSE, 999, v_manager
    FROM master_data_type t WHERE t.type_name='Customer Group'
      AND NOT EXISTS (SELECT 1 FROM master_data_value x WHERE x.type_id=t.type_id AND x.value_name='DEMO: disabled example');
    INSERT INTO role_permission (role_id, permission_id)
        SELECT r.role_id, v_permission FROM role r WHERE r.role_name='Manager'
        ON CONFLICT DO NOTHING;
    INSERT INTO role_permission (role_id, permission_id)
        SELECT r.role_id, p.permission_id FROM role r CROSS JOIN permission p
        ON CONFLICT DO NOTHING;

    INSERT INTO hn_sequence (branch_id, year_month, last_running_no)
        VALUES (v_other_branch, '2610', 0) ON CONFLICT DO NOTHING;
    INSERT INTO receipt_sequence (branch_id, year_month, last_running_no)
        VALUES (v_other_branch, '2610', 0) ON CONFLICT DO NOTHING;

    -- Tables omitted by V1100 because they were described as retired, plus system settings.
    INSERT INTO service_group (group_name, service_group_create_by) VALUES ('DEMO: บริการฟื้นฟู', v_manager) RETURNING service_group_id INTO v_group;
    INSERT INTO service_group_member VALUES (v_group, v_course_service), (v_group, v_single_service);
    INSERT INTO staff_group (group_name, staff_group_create_by) VALUES ('DEMO: ทีมนักกายภาพ', v_manager) RETURNING staff_group_id INTO v_staff_group;
    INSERT INTO staff_group_member VALUES (v_staff_group, v_pt);
    INSERT INTO role_permission (role_id, permission_id)
        SELECT r.role_id, v_permission FROM role r WHERE r.role_name='Therapist'
        ON CONFLICT DO NOTHING;
    INSERT INTO system_setting (setting_key, permission_id, setting_value, description, setting_update_at, setting_update_by)
        VALUES ('DEMO_UI_THEME', v_permission, 'light', 'Synthetic setting for demo coverage', CURRENT_TIMESTAMP, v_manager);
    INSERT INTO course_sale_staff_share (course_id, staff_id, share_percent, credited_sales_amount, created_at, created_by)
        SELECT c.course_id, c.seller_staff_id, 100.00, c.course_price, CURRENT_TIMESTAMP, v_manager
        FROM course c WHERE NOT EXISTS (SELECT 1 FROM course_sale_staff_share x WHERE x.course_id=c.course_id)
          AND (SELECT count(*) FROM course_sale_staff_share x WHERE x.course_id=c.course_id)=0;

    -- V1002 removes the old staff/service/group scopes and percentage-base column; current rules are global.
    INSERT INTO treatment_fee_rule (permission_id, fee_type, fee_value, effective_from, effective_to,
        treatment_fee_rule_status, treatment_fee_rule_create_by, rule_name)
        VALUES (v_permission, 'Percentage', 7.5000, DATE '2026-01-01', NULL, TRUE, v_manager, 'DEMO: ค่ามือ 7.5%');
    INSERT INTO treatment_fee_rule (permission_id, fee_type, fee_value, effective_from, effective_to,
        treatment_fee_rule_status, treatment_fee_rule_create_by, rule_name)
        VALUES (v_permission, 'Fixed', 60.0000, DATE '2026-01-01', DATE '2026-12-31', FALSE, v_manager, 'DEMO: กฎปิดใช้งาน');

    -- Every appointment lifecycle and calendar sync outcome; all event IDs are synthetic.
    INSERT INTO appointment (patient_id, branch_id, staff_id, service_id, appointment_status, appointment_date,
        appointment_time, appointment_create_by, appointment_patient_type, contact_name, contact_phone,
        requested_service_type, calendar_sync_status, notes, room_id, appointment_end_at)
    SELECT v_patient, v_branch, v_pt, v_single_service, s.status, DATE '2026-10-16', TIME '09:00' + (s.n * INTERVAL '30 minutes'),
        v_manager, 'CURRENT', 'DEMO patient', '0800000000', 'SingleVisit', s.sync_status, 'Synthetic condition coverage',
        (SELECT room_id FROM room WHERE branch_id = v_branch ORDER BY room_id LIMIT 1),
        (DATE '2026-10-16' + TIME '09:00' + (s.n * INTERVAL '30 minutes') + INTERVAL '30 minutes')
    FROM (VALUES (0,'Unconfirmed'::appointment_status_enum,'Pending'::calendar_sync_status_enum),
                 (1,'Confirmed'::appointment_status_enum,'Synced'::calendar_sync_status_enum),
                 (2,'Arrived'::appointment_status_enum,'Failed'::calendar_sync_status_enum),
                 (3,'InService'::appointment_status_enum,'Deleted'::calendar_sync_status_enum),
                 (4,'Completed'::appointment_status_enum,NULL::calendar_sync_status_enum),
                 (5,'Cancelled'::appointment_status_enum,NULL::calendar_sync_status_enum),
                 (6,'Rescheduled'::appointment_status_enum,NULL::calendar_sync_status_enum),
                 (7,'NoShow'::appointment_status_enum,NULL::calendar_sync_status_enum)) s(n,status,sync_status);

    INSERT INTO appointment_status_history (appointment_id, from_status, to_status, changed_by, changed_at, reason)
        SELECT a.appointment_id, NULL, a.appointment_status, v_manager, CURRENT_TIMESTAMP, 'DEMO: status coverage'
        FROM appointment a
        WHERE a.appointment_date=DATE '2026-10-16' AND a.appointment_time >= TIME '09:00'
          AND a.appointment_time < TIME '13:00'
          AND NOT EXISTS (SELECT 1 FROM appointment_status_history h WHERE h.appointment_id=a.appointment_id);

    INSERT INTO appointment (patient_id, branch_id, staff_id, service_id, appointment_status, appointment_date,
        appointment_time, appointment_create_by, appointment_patient_type, contact_name, contact_phone,
        requested_service_type, notes, appointment_end_at)
        VALUES (NULL, v_branch, v_pt, v_single_service, 'Confirmed', DATE '2026-10-19', TIME '14:00', v_manager,
                'NEW', 'DEMO new patient', '0800000001', 'SingleVisit', 'Synthetic new patient case',
                TIMESTAMP '2026-10-19 14:30:00');

    -- Course extension requests in each decision state; the approved row points to V1100's extension audit.
    INSERT INTO course_extension_request (course_id, requested_patient_id, extension_value, extension_unit, reason,
        request_status, requested_by, decided_by, decided_at, decision_note, extension_id)
    SELECT v_course, v_patient, 1, 'Month', 'DEMO: extension approved', 'Approved', v_manager, v_manager,
           CURRENT_TIMESTAMP, 'DEMO approved', extension_id
    FROM course_extension WHERE course_id=v_course ORDER BY extension_id LIMIT 1;
    INSERT INTO course_extension_request (course_id, requested_patient_id, extension_value, extension_unit, reason,
        request_status, requested_by, decided_by, decided_at, decision_note)
        VALUES (v_course, v_patient, 2, 'Month', 'DEMO: rejected extension', 'Rejected', v_manager, v_manager,
                CURRENT_TIMESTAMP, 'DEMO rejected');
    INSERT INTO course_extension_request (course_id, requested_patient_id, extension_value, extension_unit, reason, requested_by)
        SELECT v_course, v_patient, 1, 'Month', 'DEMO: pending extension', v_manager
        WHERE NOT EXISTS (SELECT 1 FROM course_extension_request WHERE course_id=v_course AND request_status='Pending');

    -- The legacy demo already has an active owner and shared-member entitlement.
    INSERT INTO course_shared_member (course_id, patient_id, member_role, member_status, joined_at, ended_at)
        SELECT c.course_id, p.patient_id, 'SharedMember', FALSE, CURRENT_TIMESTAMP - INTERVAL '3 days',
               CURRENT_TIMESTAMP - INTERVAL '1 day'
        FROM course c CROSS JOIN patient p
        WHERE c.course_id=v_course AND p.patient_id > 160
          AND NOT EXISTS (SELECT 1 FROM course_shared_member x WHERE x.course_id=c.course_id AND x.patient_id=p.patient_id)
        ORDER BY p.patient_id LIMIT 1;
    INSERT INTO course_entitlement (course_id, patient_id, allocated_visits, used_visits, remaining_visits)
        SELECT course_id, patient_id, 0, 0, 0 FROM course_shared_member
        WHERE course_id=v_course AND member_status=FALSE
          AND NOT EXISTS (SELECT 1 FROM course_entitlement e WHERE e.course_id=course_shared_member.course_id AND e.patient_id=course_shared_member.patient_id);

    -- Commission reversal plus adjustment cases; do not mutate original seeded allocations.
    -- Existing commission and single-visit ledgers contain valid Allocation examples.
    -- Reversal rows require a real business action; they are intentionally left to workflow-driven scenarios.

    -- Keep the existing seed's valid approved adjustment and draft application; add a cancelled case.
    INSERT INTO staff_pay_adjustment (staff_id, original_month, applied_month, amount, course_id, reason, processed_amount,
        remaining_amount, adjustment_status, created_by)
    VALUES (v_pt, '2026-08', '2026-09', 80, v_course, 'DEMO: cancelled', 0, 80, 'Cancelled', v_manager);

    -- Both course refund paths: pending and failed (without inventing a real payment).
    -- Existing refund_transaction covers Completed; no fabricated refund is linked to a mismatched payment/cancellation.

    -- Existing seed supplies Open/Closed and Unpaid monthly examples. Don't mark paid without a balanced payout.

    -- Commission rate override examples for a course and a monthly close.
    INSERT INTO commission_rate_override_audit (permission_id, closing_id, old_rate, new_rate, changed_by, reason)
        SELECT v_permission, closing_id, 0.0500, 0.0550, v_manager, 'DEMO: monthly close rate correction'
        FROM monthly_commission_closing ORDER BY closing_id LIMIT 1;
    INSERT INTO commission_rate_override_audit (permission_id, course_id, old_rate, new_rate, changed_by, reason)
        VALUES (v_permission, v_course, 0.0500, 0.0600, v_manager, 'DEMO: course rate override');

    -- One coherent partial course refund, linked to its own sale receipt and payment; cut the matching right
    -- from both the entitlement and the course balance and record the corresponding cancellation ledger event.
    SELECT c.course_id, c.purchaser_patient_id, c.remaining_visits
      INTO STRICT v_refund_course, v_refund_patient, v_refund_remaining
      FROM course c WHERE c.course_id <> v_course AND c.remaining_visits > 0 ORDER BY c.course_id LIMIT 1;
    SELECT p.payment_id, p.payment_method_id INTO STRICT v_course_payment, v_course_payment_method
      FROM payment_transaction p JOIN course c ON c.receipt_id=p.receipt_id
      WHERE c.course_id=v_refund_course AND p.payment_status=TRUE ORDER BY p.payment_id LIMIT 1;
    INSERT INTO transaction_cancellation (permission_id, payment_id, reason_code, reason, cancelled_by, cancelled_at, cancellation_type)
      VALUES (v_cancel_permission, v_course_payment, 'PatientRequested', 'DEMO: partial course refund', v_manager, CURRENT_TIMESTAMP, 'Refund')
      RETURNING cancellation_id INTO v_refund_cancellation;
    INSERT INTO refund_transaction (cancellation_id, original_payment_id, refund_amount, payment_method_id, refund_status,
        refunded_at, refunded_by, requested_at, requested_by, approved_by, approved_at)
      VALUES (v_refund_cancellation, v_course_payment, 916.67, v_course_payment_method, 'Completed', CURRENT_TIMESTAMP,
        v_manager, CURRENT_TIMESTAMP, v_manager, v_manager, CURRENT_TIMESTAMP)
      RETURNING refund_id INTO v_course_refund;
    INSERT INTO course_refund_item (refund_id, course_id, refund_amount, cancelled_visits)
      VALUES (v_course_refund, v_refund_course, 916.67, 1)
      RETURNING course_refund_item_id INTO v_course_refund_item;
    UPDATE course_entitlement SET allocated_visits=allocated_visits-1, remaining_visits=remaining_visits-1,
        updated_at=CURRENT_TIMESTAMP WHERE course_id=v_refund_course AND patient_id=v_refund_patient AND remaining_visits > 0;
    UPDATE course SET remaining_visits=remaining_visits-1, course_update_at=CURRENT_TIMESTAMP, course_update_by=v_manager
      WHERE course_id=v_refund_course;
    INSERT INTO course_ledger (course_id, patient_id, entry_type, qty_change, balance_after, remark, ledger_create_by,
        course_refund_item_id, event_at)
      SELECT v_refund_course, v_refund_patient, 'Cancellation', -1, e.remaining_visits,
        'DEMO: partial course refund', v_manager, v_course_refund_item, CURRENT_TIMESTAMP
      FROM course_entitlement e WHERE e.course_id=v_refund_course AND e.patient_id=v_refund_patient;

    -- Google connection states are mock references only; no key/token is stored. Busy blocks include staff-specific and branch-wide.
    INSERT INTO google_calendar_connection (google_account_email, connection_status, created_by)
        VALUES ('demo-calendar-reauth@clinic.local', 'ReauthRequired', v_manager),
               ('demo-calendar-off@clinic.local', 'Disconnected', v_manager);
    INSERT INTO google_calendar_connection (google_account_email, credential_ref, connection_status, authorized_by,
        authorized_at, created_by)
        VALUES ('demo-calendar-connected@clinic.local', 'DEMO-ONLY-NO-CREDENTIAL', 'Connected', v_manager, CURRENT_TIMESTAMP, v_manager)
        RETURNING connection_id INTO v_connection;
    UPDATE branch SET google_calendar_id='demo-calendar-primary@group.calendar.google.com',
        google_connection_id=v_connection, calendar_sync_status='Synced' WHERE branch_id=v_branch;
    UPDATE branch SET google_calendar_id=NULL, google_connection_id=NULL, calendar_sync_status='Disabled' WHERE branch_id=v_other_branch;
    INSERT INTO calendar_busy_block (branch_id, external_event_id, staff_id, starts_at, ends_at, block_status, event_etag, synced_at)
        VALUES (v_branch, 'DEMO-BUSY-001', v_pt, CURRENT_TIMESTAMP + INTERVAL '2 days', CURRENT_TIMESTAMP + INTERVAL '2 days 1 hour', 'Active', 'demo-etag-1', CURRENT_TIMESTAMP),
               (v_branch, 'DEMO-BUSY-002', NULL, CURRENT_TIMESTAMP + INTERVAL '3 days', CURRENT_TIMESTAMP + INTERVAL '3 days 1 hour', 'Cancelled', NULL, CURRENT_TIMESTAMP);

END $$;
