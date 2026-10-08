-- ============================================================================
-- Moves the data of the old Clinic ERP schema into the new (v2) schema.
--
-- Layout when this runs (see docs/legacy-data-migration.md):
--   schema "legacy" : the old tables, untouched (the old "public" schema renamed)
--   schema "public" : the new empty v2 schema (migration_new V1 + V1_1 applied)
--
-- The script runs in ONE transaction: any problem rolls everything back and
-- leaves the database as it was. Nothing in "legacy" is changed or dropped, so
-- it stays as the full backup of the old data, including the fields the new
-- schema has no column for (patient emergency contacts, allergies, service
-- durations, appointment notes, old receipt numbers, ...).
--
-- Rows that needed a decision are listed in legacy.migration_issue.
-- Run with:  psql -v ON_ERROR_STOP=1 -f migrate-legacy-to-v2.sql
-- ============================================================================
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL search_path TO public;

-- ---------------------------------------------------------------------------
-- 0. Pre-flight checks
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('legacy.patients') IS NULL OR to_regclass('legacy.app_users') IS NULL THEN
        RAISE EXCEPTION 'schema "legacy" with the old tables was not found (rename the old public schema first)';
    END IF;
    IF EXISTS (SELECT 1 FROM public.patient) OR EXISTS (SELECT 1 FROM public.branch)
       OR EXISTS (SELECT 1 FROM public.staff) OR (SELECT count(*) FROM public.app_user) > 1 THEN
        RAISE EXCEPTION 'the new schema already contains data; this script only fills an empty v2 schema';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.app_user WHERE username = 'system') THEN
        RAISE EXCEPTION 'reference data (V1_1: the system user, roles, permissions) is missing in the new schema';
    END IF;
    IF EXISTS (SELECT 1 FROM legacy.branch WHERE branch_code !~ '^[0-9]{2}$') THEN
        RAISE EXCEPTION 'a branch has a branch_code that is not 2 digits';
    END IF;
    IF EXISTS (SELECT 1 FROM legacy.app_users WHERE length(username) > 50) THEN
        RAISE EXCEPTION 'a username is longer than 50 characters (the new column is VARCHAR(50))';
    END IF;
    IF (SELECT count(*) FROM (SELECT left(employee_code, 20) c FROM legacy.staff
                              UNION ALL SELECT left(code, 20) FROM legacy.therapists) x)
       <> (SELECT count(DISTINCT c) FROM (SELECT left(employee_code, 20) c FROM legacy.staff
                                          UNION ALL SELECT left(code, 20) FROM legacy.therapists) y) THEN
        RAISE EXCEPTION 'staff/therapist codes are not unique within 20 characters';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS legacy.migration_issue (
    id       BIGSERIAL PRIMARY KEY,
    area     TEXT NOT NULL,
    old_ref  TEXT,
    issue    TEXT NOT NULL,
    action   TEXT NOT NULL,
    logged_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TEMP TABLE _ctx AS
SELECT (SELECT user_id FROM public.app_user WHERE username = 'system') AS sys_user,
       (SELECT permission_id FROM public.permission WHERE permission_name = 'SALE_CANCEL') AS cancel_perm,
       (SELECT permission_id FROM public.permission WHERE permission_name = 'COMMISSION_SCHEME_MANAGE') AS scheme_perm,
       (SELECT permission_id FROM public.permission WHERE permission_name = 'COMMISSION_CLOSING_MANAGE') AS closing_perm;

-- ---------------------------------------------------------------------------
-- 1. Branches (ids kept)
-- ---------------------------------------------------------------------------
INSERT INTO public.branch (branch_id, branch_code, branch_name, address, phone, branch_status,
                           branch_create_at, branch_create_by)
SELECT b.branch_id, b.branch_code, left(b.branch_name, 100), left(coalesce(b.address, ''), 255),
       left(coalesce(b.phone, ''), 20), b.branch_status,
       coalesce(b.branch_create_at AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC'), (SELECT sys_user FROM _ctx)
FROM legacy.branch b;

-- ---------------------------------------------------------------------------
-- 2. Services: single-visit services and course masters become "service" rows
-- ---------------------------------------------------------------------------
INSERT INTO public.service (service_type, service_name, service_price, service_status, service_code,
                            service_create_at, service_create_by)
SELECT 'SingleVisit', left(s.name, 150), s.price, s.active, left(s.code, 20),
       s.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx)
FROM legacy.services s;

CREATE TEMP TABLE _course_code AS
SELECT c.id AS old_id,
       CASE WHEN length(c.code) <= 20 AND NOT EXISTS (SELECT 1 FROM legacy.services s WHERE left(s.code, 20) = c.code)
            THEN c.code ELSE 'CRS-' || c.id END AS code
FROM legacy.courses c;

INSERT INTO legacy.migration_issue (area, old_ref, issue, action)
SELECT 'course master', 'courses.id=' || cc.old_id, 'course code "' || c.code || '" does not fit service_code', 'migrated as ' || cc.code
FROM _course_code cc JOIN legacy.courses c ON c.id = cc.old_id WHERE cc.code <> c.code;

INSERT INTO public.service (service_type, service_name, service_price, service_status, service_code,
                            service_create_at, service_create_by, default_total_visits, default_bonus_visits,
                            default_validity_days)
SELECT 'Course', left(c.name, 150), c.price, c.active, cc.code, c.created_at AT TIME ZONE 'UTC',
       (SELECT sys_user FROM _ctx), c.sessions, c.bonus_sessions, c.validity_days
FROM legacy.courses c JOIN _course_code cc ON cc.old_id = c.id;

CREATE TABLE IF NOT EXISTS legacy.map_service (old_service_id BIGINT PRIMARY KEY, new_service_id BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS legacy.map_course_master (old_course_id BIGINT PRIMARY KEY, new_service_id BIGINT NOT NULL);
TRUNCATE legacy.map_service, legacy.map_course_master;
INSERT INTO legacy.map_service
SELECT s.id, n.service_id FROM legacy.services s
JOIN public.service n ON n.service_code = left(s.code, 20) AND n.service_type = 'SingleVisit';
INSERT INTO legacy.map_course_master
SELECT cc.old_id, n.service_id FROM _course_code cc
JOIN public.service n ON n.service_code = cc.code AND n.service_type = 'Course';

-- ---------------------------------------------------------------------------
-- 3. Payment methods (matched by name, the rest added)
-- ---------------------------------------------------------------------------
INSERT INTO public.payment_method (method_name, payment_method_status, payment_method_create_by)
SELECT left(m.name, 50), m.active, (SELECT sys_user FROM _ctx)
FROM legacy.payment_methods m
WHERE NOT EXISTS (SELECT 1 FROM public.payment_method p WHERE lower(p.method_name) = lower(m.name));

CREATE TABLE IF NOT EXISTS legacy.map_payment_method (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
TRUNCATE legacy.map_payment_method;
INSERT INTO legacy.map_payment_method
SELECT m.id, (SELECT min(p.payment_method_id) FROM public.payment_method p WHERE lower(p.method_name) = lower(left(m.name, 50)))
FROM legacy.payment_methods m;

-- ---------------------------------------------------------------------------
-- 4. Users, staff, roles, branch access
-- ---------------------------------------------------------------------------
INSERT INTO public.app_user (username, password_hash, user_status, last_login_at, user_create_at, user_create_by)
SELECT u.username, u.password_hash, u.active, u.created_at AT TIME ZONE 'UTC', u.created_at AT TIME ZONE 'UTC',
       (SELECT sys_user FROM _ctx)
FROM legacy.app_users u;

CREATE TABLE IF NOT EXISTS legacy.map_user (old_user_id BIGINT PRIMARY KEY, new_user_id BIGINT NOT NULL);
TRUNCATE legacy.map_user;
INSERT INTO legacy.map_user
SELECT u.id, n.user_id FROM legacy.app_users u JOIN public.app_user n ON n.username = u.username;

-- staff = old staff (their id is the user id) + old therapists (no account)
CREATE TEMP TABLE _staff_src AS
SELECT 'staff'::text AS kind, s.id AS old_id, left(s.employee_code, 20) AS code, s.full_name, s.active,
       s.branch_id, s.created_at,
       CASE WHEN u.role = 'MANAGER' THEN 'Manager' ELSE 'Therapist' END AS position
FROM legacy.staff s JOIN legacy.app_users u ON u.id = s.id
UNION ALL
SELECT 'therapist', t.id, left(t.code, 20), t.full_name, t.active, t.branch_id, t.created_at, 'Therapist'
FROM legacy.therapists t;

INSERT INTO public.staff (staff_code, staff_fname, staff_lname, position, branch_id, staff_status, hire_date,
                          staff_create_at, staff_create_by)
SELECT x.code,
       left(split_part(btrim(x.full_name), ' ', 1), 100),
       left(coalesce(nullif(btrim(substr(btrim(x.full_name), length(split_part(btrim(x.full_name), ' ', 1)) + 1)), ''), '-'), 100),
       x.position::staff_position_enum,
       coalesce(x.branch_id, (SELECT min(branch_id) FROM public.branch)),
       x.active, (x.created_at AT TIME ZONE 'Asia/Bangkok')::date,
       x.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx)
FROM _staff_src x;

CREATE TABLE IF NOT EXISTS legacy.map_staff (old_staff_id BIGINT PRIMARY KEY, new_staff_id BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS legacy.map_therapist (old_therapist_id BIGINT PRIMARY KEY, new_staff_id BIGINT NOT NULL);
TRUNCATE legacy.map_staff, legacy.map_therapist;
INSERT INTO legacy.map_staff SELECT x.old_id, n.staff_id FROM _staff_src x JOIN public.staff n ON n.staff_code = x.code WHERE x.kind = 'staff';
INSERT INTO legacy.map_therapist SELECT x.old_id, n.staff_id FROM _staff_src x JOIN public.staff n ON n.staff_code = x.code WHERE x.kind = 'therapist';

UPDATE public.app_user a SET staff_id = ms.new_staff_id
FROM legacy.map_user mu JOIN legacy.map_staff ms ON ms.old_staff_id = mu.old_user_id
WHERE a.user_id = mu.new_user_id;

INSERT INTO public.user_role (user_id, role_id)
SELECT mu.new_user_id, r.role_id
FROM legacy.app_users u JOIN legacy.map_user mu ON mu.old_user_id = u.id
JOIN public.role r ON r.role_name = u.role;

INSERT INTO public.user_branch_access (user_id, branch_id)
SELECT mu.new_user_id, b.branch_id
FROM legacy.app_users u JOIN legacy.map_user mu ON mu.old_user_id = u.id
CROSS JOIN public.branch b
LEFT JOIN legacy.staff s ON s.id = u.id
WHERE u.role = 'MANAGER' OR s.branch_id IS NULL OR s.branch_id = b.branch_id;

-- ---------------------------------------------------------------------------
-- 5. Master data (customer groups, referral channels, insurance, old master_data)
-- ---------------------------------------------------------------------------
INSERT INTO public.master_data_type (type_name, type_create_by)
SELECT DISTINCT left(d.category, 50), (SELECT sys_user FROM _ctx)
FROM legacy.master_data d
WHERE NOT EXISTS (SELECT 1 FROM public.master_data_type t WHERE lower(t.type_name) = lower(d.category));

INSERT INTO public.master_data_value (type_id, value_name, value_status, sort_order, value_create_by)
SELECT t.type_id, left(d.name, 100), d.active,
       row_number() OVER (PARTITION BY t.type_id ORDER BY d.id) , (SELECT sys_user FROM _ctx)
FROM legacy.master_data d
JOIN public.master_data_type t ON lower(t.type_name) = lower(d.category)
WHERE NOT EXISTS (SELECT 1 FROM public.master_data_value v WHERE v.type_id = t.type_id AND lower(v.value_name) = lower(d.name));

-- the free-text patient fields become master data values
INSERT INTO public.master_data_value (type_id, value_name, value_status, sort_order, value_create_by)
SELECT t.type_id, v.val, TRUE, 1000, (SELECT sys_user FROM _ctx)
FROM (SELECT 'Customer Group' AS tname, btrim(customer_type) AS val FROM legacy.patients WHERE nullif(btrim(customer_type), '') IS NOT NULL
      UNION SELECT 'Referral Channel', btrim(customer_source) FROM legacy.patients WHERE nullif(btrim(customer_source), '') IS NOT NULL
      UNION SELECT 'Insurance Company', btrim(insurance_company) FROM legacy.patients WHERE nullif(btrim(insurance_company), '') IS NOT NULL) v
JOIN public.master_data_type t ON t.type_name = v.tname
WHERE NOT EXISTS (SELECT 1 FROM public.master_data_value x WHERE x.type_id = t.type_id AND lower(x.value_name) = lower(left(v.val, 100)));

-- ---------------------------------------------------------------------------
-- 6. Commission scheme, tiers, treatment fee rules (ids kept)
-- ---------------------------------------------------------------------------
INSERT INTO public.commission_scheme (commission_scheme_id, scheme_name, scheme_version, permission_id,
                                      effective_from, effective_to, scheme_create_at, scheme_create_by)
SELECT s.id, left(s.name, 100),
       CASE WHEN s.version ~ '^[0-9]+$' AND s.version::int >= 1 THEN s.version::int
            ELSE (row_number() OVER (PARTITION BY s.name ORDER BY s.id))::int END,
       (SELECT scheme_perm FROM _ctx), s.effective_from, s.effective_to, s.created_at AT TIME ZONE 'UTC',
       (SELECT sys_user FROM _ctx)
FROM legacy.commission_schemes s;

INSERT INTO legacy.migration_issue (area, old_ref, issue, action)
SELECT 'commission scheme', 'commission_schemes.id=' || id, 'scheme was inactive (the new schema has no status flag)', 'migrated as a normal scheme, review it'
FROM legacy.commission_schemes WHERE NOT active;

INSERT INTO public.commission_tier (tier_id, commission_scheme_id, permission_id, tier_order, minimum_monthly_sales,
                                    maximum_monthly_sales, commission_rate, tier_status, effective_from, effective_to,
                                    tier_create_at, tier_create_by)
SELECT t.id, t.scheme_id, (SELECT scheme_perm FROM _ctx), t.tier_order, t.minimum_sales, t.maximum_sales,
       round(t.commission_rate, 4), t.active, s.effective_from, s.effective_to,
       s.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx)
FROM legacy.commission_tiers t JOIN legacy.commission_schemes s ON s.id = t.scheme_id;

INSERT INTO public.treatment_fee_rule (treatment_fee_rule_id, permission_id, staff_id, fee_type, fee_value,
                                       percentage_base, effective_from, effective_to, treatment_fee_rule_status,
                                       treatment_fee_rule_create_by)
SELECT r.id, (SELECT scheme_perm FROM _ctx), ms.new_staff_id,
       CASE WHEN upper(r.fee_type) LIKE 'FIX%' THEN 'Fixed' ELSE 'Percentage' END::fee_type_enum,
       r.fee_value,
       CASE WHEN upper(r.fee_type) LIKE 'FIX%' THEN NULL ELSE 'CourseValuePerVisit' END::percentage_base_enum,
       r.effective_from, r.effective_to, r.active, (SELECT sys_user FROM _ctx)
FROM legacy.treatment_fee_rules r LEFT JOIN legacy.map_staff ms ON ms.old_staff_id = r.employee_id;

-- ---------------------------------------------------------------------------
-- 7. Patients (ids kept; HN kept when valid, otherwise re-issued)
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE _patient AS
SELECT p.id, p.branch_id, p.created_at, p.updated_at, p.active,
       CASE WHEN p.hn ~ '^[0-9]{10}$' THEN p.hn END AS hn_new,
       p.hn AS hn_old,
       btrim(p.full_name) AS full_name,
       CASE WHEN p.identity_document_type = 'NATIONAL_ID' THEN 'Thai' ELSE 'Foreigner' END AS customer_type,
       CASE
           WHEN btrim(coalesce(p.title, '')) IN ('นาย', 'นาง', 'นางสาว', 'ด.ช.', 'ด.ญ.') THEN btrim(p.title)
           WHEN btrim(coalesce(p.title, '')) IN ('เด็กชาย', 'ดช', 'ดช.', 'Master') THEN 'ด.ช.'
           WHEN btrim(coalesce(p.title, '')) IN ('เด็กหญิง', 'ดญ', 'ดญ.', 'Miss') AND p.gender IN ('หญิง', 'Female', 'FEMALE') THEN 'ด.ญ.'
           WHEN p.gender IN ('หญิง', 'Female', 'FEMALE') THEN 'นางสาว'
           ELSE 'นาย' END AS title_new,
       CASE WHEN btrim(coalesce(p.title, '')) IN ('นาย', 'นาง', 'นางสาว', 'ด.ช.', 'ด.ญ.', 'เด็กชาย', 'ดช', 'ดช.', 'Master', 'เด็กหญิง', 'ดญ', 'ดญ.', 'Miss')
            THEN FALSE ELSE TRUE END AS title_guessed,
       CASE WHEN p.gender IN ('ชาย', 'Male', 'MALE') THEN 'Male'
            WHEN p.gender IN ('หญิง', 'Female', 'FEMALE') THEN 'Female' ELSE 'Other' END AS gender_new,
       CASE WHEN p.identity_document_type = 'NATIONAL_ID' AND p.identity_number ~ '^[0-9]{13}$' THEN p.identity_number END AS national_id,
       CASE WHEN p.identity_document_type <> 'NATIONAL_ID' THEN left(p.identity_number, 20) END AS passport_no,
       CASE WHEN upper(btrim(coalesce(p.blood_group, ''))) ~ '^(A|B|O|AB)[+-]?$'
            THEN regexp_replace(upper(btrim(p.blood_group)), '[+-]$', '') END AS blood_group,
       p.phone, p.nickname, p.nationality, p.address, p.birth_date,
       p.customer_type AS customer_group_text, p.customer_source, p.insurance_company
FROM legacy.patients p;

INSERT INTO legacy.migration_issue (area, old_ref, issue, action)
SELECT 'patient', 'patients.id=' || p.id, 'identity number "' || p.identity_number || '" is not a 13-digit national id', 'national_id left empty; the number stays in legacy.patients'
FROM legacy.patients p JOIN _patient x ON x.id = p.id
WHERE p.identity_document_type = 'NATIONAL_ID' AND x.national_id IS NULL;
INSERT INTO legacy.migration_issue (area, old_ref, issue, action)
SELECT 'patient', 'patients.id=' || p.id, 'title "' || coalesce(p.title, '') || '" is not in the new title list', 'title chosen from gender'
FROM legacy.patients p JOIN _patient x ON x.id = p.id WHERE x.title_guessed;

-- re-issue HNs that do not have the 10-digit YYBBMMNNNN shape
WITH kept AS (
    SELECT branch_id, substr(hn_new, 1, 2) || substr(hn_new, 5, 2) AS ym, max(substr(hn_new, 7, 4)::int) AS mx
    FROM _patient WHERE hn_new IS NOT NULL GROUP BY 1, 2
), todo AS (
    SELECT x.id, x.branch_id, to_char(x.created_at AT TIME ZONE 'Asia/Bangkok', 'YYMM') AS ym,
           row_number() OVER (PARTITION BY x.branch_id, to_char(x.created_at AT TIME ZONE 'Asia/Bangkok', 'YYMM')
                              ORDER BY x.created_at, x.id) AS rn
    FROM _patient x WHERE x.hn_new IS NULL
)
UPDATE _patient x
SET hn_new = substr(t.ym, 1, 2) || b.branch_code || substr(t.ym, 3, 2) || lpad((coalesce(k.mx, 0) + t.rn)::text, 4, '0')
FROM todo t
JOIN legacy.branch b ON b.branch_id = t.branch_id
LEFT JOIN kept k ON k.branch_id = t.branch_id AND k.ym = t.ym
WHERE x.id = t.id;

INSERT INTO legacy.migration_issue (area, old_ref, issue, action)
SELECT 'patient', 'patients.id=' || id, 'HN "' || hn_old || '" is not 10 digits (YYBBMMNNNN)', 'new HN ' || hn_new || ' issued; old HN stays in legacy.patients'
FROM _patient WHERE hn_old IS DISTINCT FROM hn_new;

INSERT INTO public.patient (patient_id, patient_hn, customer_type, title, patient_fname, patient_lname, gender, phone,
                            branch_id, registered_at, patient_nickname, national_id, passport_no, birthdate,
                            blood_group, nationality, address, registered_by, patient_update_at, patient_update_by,
                            customer_group_value_id, referral_channel_value_id, insurance_company_value_id)
SELECT x.id, x.hn_new, x.customer_type::customer_type_enum, x.title_new::title_enum,
       left(split_part(x.full_name, ' ', 1), 100),
       left(coalesce(nullif(btrim(substr(x.full_name, length(split_part(x.full_name, ' ', 1)) + 1)), ''), '-'), 100),
       x.gender_new::gender_enum, left(x.phone, 20), x.branch_id, x.created_at AT TIME ZONE 'UTC',
       left(x.nickname, 50), x.national_id, x.passport_no, x.birth_date, x.blood_group::blood_group_enum,
       left(x.nationality, 50), left(x.address, 255), (SELECT sys_user FROM _ctx),
       CASE WHEN x.updated_at > x.created_at THEN x.updated_at AT TIME ZONE 'UTC' END,
       CASE WHEN x.updated_at > x.created_at THEN (SELECT sys_user FROM _ctx) END,
       (SELECT v.value_id FROM public.master_data_value v JOIN public.master_data_type t ON t.type_id = v.type_id
         WHERE t.type_name = 'Customer Group' AND lower(v.value_name) = lower(left(btrim(x.customer_group_text), 100)) LIMIT 1),
       (SELECT v.value_id FROM public.master_data_value v JOIN public.master_data_type t ON t.type_id = v.type_id
         WHERE t.type_name = 'Referral Channel' AND lower(v.value_name) = lower(left(btrim(x.customer_source), 100)) LIMIT 1),
       (SELECT v.value_id FROM public.master_data_value v JOIN public.master_data_type t ON t.type_id = v.type_id
         WHERE t.type_name = 'Insurance Company' AND lower(v.value_name) = lower(left(btrim(x.insurance_company), 100)) LIMIT 1)
FROM _patient x;

INSERT INTO public.hn_sequence (branch_id, year_month, last_running_no)
SELECT branch_id, ym, max(n) FROM (
    SELECT p.branch_id, substr(p.patient_hn, 1, 2) || substr(p.patient_hn, 5, 2) AS ym, substr(p.patient_hn, 7, 4)::int AS n
    FROM public.patient p
    UNION ALL
    SELECT h.branch_id, lpad((h.registration_year % 100)::text, 2, '0') || lpad(h.registration_month::text, 2, '0'), h.last_number
    FROM legacy.hn_counters h
) s GROUP BY branch_id, ym;

-- ---------------------------------------------------------------------------
-- 8. Appointments (ids kept), status history, visits
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE _appt AS
SELECT a.id, a.branch_id, a.patient_id, a.service_id, a.therapist_id, a.start_at, a.created_at, a.updated_at,
       a.note, a.cancel_reason,
       CASE a.status WHEN 'CONFIRMED' THEN 'Confirmed' WHEN 'SCHEDULED' THEN 'Confirmed' WHEN 'ARRIVED' THEN 'Arrived'
                     WHEN 'CHECKED_IN' THEN 'Arrived' WHEN 'IN_SERVICE' THEN 'InService' WHEN 'COMPLETED' THEN 'Completed'
                     WHEN 'CANCELLED' THEN 'Cancelled' WHEN 'RESCHEDULED' THEN 'Rescheduled' WHEN 'NO_SHOW' THEN 'NoShow'
                     ELSE 'Confirmed' END AS status_new
FROM legacy.appointments a;

INSERT INTO public.appointment (appointment_id, patient_id, branch_id, staff_id, service_id, appointment_status,
                                appointment_date, appointment_time, appointment_create_at, appointment_create_by,
                                appointment_update_at, appointment_update_by, appointment_patient_type)
SELECT x.id, x.patient_id, x.branch_id, mt.new_staff_id, ms.new_service_id, x.status_new::appointment_status_enum,
       (x.start_at AT TIME ZONE 'Asia/Bangkok')::date, (x.start_at AT TIME ZONE 'Asia/Bangkok')::time,
       x.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx),
       CASE WHEN x.updated_at > x.created_at THEN x.updated_at AT TIME ZONE 'UTC' END,
       CASE WHEN x.updated_at > x.created_at THEN (SELECT sys_user FROM _ctx) END, 'CURRENT'
FROM _appt x
JOIN legacy.map_therapist mt ON mt.old_therapist_id = x.therapist_id
JOIN legacy.map_service ms ON ms.old_service_id = x.service_id;

INSERT INTO public.appointment_status_history (appointment_id, from_status, to_status, changed_by, changed_at, reason)
SELECT x.id, NULL, x.status_new::appointment_to_status_enum, (SELECT sys_user FROM _ctx),
       coalesce(x.updated_at, x.created_at) AT TIME ZONE 'UTC',
       left('ย้ายข้อมูลจากระบบเดิม' || coalesce(' - เหตุผลยกเลิก: ' || nullif(btrim(x.cancel_reason), ''), ''), 255)
FROM _appt x;

CREATE TEMP TABLE _visit_appt AS
SELECT x.id AS appointment_id, nextval(pg_get_serial_sequence('public.patient_visit', 'visit_id')) AS visit_id
FROM _appt x WHERE x.status_new IN ('Arrived', 'InService', 'Completed');

INSERT INTO public.patient_visit (visit_id, appointment_id, patient_id, branch_id, assigned_staff_id, visit_date,
                                  visit_status, visit_create_at, visit_create_by)
SELECT v.visit_id, x.id, x.patient_id, x.branch_id, mt.new_staff_id, x.start_at AT TIME ZONE 'UTC',
       x.status_new::visit_status_enum, x.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx)
FROM _visit_appt v JOIN _appt x ON x.id = v.appointment_id
JOIN legacy.map_therapist mt ON mt.old_therapist_id = x.therapist_id;

-- ---------------------------------------------------------------------------
-- 9. Treatments (every treatment needs a visit)
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE _treat AS
SELECT t.id AS old_id, t.patient_id, t.appointment_id, t.staff_id, t.service, t.note, t.created_at,
       va.visit_id AS appt_visit_id,
       nextval(pg_get_serial_sequence('public.patient_visit', 'visit_id')) AS new_visit_id,
       (SELECT ms.new_service_id FROM legacy.services s JOIN legacy.map_service ms ON ms.old_service_id = s.id
         WHERE lower(btrim(s.name)) = lower(btrim(t.service)) OR lower(s.code) = lower(btrim(t.service)) LIMIT 1) AS service_id
FROM legacy.treatments t LEFT JOIN _visit_appt va ON va.appointment_id = t.appointment_id;

INSERT INTO public.service (service_type, service_name, service_price, service_status, service_code, service_create_by)
SELECT 'SingleVisit', 'บริการจากระบบเดิม (ไม่ระบุ)', 0, FALSE, 'LEGACY', (SELECT sys_user FROM _ctx)
WHERE EXISTS (SELECT 1 FROM _treat WHERE service_id IS NULL);
INSERT INTO legacy.migration_issue (area, old_ref, issue, action)
SELECT 'treatment', 'treatments.id=' || old_id, 'service text "' || service || '" matches no service', 'linked to the inactive service LEGACY'
FROM _treat WHERE service_id IS NULL;

INSERT INTO public.patient_visit (visit_id, appointment_id, patient_id, branch_id, assigned_staff_id, visit_date,
                                  visit_status, visit_create_at, visit_create_by)
SELECT t.new_visit_id, NULL, t.patient_id, p.branch_id, ms.new_staff_id, t.created_at AT TIME ZONE 'UTC', 'Completed',
       t.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx)
FROM _treat t JOIN public.patient p ON p.patient_id = t.patient_id
LEFT JOIN legacy.map_staff ms ON ms.old_staff_id = t.staff_id
WHERE t.appt_visit_id IS NULL;

INSERT INTO public.treatment (visit_id, service_id, treating_staff_id, notes, treatment_create_by, treatment_create_at)
SELECT coalesce(t.appt_visit_id, t.new_visit_id),
       coalesce(t.service_id, (SELECT service_id FROM public.service WHERE service_code = 'LEGACY')),
       ms.new_staff_id, t.note, (SELECT sys_user FROM _ctx), t.created_at AT TIME ZONE 'UTC'
FROM _treat t LEFT JOIN legacy.map_staff ms ON ms.old_staff_id = t.staff_id;

-- ---------------------------------------------------------------------------
-- 10. Receipts (ids kept = old sale id), sale lines, payments, voids, refunds
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS legacy.map_receipt (old_sale_id BIGINT, new_receipt_id BIGINT NOT NULL, new_receipt_no TEXT NOT NULL, old_receipt_no TEXT);
TRUNCATE legacy.map_receipt;
INSERT INTO legacy.map_receipt
SELECT s.id, s.id, 'RE-' || lpad((row_number() OVER (ORDER BY s.sold_at, s.id))::text, 7, '0'), s.receipt_no
FROM legacy.sales s;

INSERT INTO public.receipt (receipt_id, receipt_no, patient_id, branch_id, sale_date, receipt_create_at,
                            receipt_create_by, subtotal_before_discount, bill_discount_amount, taxable_amount,
                            tax_amount, net_amount, receipt_status)
SELECT s.id, mr.new_receipt_no, s.patient_id, s.branch_id, s.sold_at AT TIME ZONE 'UTC', s.created_at AT TIME ZONE 'UTC',
       (SELECT sys_user FROM _ctx), tot.total, 0, tot.total, 0, tot.total,
       CASE WHEN s.status = 'VOIDED' THEN 'Voided' ELSE 'Issued' END::receipt_status_enum
FROM legacy.sales s
JOIN legacy.map_receipt mr ON mr.old_sale_id = s.id
CROSS JOIN LATERAL (SELECT coalesce((SELECT sum(i.line_total) FROM legacy.sale_items i WHERE i.sale_id = s.id), s.total) AS total) tot;

CREATE TABLE IF NOT EXISTS legacy.map_sale_line (old_item_id BIGINT PRIMARY KEY, new_transaction_id BIGINT NOT NULL);
TRUNCATE legacy.map_sale_line;
INSERT INTO legacy.map_sale_line
SELECT i.id, nextval(pg_get_serial_sequence('public.sales_transaction', 'transaction_id')) FROM legacy.sale_items i;

INSERT INTO public.sales_transaction (transaction_id, patient_id, branch_id, service_id, transaction_type,
        salesperson_staff_id, quantity, unit_price, amount, transaction_date, transaction_status,
        transaction_create_at, transaction_create_by, receipt_id, line_no, line_discount_amount,
        allocated_bill_discount_amount, commissionable_amount, item_name_snapshot, unit_name_snapshot)
SELECT ml.new_transaction_id, s.patient_id, s.branch_id,
       CASE WHEN i.item_type = 'COURSE' THEN mc.new_service_id ELSE msv.new_service_id END,
       CASE WHEN i.item_type = 'COURSE' THEN 'CoursePurchase' ELSE 'SingleVisit' END::transaction_type_enum,
       mst.new_staff_id, i.quantity, i.unit_price, i.line_total, s.sold_at AT TIME ZONE 'UTC', s.status <> 'VOIDED',
       i.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx), s.id,
       (row_number() OVER (PARTITION BY i.sale_id ORDER BY i.id))::int, 0, 0, i.line_total,
       left(i.description, 200), CASE WHEN i.item_type = 'COURSE' THEN 'คอร์ส' ELSE 'ครั้ง' END
FROM legacy.sale_items i
JOIN legacy.sales s ON s.id = i.sale_id
JOIN legacy.map_sale_line ml ON ml.old_item_id = i.id
LEFT JOIN legacy.map_course_master mc ON mc.old_course_id = i.course_id
LEFT JOIN legacy.map_service msv ON msv.old_service_id = i.service_id
LEFT JOIN legacy.map_staff mst ON mst.old_staff_id = s.salesperson_staff_id;

-- a voided sale: one void record per line
INSERT INTO public.transaction_cancellation (permission_id, transaction_id, reason, cancelled_by, cancelled_at, cancellation_type)
SELECT (SELECT cancel_perm FROM _ctx), ml.new_transaction_id,
       left(coalesce(nullif(btrim(s.void_reason), ''), 'ยกเลิกในระบบเดิม'), 255), (SELECT sys_user FROM _ctx),
       coalesce(s.voided_at, s.sold_at) AT TIME ZONE 'UTC', 'Void'
FROM legacy.sale_items i JOIN legacy.sales s ON s.id = i.sale_id AND s.status = 'VOIDED'
JOIN legacy.map_sale_line ml ON ml.old_item_id = i.id;

-- payments (money in)
CREATE TEMP TABLE _pay AS
SELECT p.id AS old_id, nextval(pg_get_serial_sequence('public.payment_transaction', 'payment_id')) AS new_id
FROM legacy.sale_payments p WHERE p.type = 'PAYMENT';

INSERT INTO public.payment_transaction (payment_id, patient_id, branch_id, amount, payment_method_id, payment_date,
                                        payment_status, receipt_id, payment_create_at, payment_create_by)
SELECT py.new_id, s.patient_id, s.branch_id, p.amount, mpm.new_id, p.created_at AT TIME ZONE 'UTC', TRUE, p.sale_id,
       p.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx)
FROM legacy.sale_payments p JOIN _pay py ON py.old_id = p.id
JOIN legacy.sales s ON s.id = p.sale_id
JOIN legacy.map_payment_method mpm ON mpm.old_id = p.payment_method_id;

-- refunds (money out): a Refund cancellation of the original payment + a completed refund
CREATE TEMP TABLE _refund AS
SELECT r.id AS old_id, r.sale_id, r.payment_method_id, abs(r.amount) AS amount, r.reason, r.created_at,
       coalesce(py.new_id, (SELECT min(new_id) FROM _pay x JOIN legacy.sale_payments q ON q.id = x.old_id WHERE q.sale_id = r.sale_id)) AS payment_id,
       nextval(pg_get_serial_sequence('public.transaction_cancellation', 'cancellation_id')) AS cancellation_id
FROM legacy.sale_payments r LEFT JOIN _pay py ON py.old_id = r.reverses_payment_id
WHERE r.type = 'REFUND';

INSERT INTO legacy.migration_issue (area, old_ref, issue, action)
SELECT 'refund', 'sale_payments.id=' || old_id, 'refund has no payment to refer to', 'skipped; the row stays in legacy.sale_payments'
FROM _refund WHERE payment_id IS NULL;

INSERT INTO public.transaction_cancellation (cancellation_id, permission_id, payment_id, reason, cancelled_by, cancelled_at, cancellation_type)
SELECT f.cancellation_id, (SELECT cancel_perm FROM _ctx), f.payment_id,
       left(coalesce(nullif(btrim(f.reason), ''), 'คืนเงินในระบบเดิม'), 255), (SELECT sys_user FROM _ctx),
       f.created_at AT TIME ZONE 'UTC', 'Refund'
FROM _refund f WHERE f.payment_id IS NOT NULL;

INSERT INTO public.refund_transaction (cancellation_id, original_payment_id, refund_amount, payment_method_id, refund_status,
                                       refunded_at, refunded_by, requested_at, requested_by)
SELECT f.cancellation_id, f.payment_id, f.amount, mpm.new_id, 'Completed', f.created_at AT TIME ZONE 'UTC',
       (SELECT sys_user FROM _ctx), f.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx)
FROM _refund f JOIN legacy.map_payment_method mpm ON mpm.old_id = f.payment_method_id
WHERE f.payment_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 11. Course purchases -> course (ids kept)
-- ---------------------------------------------------------------------------
-- a purchase made without a sale line gets a receipt and a sale line of its own
SELECT setval(pg_get_serial_sequence('public.receipt', 'receipt_id'), greatest((SELECT coalesce(max(receipt_id), 0) FROM public.receipt), 1));

CREATE TEMP TABLE _purchase AS
SELECT p.id AS old_id, p.patient_id, p.course_id AS master_id, p.branch_id, p.course_name, p.price, p.sessions, p.bonus_sessions,
       p.purchased_at, p.expires_at, p.status, p.sale_month, p.seller_employee_id, p.case_owner_employee_id,
       p.commission_status, p.commission_scheme_id, p.commission_scheme_version, p.provisional_commission_rate,
       p.locked_commission_rate, p.total_course_commission_pool, p.commission_allocation_per_visit,
       p.commission_generated_date,
       li.sale_id, li.id AS item_id
FROM legacy.course_purchases p
LEFT JOIN legacy.sale_items li ON li.course_purchase_id = p.id;

CREATE TEMP TABLE _orphan AS
SELECT o.old_id,
       nextval(pg_get_serial_sequence('public.receipt', 'receipt_id')) AS receipt_id,
       nextval(pg_get_serial_sequence('public.sales_transaction', 'transaction_id')) AS txn_id,
       (SELECT coalesce(max(substr(receipt_no, 4)::int), 0) FROM public.receipt) + (row_number() OVER (ORDER BY o.purchased_at, o.old_id))::int AS no
FROM _purchase o WHERE o.sale_id IS NULL;

INSERT INTO legacy.migration_issue (area, old_ref, issue, action)
SELECT 'course purchase', 'course_purchases.id=' || old_id, 'purchase has no sale line', 'receipt RE-' || lpad(rno::text, 7, '0') || ' created for it'
FROM _orphan;

INSERT INTO public.receipt (receipt_id, receipt_no, patient_id, branch_id, sale_date, receipt_create_at, receipt_create_by,
                            subtotal_before_discount, bill_discount_amount, taxable_amount, tax_amount, net_amount, receipt_status)
SELECT o.receipt_id, 'RE-' || lpad(o.rno::text, 7, '0'), p.patient_id, p.branch_id, p.purchased_at AT TIME ZONE 'UTC',
       p.purchased_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx), p.price, 0, p.price, 0, p.price, 'Issued'
FROM _orphan o JOIN _purchase p ON p.old_id = o.old_id;

INSERT INTO public.sales_transaction (transaction_id, patient_id, branch_id, service_id, transaction_type, salesperson_staff_id,
        quantity, unit_price, amount, transaction_date, transaction_status, transaction_create_at, transaction_create_by,
        receipt_id, line_no, line_discount_amount, allocated_bill_discount_amount, commissionable_amount,
        item_name_snapshot, unit_name_snapshot)
SELECT o.txn_id, p.patient_id, p.branch_id, mc.new_service_id, 'CoursePurchase', ms.new_staff_id, 1, p.price, p.price,
       p.purchased_at AT TIME ZONE 'UTC', p.status <> 'CANCELLED', p.purchased_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx),
       o.receipt_id, 1, 0, 0, p.price, left(p.course_name, 200), 'คอร์ส'
FROM _orphan o JOIN _purchase p ON p.old_id = o.old_id
JOIN legacy.map_course_master mc ON mc.old_course_id = p.master_id
LEFT JOIN legacy.map_staff ms ON ms.old_staff_id = p.seller_employee_id;

-- where every course sits: receipt, sale line, running number on the receipt
CREATE TEMP TABLE _course AS
SELECT p.old_id,
       coalesce(p.sale_id, o.receipt_id) AS receipt_id,
       coalesce(ml.new_transaction_id, o.txn_id) AS txn_id,
       (row_number() OVER (PARTITION BY coalesce(p.sale_id, o.receipt_id) ORDER BY p.old_id))::int AS seq
FROM _purchase p
LEFT JOIN _orphan o ON o.old_id = p.old_id
LEFT JOIN legacy.map_sale_line ml ON ml.old_item_id = p.item_id;

-- ledger rows (ids pre-allocated so reversals can point at them)
CREATE TEMP TABLE _mv AS
SELECT m.id AS old_id, m.course_purchase_id AS purchase_id, m.type, m.quantity, m.reason, m.appointment_id,
       m.reverses_movement_id, m.created_at,
       nextval(pg_get_serial_sequence('public.course_ledger', 'ledger_id')) AS ledger_id,
       CASE WHEN m.type = 'USAGE' THEN nextval(pg_get_serial_sequence('public.course_visit_usage', 'visit_usage_id')) END AS usage_id
FROM legacy.course_movements m;

-- the staff behind each purchase
CREATE TEMP TABLE _owner AS
SELECT p.old_id,
       coalesce(ms.new_staff_id, mss.new_staff_id, (SELECT min(staff_id) FROM public.staff WHERE position = 'Manager'),
                (SELECT min(staff_id) FROM public.staff)) AS seller_staff_id,
       coalesce(mo.new_staff_id, ms.new_staff_id, mss.new_staff_id, (SELECT min(staff_id) FROM public.staff WHERE position = 'Manager'),
                (SELECT min(staff_id) FROM public.staff)) AS case_owner_staff_id
FROM _purchase p
LEFT JOIN legacy.map_staff ms ON ms.old_staff_id = p.seller_employee_id
LEFT JOIN legacy.sales s ON s.id = p.sale_id
LEFT JOIN legacy.map_staff mss ON mss.old_staff_id = s.salesperson_staff_id
LEFT JOIN legacy.map_staff mo ON mo.old_staff_id = p.case_owner_employee_id;

INSERT INTO public.course (course_id, course_no, course_sequence, course_name, course_price, total_visits, bonus_visits,
        expiry_date, course_status, service_id, course_create_at, course_create_by, receipt_id, sale_month,
        seller_staff_id, case_owner_staff_id, commission_status, commission_scheme_id, commission_scheme_version,
        provisional_commission_rate, locked_commission_rate, total_course_commission_pool,
        commission_allocation_per_visit, commission_generated_date, visits_used, remaining_visits,
        gross_commission_allocated_total, owner_net_commission_released_total, substitute_treatment_fee_total,
        commission_outstanding, purchaser_patient_id, sale_transaction_id)
SELECT p.old_id,
       r.receipt_no || CASE WHEN c.seq > 1 THEN '-' || lpad(c.seq::text, 2, '0') ELSE '' END,
       c.seq, left(p.course_name, 255), p.price, p.sessions, p.bonus_sessions,
       (p.expires_at AT TIME ZONE 'Asia/Bangkok')::date,
       CASE WHEN p.status = 'CANCELLED' THEN 'Cancelled' ELSE 'Active' END::course_status_enum,
       mc.new_service_id, p.purchased_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx), c.receipt_id,
       coalesce(to_char(p.sale_month, 'YYYY-MM'), to_char(p.purchased_at AT TIME ZONE 'Asia/Bangkok', 'YYYY-MM')),
       o.seller_staff_id, o.case_owner_staff_id,
       CASE WHEN p.commission_status = 'LOCKED' THEN 'Locked' ELSE 'Provisional' END::commission_status_enum,
       coalesce(p.commission_scheme_id, (SELECT min(commission_scheme_id) FROM public.commission_scheme)),
       coalesce(CASE WHEN p.commission_scheme_version ~ '^[0-9]+$' THEN p.commission_scheme_version::int END, 1),
       round(p.provisional_commission_rate, 4), round(p.locked_commission_rate, 4), p.total_course_commission_pool,
       p.commission_allocation_per_visit, p.commission_generated_date::date,
       0, 0, 0, 0, 0, 0, p.patient_id, c.txn_id
FROM _purchase p
JOIN _course c ON c.old_id = p.old_id
JOIN _owner o ON o.old_id = p.old_id
JOIN public.receipt r ON r.receipt_id = c.receipt_id
JOIN legacy.map_course_master mc ON mc.old_course_id = p.master_id;

-- the owner of the course
INSERT INTO public.course_shared_member (course_id, patient_id, member_role, member_status, joined_at)
SELECT p.old_id, p.patient_id, 'Owner', TRUE, p.purchased_at AT TIME ZONE 'UTC' FROM _purchase p;

INSERT INTO public.course_sale_staff_share (course_id, staff_id, share_percent, credited_sales_amount, created_by)
SELECT p.old_id, o.seller_staff_id, 100, p.price, (SELECT sys_user FROM _ctx)
FROM _purchase p JOIN _owner o ON o.old_id = p.old_id;

-- visits and visit usages for every USAGE movement
CREATE TEMP TABLE _usage AS
SELECT m.old_id AS movement_id, m.usage_id, m.purchase_id, m.created_at, m.reason, m.appointment_id,
       va.visit_id AS appt_visit_id,
       nextval(pg_get_serial_sequence('public.patient_visit', 'visit_id')) AS new_visit_id,
       EXISTS (SELECT 1 FROM legacy.course_movements r WHERE r.reverses_movement_id = m.old_id) AS reversed
FROM _mv m LEFT JOIN _visit_appt va ON va.appointment_id = m.appointment_id
WHERE m.type = 'USAGE';

INSERT INTO public.patient_visit (visit_id, appointment_id, patient_id, branch_id, assigned_staff_id, visit_date,
                                  visit_status, visit_create_at, visit_create_by)
SELECT u.new_visit_id, NULL, p.patient_id, p.branch_id, o.case_owner_staff_id, u.created_at AT TIME ZONE 'UTC', 'Completed',
       u.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx)
FROM _usage u JOIN _purchase p ON p.old_id = u.purchase_id JOIN _owner o ON o.old_id = u.purchase_id
WHERE u.appt_visit_id IS NULL;

-- old commission allocation rows -> pair with usage movements (by link, else by order within the purchase)
CREATE TEMP TABLE _alloc AS
WITH unlinked AS (
    SELECT a.id, row_number() OVER (PARTITION BY a.course_purchase_id ORDER BY a.id) AS rn
    FROM legacy.commission_allocation_ledger a WHERE a.course_movement_id IS NULL
), free_usage AS (
    SELECT u.movement_id, u.purchase_id, row_number() OVER (PARTITION BY u.purchase_id ORDER BY u.movement_id) AS rn
    FROM _usage u
    WHERE NOT EXISTS (SELECT 1 FROM legacy.commission_allocation_ledger a2 WHERE a2.course_movement_id = u.movement_id)
)
SELECT a.id AS old_id, a.course_purchase_id AS purchase_id, a.visit_date, a.case_owner_employee_id,
       a.treating_employee_id, a.gross_commission_allocation AS gross, a.treatment_fee_rule_id,
       a.treatment_fee_amount AS fee, a.owner_net_commission AS net, a.created_at,
       coalesce(a.course_movement_id, fu.movement_id) AS movement_id
FROM legacy.commission_allocation_ledger a
LEFT JOIN unlinked ul ON ul.id = a.id
LEFT JOIN free_usage fu ON fu.purchase_id = a.course_purchase_id AND fu.rn = ul.rn;

INSERT INTO legacy.migration_issue (area, old_ref, issue, action)
SELECT 'commission allocation', 'commission_allocation_ledger.id=' || old_id, 'allocation row matches no course usage', 'skipped; it stays in legacy.commission_allocation_ledger'
FROM _alloc WHERE movement_id IS NULL;

INSERT INTO public.course_visit_usage (visit_usage_id, visit_id, course_id, patient_id, treating_staff_id, case_owner_staff_id,
        visit_date, visit_qty, commission_allocation_per_visit, gross_commission_allocation, treatment_fee_rule_id,
        substitute_treatment_fee_amount, owner_net_commission, usage_create_at, usage_create_by, usage_status, print_required)
SELECT u.usage_id, coalesce(u.appt_visit_id, u.new_visit_id), u.purchase_id, p.patient_id,
       coalesce(mt.new_staff_id, o.case_owner_staff_id), coalesce(mco.new_staff_id, o.case_owner_staff_id),
       u.created_at AT TIME ZONE 'UTC', 1, a.gross, a.gross, a.treatment_fee_rule_id, a.fee, a.net,
       u.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx),
       CASE WHEN u.reversed THEN 'Reversed' ELSE 'Completed' END::usage_status_enum, TRUE
FROM _usage u
JOIN _purchase p ON p.old_id = u.purchase_id
JOIN _owner o ON o.old_id = u.purchase_id
LEFT JOIN _alloc a ON a.movement_id = u.movement_id
LEFT JOIN legacy.map_staff mt ON mt.old_staff_id = a.treating_employee_id
LEFT JOIN legacy.map_staff mco ON mco.old_staff_id = a.case_owner_employee_id;

-- course ledger
INSERT INTO public.course_ledger (ledger_id, course_id, patient_id, entry_type, qty_change, balance_after, remark,
        ledger_create_at, ledger_create_by, visit_usage_id, reverses_ledger_id, event_at, sales_transaction_id,
        adjustment_approved_by, adjustment_approved_at)
SELECT m.ledger_id, m.purchase_id, p.patient_id,
       CASE WHEN m.type = 'PURCHASE' THEN 'Purchase'
            WHEN m.type = 'USAGE' THEN 'Treatment'
            WHEN m.type = 'REVERSAL' AND m.reverses_movement_id IS NOT NULL THEN 'Reversal'
            ELSE 'Adjustment' END::entry_type_enum,
       m.quantity,
       (sum(m.quantity) OVER (PARTITION BY m.purchase_id ORDER BY m.old_id))::int,
       CASE WHEN m.type IN ('PURCHASE', 'USAGE') OR (m.type = 'REVERSAL' AND m.reverses_movement_id IS NOT NULL) THEN left(m.reason, 255)
            ELSE left(coalesce(nullif(btrim(m.reason), ''), 'ปรับสิทธิ์/โอนสิทธิ์/ยกเลิกคอร์ส (ย้ายจากระบบเดิม: ' || m.type || ')'), 255) END,
       m.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx),
       m.usage_id, rv.ledger_id, m.created_at AT TIME ZONE 'UTC',
       CASE WHEN m.type = 'PURCHASE' THEN c.txn_id END,
       CASE WHEN m.type NOT IN ('PURCHASE', 'USAGE') AND NOT (m.type = 'REVERSAL' AND m.reverses_movement_id IS NOT NULL)
            THEN (SELECT sys_user FROM _ctx) END,
       CASE WHEN m.type NOT IN ('PURCHASE', 'USAGE') AND NOT (m.type = 'REVERSAL' AND m.reverses_movement_id IS NOT NULL)
            THEN m.created_at AT TIME ZONE 'UTC' END
FROM _mv m
JOIN _purchase p ON p.old_id = m.purchase_id
JOIN _course c ON c.old_id = m.purchase_id
LEFT JOIN _mv rv ON rv.old_id = m.reverses_movement_id;

INSERT INTO legacy.migration_issue (area, old_ref, issue, action)
SELECT 'course ledger', 'course_movements.id=' || m.old_id,
       'old ' || m.type || ' movement has no equivalent entry type in the new ledger', 'stored as an Adjustment with a remark'
FROM _mv m WHERE m.type IN ('TRANSFER_IN', 'TRANSFER_OUT') OR (m.type = 'REVERSAL' AND m.reverses_movement_id IS NULL);

-- commission allocation: the allocation of each usage, and a reversal for usages that were reversed
CREATE TEMP TABLE _alloc_new AS
SELECT a.old_id, a.movement_id, nextval(pg_get_serial_sequence('public.commission_allocation_ledger', 'allocation_id')) AS new_id
FROM _alloc a WHERE a.movement_id IS NOT NULL;

INSERT INTO public.commission_allocation_ledger (allocation_id, visit_usage_id, course_id, patient_id, case_owner_staff_id,
        treating_staff_id, visit_date, gross_commission_allocation, treatment_fee_rule_id, treatment_fee_type,
        treatment_fee_rate_or_amount, treatment_fee_amount, owner_net_commission, allocation_create_at,
        allocation_create_by, entry_type)
SELECT an.new_id, u.usage_id, u.purchase_id, p.patient_id,
       coalesce(mco.new_staff_id, o.case_owner_staff_id), coalesce(mt.new_staff_id, o.case_owner_staff_id),
       a.visit_date, a.gross, a.treatment_fee_rule_id,
       CASE WHEN r.id IS NULL THEN NULL WHEN upper(r.fee_type) LIKE 'FIX%' THEN 'Fixed' ELSE 'Percentage' END::fee_type_enum,
       r.fee_value, a.fee, a.net, a.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx), 'Allocation'
FROM _alloc_new an
JOIN _alloc a ON a.old_id = an.old_id
JOIN _usage u ON u.movement_id = an.movement_id
JOIN _purchase p ON p.old_id = u.purchase_id
JOIN _owner o ON o.old_id = u.purchase_id
LEFT JOIN legacy.treatment_fee_rules r ON r.id = a.treatment_fee_rule_id
LEFT JOIN legacy.map_staff mt ON mt.old_staff_id = a.treating_employee_id
LEFT JOIN legacy.map_staff mco ON mco.old_staff_id = a.case_owner_employee_id;

INSERT INTO public.commission_allocation_ledger (visit_usage_id, course_id, patient_id, case_owner_staff_id, treating_staff_id,
        visit_date, gross_commission_allocation, treatment_fee_rule_id, treatment_fee_type, treatment_fee_rate_or_amount,
        treatment_fee_amount, owner_net_commission, adjustment_reference_id, allocation_create_at, allocation_create_by,
        entry_type, reversal_reason)
SELECT n.visit_usage_id, n.course_id, n.patient_id, n.case_owner_staff_id, n.treating_staff_id, n.visit_date,
       -n.gross_commission_allocation, n.treatment_fee_rule_id, n.treatment_fee_type, n.treatment_fee_rate_or_amount,
       -n.treatment_fee_amount, -n.owner_net_commission, n.allocation_id, n.allocation_create_at, n.allocation_create_by,
       'Reversal', left(coalesce((SELECT nullif(btrim(r.reason), '') FROM legacy.course_movements r
                                   WHERE r.reverses_movement_id = u.movement_id LIMIT 1), 'กลับรายการใช้สิทธิ์ (ย้ายจากระบบเดิม)'), 255)
FROM public.commission_allocation_ledger n
JOIN _usage u ON u.usage_id = n.visit_usage_id AND u.reversed
WHERE n.entry_type = 'Allocation';

INSERT INTO legacy.migration_issue (area, old_ref, issue, action)
SELECT 'commission allocation', 'course_movements.id=' || u.movement_id,
       'the old system left the commission allocated when a usage was reversed', 'a negative Reversal row was added so the course totals are correct'
FROM _usage u WHERE u.reversed AND EXISTS (SELECT 1 FROM _alloc a WHERE a.movement_id = u.movement_id);

-- entitlement + per-course totals, derived from the migrated ledger
INSERT INTO public.course_entitlement (course_id, patient_id, allocated_visits, used_visits, remaining_visits, updated_at)
SELECT l.course_id, l.patient_id,
       coalesce(sum(l.qty_change) FILTER (WHERE l.qty_change > 0 AND l.entry_type <> 'Reversal'), 0)::int,
       (SELECT count(*) FROM public.course_visit_usage u WHERE u.course_id = l.course_id AND u.usage_status = 'Completed')::int,
       greatest(sum(l.qty_change), 0)::int, now() AT TIME ZONE 'UTC'
FROM public.course_ledger l GROUP BY l.course_id, l.patient_id;

UPDATE public.course c SET
    remaining_visits = coalesce((SELECT greatest(sum(l.qty_change), 0) FROM public.course_ledger l WHERE l.course_id = c.course_id), 0),
    visits_used = (SELECT count(*) FROM public.course_visit_usage u WHERE u.course_id = c.course_id AND u.usage_status = 'Completed'),
    gross_commission_allocated_total = coalesce((SELECT greatest(sum(a.gross_commission_allocation), 0) FROM public.commission_allocation_ledger a WHERE a.course_id = c.course_id), 0),
    owner_net_commission_released_total = coalesce((SELECT greatest(sum(a.owner_net_commission), 0) FROM public.commission_allocation_ledger a WHERE a.course_id = c.course_id), 0),
    substitute_treatment_fee_total = coalesce((SELECT greatest(sum(a.treatment_fee_amount), 0) FROM public.commission_allocation_ledger a WHERE a.course_id = c.course_id), 0);
UPDATE public.course c SET commission_outstanding =
    greatest(coalesce(c.total_course_commission_pool, 0) - c.gross_commission_allocated_total, 0);

-- ---------------------------------------------------------------------------
-- 12. Monthly closings, rooms, audit trail
-- ---------------------------------------------------------------------------
INSERT INTO public.monthly_commission_closing (closing_id, closing_month, permission_id, staff_id, monthly_course_sales,
        commission_scheme_id, commission_scheme_version, calculated_commission_rate, locked_commission_rate,
        closing_status, closed_at, closed_by)
SELECT c.id, to_char(c.closing_month, 'YYYY-MM'), (SELECT closing_perm FROM _ctx), ms.new_staff_id, c.monthly_course_sales,
       coalesce(c.commission_scheme_id, (SELECT min(commission_scheme_id) FROM public.commission_scheme)),
       coalesce(CASE WHEN c.commission_scheme_version ~ '^[0-9]+$' THEN c.commission_scheme_version::int END, 1),
       round(c.calculated_commission_rate, 4), round(c.locked_commission_rate, 4), c.closing_status = 'CLOSED',
       c.closed_at AT TIME ZONE 'UTC', coalesce(mu.new_user_id, CASE WHEN c.closing_status = 'CLOSED' THEN (SELECT sys_user FROM _ctx) END)
FROM legacy.commission_closings c
JOIN legacy.map_staff ms ON ms.old_staff_id = c.employee_id
LEFT JOIN legacy.map_user mu ON mu.old_user_id = c.closed_by;

INSERT INTO public.room (branch_id, room_name, room_type, room_status, room_create_at, room_create_by)
SELECT r.branch_id, left(r.name, 100), left(r.type, 50), r.active, r.created_at AT TIME ZONE 'UTC', (SELECT sys_user FROM _ctx)
FROM legacy.clinic_resources r;

INSERT INTO public.audit_log (table_name, record_id, action, new_value, changed_by, changed_at)
SELECT left(a.entity_type, 50), coalesce(a.entity_id::text, '-'), 'INSERT', left(a.action, 255) || ' (ย้ายจากระบบเดิม)',
       coalesce(mu.new_user_id, (SELECT sys_user FROM _ctx)), a.created_at AT TIME ZONE 'UTC'
FROM legacy.audit_logs a LEFT JOIN legacy.map_user mu ON mu.old_user_id = a.actor_id;

-- ---------------------------------------------------------------------------
-- 13. Sequences follow the explicit ids, and a last consistency check
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT * FROM (VALUES
        ('branch', 'branch_id'), ('patient', 'patient_id'), ('appointment', 'appointment_id'),
        ('receipt', 'receipt_id'), ('course', 'course_id'), ('commission_scheme', 'commission_scheme_id'),
        ('commission_tier', 'tier_id'), ('treatment_fee_rule', 'treatment_fee_rule_id'),
        ('monthly_commission_closing', 'closing_id'), ('patient_visit', 'visit_id'),
        ('sales_transaction', 'transaction_id'), ('payment_transaction', 'payment_id'),
        ('transaction_cancellation', 'cancellation_id'), ('course_ledger', 'ledger_id'),
        ('course_visit_usage', 'visit_usage_id'), ('commission_allocation_ledger', 'allocation_id'),
        ('service', 'service_id'), ('staff', 'staff_id'), ('app_user', 'user_id')
    ) AS v(tbl, col) LOOP
        EXECUTE format('SELECT setval(pg_get_serial_sequence(%L, %L), greatest((SELECT coalesce(max(%I), 0) FROM public.%I), 1), (SELECT count(*) > 0 FROM public.%I))',
                       'public.' || t.tbl, t.col, t.col, t.tbl, t.tbl);
    END LOOP;
END $$;

DO $$
DECLARE
    old_n BIGINT; new_n BIGINT;
    old_sum NUMERIC; new_sum NUMERIC;
BEGIN
    SELECT count(*) INTO old_n FROM legacy.patients;           SELECT count(*) INTO new_n FROM public.patient;
    IF old_n <> new_n THEN RAISE EXCEPTION 'patients: % in legacy, % migrated', old_n, new_n; END IF;
    SELECT count(*) INTO old_n FROM legacy.appointments;       SELECT count(*) INTO new_n FROM public.appointment;
    IF old_n <> new_n THEN RAISE EXCEPTION 'appointments: % in legacy, % migrated', old_n, new_n; END IF;
    SELECT count(*) INTO old_n FROM legacy.sales;              SELECT count(*) INTO new_n FROM public.receipt WHERE receipt_id IN (SELECT id FROM legacy.sales);
    IF old_n <> new_n THEN RAISE EXCEPTION 'receipts: % in legacy, % migrated', old_n, new_n; END IF;
    SELECT coalesce(sum(line_total), 0) INTO old_sum FROM legacy.sale_items;
    SELECT coalesce(sum(amount), 0) INTO new_sum FROM public.sales_transaction WHERE receipt_id IN (SELECT id FROM legacy.sales);
    IF old_sum <> new_sum THEN RAISE EXCEPTION 'sale lines: total % in legacy, % migrated', old_sum, new_sum; END IF;
    SELECT coalesce(sum(amount), 0) INTO old_sum FROM legacy.sale_payments WHERE type = 'PAYMENT';
    SELECT coalesce(sum(amount), 0) INTO new_sum FROM public.payment_transaction;
    IF old_sum <> new_sum THEN RAISE EXCEPTION 'payments: total % in legacy, % migrated', old_sum, new_sum; END IF;
    SELECT count(*) INTO old_n FROM legacy.course_purchases;   SELECT count(*) INTO new_n FROM public.course;
    IF old_n <> new_n THEN RAISE EXCEPTION 'courses: % in legacy, % migrated', old_n, new_n; END IF;
    SELECT coalesce(sum(quantity), 0) INTO old_sum FROM legacy.course_movements;
    SELECT coalesce(sum(qty_change), 0) INTO new_sum FROM public.course_ledger;
    IF old_sum <> new_sum THEN RAISE EXCEPTION 'course balance: % in legacy, % migrated', old_sum, new_sum; END IF;
    SELECT count(*) INTO old_n FROM legacy.app_users;          SELECT count(*) INTO new_n FROM public.app_user WHERE username <> 'system';
    IF old_n <> new_n THEN RAISE EXCEPTION 'users: % in legacy, % migrated', old_n, new_n; END IF;
END $$;

SELECT area, count(*) AS rows_needing_a_look FROM legacy.migration_issue GROUP BY area ORDER BY area;

COMMIT;
