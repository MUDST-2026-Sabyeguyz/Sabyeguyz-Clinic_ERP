-- V1__initial_schema.sql
    -- CONSTRAINT format :
    --     pk_<table_name>
    --     fk_<table_name>_<column_name>
    --     chk_<table_name>_<column_name>

-- ENUM types
CREATE TYPE service_type_enum AS ENUM (
    'SingleVisit', 'Course'
);
CREATE TYPE staff_position_enum AS ENUM (
    'Therapist', 'Manager'
);
CREATE TYPE commission_after_termination_policy_enum AS ENUM (
    'FORFEIT', 'CONTINUE_UNTIL_COMPLETE'
);
CREATE TYPE customer_type_enum AS ENUM (
    'Thai', 'Foreigner'
);
CREATE TYPE title_enum AS ENUM (
    'ด.ช.', 'ด.ญ.', 'นาย', 'นาง', 'นางสาว'
);
CREATE TYPE gender_enum AS ENUM (
    'Male', 'Female', 'Other'
);
CREATE TYPE blood_group_enum AS ENUM (
    'A', 'B', 'O', 'AB'
);
CREATE TYPE appointment_status_enum AS ENUM (
    'Unconfirmed', 'Confirmed', 'Arrived', 'InService', 'Completed', 'Cancelled', 'Rescheduled', 'NoShow'
);
CREATE TYPE appointment_patient_type_enum AS ENUM (
    'NEW', 'CURRENT'
);
CREATE TYPE requested_service_type_enum AS ENUM (
    'SingleVisit', 'Course'
);
CREATE TYPE calendar_sync_status_enum AS ENUM (
    'Pending', 'Synced', 'Failed', 'Deleted'                   -- pending integration scope
);
CREATE TYPE appointment_to_status_enum AS ENUM (
    'Confirmed', 'Arrived', 'InService', 'Completed', 'Cancelled', 'Rescheduled', 'NoShow'
);
CREATE TYPE visit_status_enum AS ENUM (
    'Arrived', 'InService', 'Completed', 'Cancelled'
);
CREATE TYPE fee_type_enum AS ENUM (
    'Fixed', 'Percentage'
);
CREATE TYPE percentage_base_enum AS ENUM (
    'CourseValuePerVisit', 'OtherConfiguredBase'
);
CREATE TYPE bill_discount_type_enum AS ENUM (
    'Fixed', 'Percentage'
);
CREATE TYPE receipt_status_enum AS ENUM (
    'Issued', 'Voided', 'Refunded'
);
CREATE TYPE course_status_enum AS ENUM (
    'Active', 'Completed', 'Cancelled', 'Refunded'
);
CREATE TYPE commission_status_enum AS ENUM (
    'Provisional', 'Locked'
);
CREATE TYPE course_member_role_enum AS ENUM (
    'Owner', 'SharedMember'
);
CREATE TYPE transaction_type_enum AS ENUM (
    'SingleVisit', 'CoursePurchase'
);
CREATE TYPE line_discount_type_enum AS ENUM (
    'Fixed', 'Percentage'
);
CREATE TYPE transfer_status_enum AS  ENUM (
    'Completed', 'Reversed'
);
CREATE TYPE usage_status_enum AS ENUM (
    'Completed', 'Reversed'
);
CREATE TYPE entry_type_enum AS ENUM (
    'Purchase', 'Treatment', 'TransferOut', 'TransferIn', 'Adjustment', 'Reversal'
);
CREATE TYPE event_type_enum AS ENUM(
    'Issued', 'Reprint'
);
CREATE TYPE cancellation_type_enum AS ENUM(
    'Void', 'Refund', 'UsageReversal', 'TransferReversal'
);
CREATE TYPE refund_status_enum AS ENUM (
    'Pending', 'Completed', 'Failed', 'Voided'
);
CREATE TYPE commission_allocation_entry_type_enum AS ENUM (
    'Allocation', 'Reversal', 'Adjustment'
);
CREATE TYPE overflow_policy_enum AS ENUM (
    'CapAtCommission', 'CompanyTopUp', 'BlockAndRequireApproval'
);
CREATE TYPE audit_action_enum AS ENUM (
    'INSERT', 'UPDATE' ,'DELETE'
);



-- no Reference tables
CREATE TABLE branch (
    branch_id BIGSERIAL PRIMARY KEY,        -- auto-increment
    branch_code CHAR(2) NOT NULL UNIQUE,    -- NN ; 0-99
    branch_name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    branch_status BOOLEAN NOT NULL DEFAULT TRUE,    -- 1 : Active, 0 : Inactive

    branch_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    branch_create_by BIGINT NOT NULL,
    branch_update_at TIMESTAMP,
    branch_update_by BIGINT,

    -- NN ; Two-digit branch code: 00-99
    CONSTRAINT chk_branch_code
        CHECK (branch_code ~ '^[0-9]{2}$')

    -- Circular Foreign Key : branch_create_by, branch_update_by
);

CREATE TABLE role (
    role_id BIGSERIAL PRIMARY KEY,          -- auto-increment
    role_name VARCHAR(50) NOT NULL UNIQUE,

    role_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    role_create_by BIGINT NOT NULL,
    role_update_at TIMESTAMP,
    role_update_by BIGINT

    -- Circular Foreign Key : role_create_by, role_update_by
);

CREATE TABLE service (
    service_id BIGSERIAL PRIMARY KEY,                   -- auto-increment
    service_type service_type_enum NOT NULL,
    service_name VARCHAR(150) NOT NULL,
    service_price NUMERIC(12, 2) NOT NULL,      -- >=0
    service_status BOOLEAN NOT NULL DEFAULT TRUE,       -- 1 : Active, 0 : Inactive
    service_code VARCHAR(20) NOT NULL UNIQUE,

    service_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    service_create_by BIGINT NOT NULL,
    service_update_at TIMESTAMP,
    service_update_by BIGINT,

    default_total_visits INT,                   -- >=1
    default_bonus_visits INT,                   -- >=0
    default_validity_days INT,                  -- >=0

    CONSTRAINT chk_service_price
        CHECK (service_price >= 0),

    CONSTRAINT chk_service_default_total_visits
        CHECK (default_total_visits >= 1),

    CONSTRAINT chk_service_default_bonus_visits
        CHECK (default_bonus_visits >= 0),

    CONSTRAINT chk_service_default_validity_days
        CHECK (default_validity_days >= 0)

    -- Circular Foreign Key : service_create_by, service_update_by
);

CREATE TABLE permission(
    permission_id BIGSERIAL PRIMARY KEY,            -- auto-increment
    permission_name VARCHAR(100) NOT NULL UNIQUE,
    module_name VARCHAR(50),

    permission_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    permission_create_by BIGINT NOT NULL,
    permission_update_at TIMESTAMP,
    permission_update_by BIGINT

    -- Circular Foreign Key : permission_create_by, permission_update_by
);

CREATE TABLE payment_method(
    payment_method_id BIGSERIAL PRIMARY KEY,                -- auto-increment
    method_name VARCHAR(50) NOT NULL,
    payment_method_status BOOLEAN NOT NULL DEFAULT TRUE,    -- 1 : Enabled, 0 : Disabled

    payment_method_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_method_create_by BIGINT NOT NULL,
    payment_method_update_at TIMESTAMP,
    payment_method_update_by BIGINT

    -- Circular Foreign Key : payment_method_create_by, payment_method_update_by
);

CREATE TABLE room(
    room_id BIGSERIAL PRIMARY KEY,                  -- auto-increment
    branch_id BIGINT NOT NULL,
    room_name VARCHAR(100) NOT NULL,
    room_type VARCHAR(50),
    room_status BOOLEAN NOT NULL DEFAULT TRUE,      -- 1 : Enabled, 0 : Disabled

    room_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    room_create_by BIGINT NOT NULL,
    room_update_at TIMESTAMP,
    room_update_by BIGINT,

    CONSTRAINT fk_room_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id)

    -- Circular Foreign Key : room_create_by, room_update_by
);

CREATE TABLE master_data_type(
    type_id BIGSERIAL PRIMARY KEY,                  -- auto-increment
    type_name VARCHAR(50) NOT NULL UNIQUE,

    type_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    type_create_by BIGINT NOT NULL,
    type_update_at TIMESTAMP,
    type_update_by BIGINT

    -- Circular Foreign Key : type_create_by, type_update_by
);


-- Staff
CREATE TABLE staff (
    staff_id BIGSERIAL PRIMARY KEY,                 -- auto-increment
    staff_code VARCHAR(20) NOT NULL UNIQUE,
    staff_fname VARCHAR(100) NOT NULL,
    staff_lname VARCHAR(100) NOT NULL,
    position staff_position_enum NOT NULL,
    branch_id BIGINT NOT NULL,
    staff_status BOOLEAN NOT NULL DEFAULT TRUE,     -- 1 : Active, 0 : Inactive

    termination_date DATE,
    hire_date DATE NOT NULL,
    commission_after_termination_policy commission_after_termination_policy_enum,

    staff_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    staff_create_by BIGINT NOT NULL,
    staff_update_at TIMESTAMP,
    staff_update_by BIGINT,
    termination_policy_locked_at TIMESTAMP,

    CONSTRAINT fk_staff_branch
       FOREIGN KEY (branch_id)
           REFERENCES branch(branch_id)

    -- Circular Foreign Key : staff_create_by, staff_update_by
);


-- User and Permission tables
CREATE TABLE app_user (
    user_id BIGSERIAL PRIMARY KEY,                -- auto-increment
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    staff_id BIGINT,
    user_status BOOLEAN NOT NULL DEFAULT TRUE,    -- -- 1 : Active, 0 : Inactive ; active = Account can be use

    last_login_at TIMESTAMP NOT NULL,
    user_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_create_by BIGINT,
    user_update_at TIMESTAMP,
    user_update_by BIGINT,

    CONSTRAINT fk_user_staff
      FOREIGN KEY (staff_id)
          REFERENCES staff(staff_id),

    CONSTRAINT fk_app_user_create_by
        FOREIGN KEY (user_create_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_app_user_update_by
        FOREIGN KEY (user_update_by)
            REFERENCES app_user(user_id)

    -- Circular Foreign Key : user_create_by, user_update_by
);

CREATE TABLE user_role (
   user_id BIGINT NOT NULL,             -- auto-increment
   role_id BIGINT NOT NULL,             -- auto-increment

   CONSTRAINT pk_user_role
       PRIMARY KEY (user_id, role_id),

   CONSTRAINT fk_user_role_user
       FOREIGN KEY (user_id)
           REFERENCES app_user(user_id),

   CONSTRAINT fk_user_role_role
       FOREIGN KEY (role_id)
           REFERENCES role(role_id)
);

CREATE TABLE role_permission(
    role_id BIGINT NOT NULL,            -- auto-increment
    permission_id BIGINT NOT NULL,      -- auto-increment

    CONSTRAINT pk_role_permission
        PRIMARY KEY (role_id, permission_id),

    CONSTRAINT fk_role_permission_role
        FOREIGN KEY (role_id)
            REFERENCES role(role_id),

    CONSTRAINT fk_role_permission_permission
        FOREIGN KEY (permission_id)
            REFERENCES permission(permission_id)
);

CREATE TABLE user_branch_access(
    user_id BIGINT NOT NULL,            -- auto-increment
    branch_id BIGINT NOT NULL,          -- auto-increment

    CONSTRAINT pk_user_branch_access
       PRIMARY KEY (user_id, branch_id),

    CONSTRAINT fk_user_branch_access_user
        FOREIGN KEY (user_id)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_user_branch_access_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id)
);


-- Master Data and Groups
CREATE TABLE master_data_value(
    value_id BIGSERIAL PRIMARY KEY,                 -- auto-increment
    type_id BIGINT NOT NULL,
    value_name VARCHAR(100) NOT NULL,
    value_status BOOLEAN NOT NULL DEFAULT TRUE,     -- 1 : Enabled, 0 : Disabled
    sort_order INT,                                 -- >=0

    value_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    value_create_by BIGINT NOT NULL,
    value_update_at TIMESTAMP,
    value_update_by BIGINT,

    CONSTRAINT fk_master_data_value_type
      FOREIGN KEY (type_id)
          REFERENCES master_data_type(type_id),

    CONSTRAINT fk_master_data_value_create_by
        FOREIGN KEY (value_create_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_master_data_value_update_by
        FOREIGN KEY (value_update_by)
            REFERENCES app_user(user_id),

    CONSTRAINT chk_master_data_value_sort_order
        CHECK (sort_order >= 0)
);

CREATE TABLE service_group(
    service_group_id BIGSERIAL PRIMARY KEY,         -- auto-increment
    group_name VARCHAR(100) NOT NULL,

    service_group_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    service_group_create_by BIGINT NOT NULL,
    service_group_update_at TIMESTAMP,
    service_group_update_by BIGINT,

    CONSTRAINT fk_service_group_create_by
        FOREIGN KEY (service_group_create_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_service_group_update_by
        FOREIGN KEY (service_group_update_by)
            REFERENCES app_user(user_id)
);

CREATE TABLE service_group_member(
    service_group_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,

    CONSTRAINT pk_service_group_member
        PRIMARY KEY (service_group_id, service_id),

    CONSTRAINT fk_service_group_member_service_group
        FOREIGN KEY (service_group_id)
            REFERENCES service_group(service_group_id),

    CONSTRAINT fk_service_group_member_service
        FOREIGN KEY (service_id)
            REFERENCES service(service_id)
);

CREATE TABLE staff_group(
    staff_group_id BIGSERIAL PRIMARY KEY,           -- auto-increment
    group_name VARCHAR(100) NOT NULL,

    staff_group_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    staff_group_create_by BIGINT NOT NULL,
    staff_group_update_at TIMESTAMP,
    staff_group_update_by BIGINT,

    CONSTRAINT fk_staff_group_create_by
        FOREIGN KEY (staff_group_create_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_staff_group_update_by
        FOREIGN KEY (staff_group_update_by)
            REFERENCES app_user(user_id)
);

CREATE TABLE staff_group_member(
    staff_group_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,

    CONSTRAINT pk_staff_group_member
        PRIMARY KEY (staff_group_id, staff_id),

    CONSTRAINT fk_staff_group_member_staff_group
        FOREIGN KEY (staff_group_id)
            REFERENCES staff_group(staff_group_id),

    CONSTRAINT fk_staff_group_member_staff
        FOREIGN KEY (staff_id)
            REFERENCES staff(staff_id)
);

CREATE TABLE system_setting(
    setting_key VARCHAR(50) PRIMARY KEY,
    permission_id BIGINT NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    description VARCHAR(255),

    setting_update_at TIMESTAMP,
    setting_update_by BIGINT,

    CONSTRAINT fk_system_setting_permission
        FOREIGN KEY (permission_id)
            REFERENCES permission(permission_id),

    CONSTRAINT fk_system_setting_update_by
        FOREIGN KEY (setting_update_by)
            REFERENCES app_user(user_id)
);


-- Patient tables
CREATE TABLE patient (
    patient_id BIGSERIAL PRIMARY KEY,               -- auto-increment
    patient_hn CHAR(10) NOT NULL UNIQUE,            -- YYBBMMNNNN
    customer_type customer_type_enum NOT NULL,
    title title_enum NOT NULL,
    patient_fname VARCHAR(100) NOT NULL,
    patient_lname VARCHAR(100) NOT NULL,
    gender gender_enum NOT NULL,
    phone VARCHAR(20),                              -- Thai: required; Foreigner: optional
    branch_id BIGINT NOT NULL,
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    gender_detail VARCHAR(50),
    patient_nickname VARCHAR(50),
    national_id CHAR(13),                   -- x-xxxx-xxxxx-xx-x ; Thai: 13 digits required; foreigner: optional
    passport_no VARCHAR(20),
    birthdate DATE,
    blood_group blood_group_enum,
    nationality VARCHAR(50),
    address VARCHAR(255),

    registered_by BIGINT NOT NULL,
    patient_update_at TIMESTAMP,
    patient_update_by BIGINT,

    customer_group_value_id BIGINT,
    referral_channel_value_id BIGINT,
    insurance_type_value_id BIGINT,
    insurance_company_value_id BIGINT,

    -- YYBBMMNNNN
    CONSTRAINT chk_patient_hn
        CHECK (patient_hn ~ '^[0-9]{10}$'),

    -- x-xxxx-xxxxx-xx-x
    CONSTRAINT chk_patient_national_id
        CHECK (national_id ~ '^[0-9]{13}$'),

    CONSTRAINT fk_patient_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id),

    CONSTRAINT fk_patient_registered_by
        FOREIGN KEY (registered_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_patient_update_by
        FOREIGN KEY (patient_update_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_patient_customer_group
        FOREIGN KEY (customer_group_value_id)
            REFERENCES master_data_value(value_id),

    CONSTRAINT fk_patient_referral_channel
        FOREIGN KEY (referral_channel_value_id)
            REFERENCES master_data_value(value_id),

    CONSTRAINT fk_patient_insurance_type
        FOREIGN KEY (insurance_type_value_id)
            REFERENCES master_data_value(value_id),

    CONSTRAINT fk_patient_insurance_company
        FOREIGN KEY (insurance_company_value_id)
            REFERENCES master_data_value(value_id)
);

CREATE TABLE hn_sequence (
    branch_id BIGINT NOT NULL,
    year_month CHAR(4) NOT NULL,            -- YYMM
    last_running_no INT NOT NULL DEFAULT 0, -- >=0

    last_running_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- YYMM
    CONSTRAINT chk_hn_sequence_year_month
        CHECK (year_month ~ '^[0-9]{4}$'),

    CONSTRAINT chk_hn_sequence_running_no
        CHECK (last_running_no >= 0),

    CONSTRAINT pk_hn_sequence
        PRIMARY KEY (branch_id, year_month),

    CONSTRAINT fk_hn_sequence_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id)
);


-- Appointment tables
CREATE TABLE appointment (
    appointment_id BIGSERIAL PRIMARY KEY,   -- auto-increment
    patient_id BIGINT,                      -- CURRENT: required; NEW: nullable
    branch_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    service_id BIGINT,
    appointment_status appointment_status_enum NOT NULL,
    room_id BIGINT,

    appointment_date DATE NOT NULL,
    appointment_time TIME NOT NULL,

    appointment_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    appointment_create_by BIGINT NOT NULL,
    appointment_update_at TIMESTAMP,
    appointment_update_by BIGINT,

    appointment_patient_type appointment_patient_type_enum NOT NULL,
    contact_name VARCHAR(200),                  -- NEW: ชื่อ–นามสกุล หรือชื่อเล่นพร้อมเบอร์โทร; CURRENT: ใช้ข้อมูล Patient
    contact_phone VARCHAR(20),                  -- NEW: บังคับเมื่อใช้ชื่อเล่น; ชื่อ–นามสกุลอย่างเดียวรับได้ตาม feedback
    requested_service_type requested_service_type_enum,
    external_calendar_event_id VARCHAR(255),    -- Pending integration scope
    calendar_sync_status calendar_sync_status_enum,


    CONSTRAINT chk_appointment_patient_type
        CHECK (
            appointment_patient_type <> 'CURRENT' OR patient_id IS NOT NULL     -- CURRENT ต้องระบุ patient_id
        ),

    CONSTRAINT fk_appointment_patient
        FOREIGN KEY (patient_id)
            REFERENCES patient(patient_id),

    CONSTRAINT fk_appointment_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id),

    CONSTRAINT fk_appointment_staff
        FOREIGN KEY (staff_id)
            REFERENCES staff(staff_id),

    CONSTRAINT fk_appointment_service
        FOREIGN KEY (service_id)
            REFERENCES service(service_id),

    CONSTRAINT fk_appointment_room
        FOREIGN KEY (room_id)
            REFERENCES room(room_id),

    CONSTRAINT fk_appointment_create_by
        FOREIGN KEY (appointment_create_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_appointment_update_by
        FOREIGN KEY (appointment_update_by)
            REFERENCES app_user(user_id)
);

CREATE TABLE appointment_status_history (
    history_id BIGSERIAL PRIMARY KEY,           -- auto-increment
    appointment_id BIGINT NOT NULL,
    from_status appointment_status_enum,
    to_status appointment_to_status_enum NOT NULL,
    changed_by BIGINT NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(255),

    CONSTRAINT fk_appointment_status_history_appointment
        FOREIGN KEY (appointment_id)
            REFERENCES appointment(appointment_id),

    CONSTRAINT fk_appointment_status_history_changed_by
        FOREIGN KEY (changed_by)
            REFERENCES app_user(user_id)
);


-- Visit tables
CREATE TABLE patient_visit (
    visit_id BIGSERIAL PRIMARY KEY,             -- auto-increment
    appointment_id BIGINT,
    patient_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    assigned_staff_id BIGINT,
    visit_date TIMESTAMP NOT NULL,
    checkout_time TIMESTAMP,
    visit_status visit_status_enum NOT NULL,

    visit_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    visit_create_by BIGINT NOT NULL,
    visit_update_at TIMESTAMP,
    visit_update_by BIGINT,

    CONSTRAINT fk_patient_visit_appointment
        FOREIGN KEY (appointment_id)
            REFERENCES appointment(appointment_id),

    CONSTRAINT fk_patient_visit_patient
        FOREIGN KEY (patient_id)
            REFERENCES patient(patient_id),

    CONSTRAINT fk_patient_visit_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id),

    CONSTRAINT fk_patient_visit_assigned_staff
        FOREIGN KEY (assigned_staff_id)
            REFERENCES staff(staff_id),

    CONSTRAINT fk_patient_visit_create_by
        FOREIGN KEY (visit_create_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_patient_visit_update_by
        FOREIGN KEY (visit_update_by)
            REFERENCES app_user(user_id)
);


-- Commission before course Tables
CREATE TABLE commission_scheme(
    commission_scheme_id BIGSERIAL PRIMARY KEY,     -- auto-increment
    scheme_name VARCHAR(100) NOT NULL,
    scheme_version INT NOT NULL,                    -- >= 1
    permission_id BIGINT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,

    scheme_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scheme_create_by BIGINT NOT NULL,
    scheme_update_at TIMESTAMP,
    scheme_update_by BIGINT,


    -- >=1; historical version immutable once used
    CONSTRAINT chk_commission_scheme_version
        CHECK (scheme_version >= 1),
    CONSTRAINT chk_commission_scheme_effective_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from),

    CONSTRAINT fk_commission_scheme_permission
        FOREIGN KEY (permission_id)
            REFERENCES permission(permission_id),

    CONSTRAINT fk_commission_scheme_create_by
        FOREIGN KEY (scheme_create_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_commission_scheme_update_by
        FOREIGN KEY (scheme_update_by)
            REFERENCES app_user(user_id)
);

CREATE TABLE commission_tier(
    tier_id BIGSERIAL PRIMARY KEY,                  -- auto-increment

    commission_scheme_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    tier_order INT NOT NULL,                        -- >= 1

    minimum_monthly_sales NUMERIC(12, 2) NOT NULL,  -- >= 0 ; no overlap/gaps
    maximum_monthly_sales NUMERIC(12, 2),           -- >= minimum_monthly_sales
    commission_rate NUMERIC(5, 4) NOT NULL,         -- 0 to 1
    tier_status BOOLEAN NOT NULL DEFAULT TRUE,      -- 1 : Enabled, 0 : Disabled

    effective_from DATE NOT NULL,
    effective_to DATE,

    tier_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tier_create_by BIGINT NOT NULL,
    tier_update_at TIMESTAMP,
    tier_update_by BIGINT,

    CONSTRAINT chk_commission_tier_order
        CHECK (tier_order >= 1),

    CONSTRAINT chk_commission_tier_minimum_sales
        CHECK (minimum_monthly_sales >= 0),

    CONSTRAINT chk_commission_tier_maximum_sales
        CHECK (maximum_monthly_sales >= minimum_monthly_sales),

    CONSTRAINT chk_commission_tier_rate
        CHECK (commission_rate BETWEEN 0 AND 1),

    CONSTRAINT fk_commission_tier_scheme
        FOREIGN KEY (commission_scheme_id)
            REFERENCES commission_scheme(commission_scheme_id),

    CONSTRAINT fk_commission_tier_permission
        FOREIGN KEY (permission_id)
            REFERENCES permission(permission_id),

    CONSTRAINT fk_commission_tier_create_by
        FOREIGN KEY (tier_create_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_commission_tier_update_by
        FOREIGN KEY (tier_update_by)
            REFERENCES app_user(user_id)
);

CREATE TABLE treatment_fee_rule(
    treatment_fee_rule_id BIGSERIAL PRIMARY KEY,    -- auto-increment
    permission_id BIGINT NOT NULL,
    staff_id BIGINT,
    staff_group_id BIGINT,
    service_id BIGINT,
    service_group_id BIGINT,

    fee_type fee_type_enum NOT NULL,
    fee_value NUMERIC(12, 4) NOT NULL,              -- >= 0
    percentage_base percentage_base_enum,

    effective_from DATE NOT NULL,
    effective_to DATE,
    treatment_fee_rule_status BOOLEAN NOT NULL DEFAULT TRUE,

    treatment_fee_rule_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    treatment_fee_rule_create_by BIGINT NOT NULL,
    treatment_fee_rule_update_at TIMESTAMP,
    treatment_fee_rule_update_by BIGINT,

    CONSTRAINT chk_treatment_fee_rule_value
        CHECK (fee_value >= 0),

    -- require percentage_base when fee_type is 'Percentage'
    CONSTRAINT chk_treatment_fee_rule_percentage_base
        CHECK (
            (fee_type = 'Percentage' AND percentage_base IS NOT NULL)
                OR (fee_type = 'Fixed' AND percentage_base IS NULL)
            ),

    CONSTRAINT fk_treatment_fee_rule_permission
        FOREIGN KEY (permission_id)
            REFERENCES permission(permission_id),

    CONSTRAINT fk_treatment_fee_rule_staff
        FOREIGN KEY (staff_id)
            REFERENCES staff(staff_id),

    CONSTRAINT fk_treatment_fee_rule_staff_group
        FOREIGN KEY (staff_group_id)
            REFERENCES staff_group(staff_group_id),

    CONSTRAINT fk_treatment_fee_rule_service
        FOREIGN KEY (service_id)
            REFERENCES service(service_id),

    CONSTRAINT fk_treatment_fee_rule_service_group
        FOREIGN KEY (service_group_id)
            REFERENCES service_group(service_group_id),

    CONSTRAINT fk_treatment_fee_rule_create_by
        FOREIGN KEY (treatment_fee_rule_create_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_treatment_fee_rule_update_by
        FOREIGN KEY (treatment_fee_rule_update_by)
            REFERENCES app_user(user_id)
);


-- Receipt Tables
CREATE TABLE receipt (
    receipt_id BIGSERIAL PRIMARY KEY,           -- auto-increment
    receipt_no VARCHAR(30) NOT NULL UNIQUE,     -- RE-xxxxxxx

    patient_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    sale_date TIMESTAMP NOT NULL,

    receipt_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    receipt_create_by BIGINT NOT NULL,
    receipt_update_at TIMESTAMP,
    receipt_update_by BIGINT,

    subtotal_before_discount DECIMAL(12,2) NOT NULL,        -- >=0
    bill_discount_type bill_discount_type_enum,
    bill_discount_value DECIMAL(12,4),
    bill_discount_amount DECIMAL(12, 2) NOT NULL,           -- >=0
    taxable_amount DECIMAL(12,2) NOT NULL,                  -- >=0; tax policy to confirm
    tax_amount DECIMAL(12,2) NOT NULL,                      -- >=0; tax policy to confirm
    net_amount DECIMAL(12,2) NOT NULL,                      -- >=0
    receipt_status receipt_status_enum NOT NULL,


    -- RE-xxxxxxx
    CONSTRAINT chk_receipt_no
        CHECK (receipt_no ~ '^RE-[0-9]{7}$'),

    CONSTRAINT chk_receipt_subtotal_before_discount
        CHECK (subtotal_before_discount >= 0),

    -- Fixed >=0; Percentage 0..100
    CONSTRAINT chk_receipt_bill_discount
        CHECK (
            (bill_discount_type IS NULL
                AND bill_discount_value IS NULL
                AND bill_discount_amount = 0)
                OR
            (bill_discount_type = 'Fixed'
                AND bill_discount_value IS NOT NULL
                AND bill_discount_value >= 0)
                OR
            (bill_discount_type = 'Percentage'
                AND bill_discount_value IS NOT NULL
                AND bill_discount_value BETWEEN 0 AND 100)
            ),

    CONSTRAINT chk_receipt_bill_discount_amount
        CHECK (bill_discount_amount >= 0),

    CONSTRAINT chk_receipt_taxable_amount
        CHECK (taxable_amount >= 0),

    CONSTRAINT chk_receipt_tax_amount
        CHECK (tax_amount >= 0),

    CONSTRAINT chk_receipt_net_amount
        CHECK (net_amount >= 0),


    CONSTRAINT fk_receipt_patient
        FOREIGN KEY (patient_id)
            REFERENCES patient(patient_id),

    CONSTRAINT fk_receipt_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id),

    CONSTRAINT fk_receipt_create_by
        FOREIGN KEY (receipt_create_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_receipt_update_by
        FOREIGN KEY (receipt_update_by)
            REFERENCES app_user(user_id)
);

-- Course and Sales Tables
CREATE TABLE course(
    course_id BIGSERIAL PRIMARY KEY,            -- auto-increment for Link Table
    course_no VARCHAR(30) NOT NULL UNIQUE,      -- course id for Business format: RE-xxxxxxx or RE-xxxxxxx-xx
    course_sequence INT NOT NULL DEFAULT 1,     -- กรณีมีหลาย Course ต่อ Receipt; >= 1
    course_name VARCHAR(255) NOT NULL,
    course_price NUMERIC(12, 2) NOT NULL,       -- >=0; equals commissionable_amount [Sales_transaction]

    total_visits INT NOT NULL,                  -- >=1
    bonus_visits INT,                           -- >=0
    expiry_date DATE NOT NULL,

    course_status course_status_enum NOT NULL,
    service_id BIGINT NOT NULL,

    course_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    course_create_by BIGINT NOT NULL,
    course_update_at TIMESTAMP,
    course_update_by BIGINT,

    receipt_id BIGINT NOT NULL,
    sale_month CHAR(7) NOT NULL,                -- yyyy-mm

    seller_staff_id BIGINT NOT NULL,
    case_owner_staff_id BIGINT NOT NULL,

    commission_status commission_status_enum NOT NULL,
    commission_scheme_id BIGINT NOT NULL,
    commission_scheme_version INT NOT NULL,         -- >=1

    provisional_commission_rate NUMERIC(5, 4),      -- 0-1
    locked_commission_rate NUMERIC(5, 4),           -- 0-1
    total_course_commission_pool NUMERIC(12, 2),    -- >=0
    commission_allocation_per_visit NUMERIC(12, 2), -- >=0
    commission_generated_date DATE,

    visits_used INT NOT NULL DEFAULT 0,             -- >=0
    remaining_visits INT NOT NULL,                  -- >=0

    gross_commission_allocated_total NUMERIC(12, 2) NOT NULL,        -- >=0
    owner_net_commission_released_total NUMERIC(12, 2) NOT NULL,     -- >=0
    substitute_treatment_fee_total NUMERIC(12, 2) NOT NULL,          -- >=0
    commission_outstanding NUMERIC(12, 2) NOT NULL,                  -- >=0

    purchaser_patient_id BIGINT NOT NULL,
    sale_transaction_id BIGINT NOT NULL UNIQUE,     -- Unique; one course per sale line
    entitled_visits INT GENERATED ALWAYS AS                 -- >=1; derived; not independently editable
        (total_visits + COALESCE(bonus_visits, 0)) STORED,

    CONSTRAINT chk_course_no
        CHECK (course_no ~ '^RE-[0-9]{7}(-[0-9]{2})?$'),    -- เมื่อมี 2 courses ขึ้นไป ใน 1 RE ใช้เพิ่ม -xx ; ถ้าไม่มี ไม่ต้องเพิ่ม

    CONSTRAINT chk_course_sequence
        CHECK (course_sequence >= 1),

    CONSTRAINT chk_course_price
        CHECK (course_price >= 0),

    CONSTRAINT chk_course_commission_amounts
        CHECK (
            (total_course_commission_pool IS NULL
                OR total_course_commission_pool >= 0)
            AND (commission_allocation_per_visit IS NULL
                OR commission_allocation_per_visit >= 0)
            AND gross_commission_allocated_total >= 0
            AND owner_net_commission_released_total >= 0
            AND substitute_treatment_fee_total >= 0
            AND commission_outstanding >= 0
        ),

    CONSTRAINT chk_course_total_visits
       CHECK (total_visits >= 1),

    CONSTRAINT chk_course_bonus_visits
       CHECK (bonus_visits >= 0),

    -- yyyy-mm
    CONSTRAINT chk_course_sale_month
       CHECK (sale_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),

    CONSTRAINT chk_course_commission_scheme_version
       CHECK (commission_scheme_version >= 1),

    CONSTRAINT chk_course_provisional_rate
       CHECK (provisional_commission_rate BETWEEN 0 AND 1),

    CONSTRAINT chk_course_locked_rate
       CHECK (locked_commission_rate BETWEEN 0 AND 1),

    CONSTRAINT chk_course_visits_used
       CHECK (visits_used >= 0),

    CONSTRAINT chk_course_remaining_visits
       CHECK (remaining_visits >= 0),

    CONSTRAINT chk_course_entitled_visits
        CHECK (entitled_visits >= 1),


    CONSTRAINT fk_course_service
        FOREIGN KEY (service_id)
            REFERENCES service(service_id),

    CONSTRAINT fk_course_create_by
        FOREIGN KEY (course_create_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_course_update_by
        FOREIGN KEY (course_update_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_course_receipt
        FOREIGN KEY (receipt_id)
            REFERENCES receipt(receipt_id),

    CONSTRAINT fk_course_seller_staff
        FOREIGN KEY (seller_staff_id)
            REFERENCES staff(staff_id),

    CONSTRAINT fk_course_case_owner_staff
        FOREIGN KEY (case_owner_staff_id)
            REFERENCES staff(staff_id),

    CONSTRAINT fk_course_commission_scheme
        FOREIGN KEY (commission_scheme_id)
            REFERENCES commission_scheme(commission_scheme_id),

    CONSTRAINT fk_course_purchaser_patient
        FOREIGN KEY (purchaser_patient_id)
            REFERENCES patient (patient_id)

    -- Circular Foreign Key : sale_transaction_id

);

CREATE TABLE course_shared_member(
    course_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    member_role course_member_role_enum NOT NULL,
    member_status BOOLEAN NOT NULL DEFAULT TRUE,        -- 1 : Active, 0 : Inactive

    joined_at TIMESTAMP NOT NULL DEFAULT  CURRENT_TIMESTAMP,
    ended_at TIMESTAMP,

    CONSTRAINT pk_course_shared_member
        PRIMARY KEY (course_id, patient_id),

    CONSTRAINT fk_course_shared_member_course
        FOREIGN KEY (course_id)
            REFERENCES course (course_id),

    CONSTRAINT fk_course_shared_member_patient
        FOREIGN KEY (patient_id)
            REFERENCES patient (patient_id)
);
-- member_role; one active Owner per course
CREATE UNIQUE INDEX uq_course_shared_member_active_owner
    ON course_shared_member (course_id)
    WHERE member_role = 'Owner' AND member_status = TRUE;

CREATE TABLE course_entitlement(
    course_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,

    allocated_visits INT NOT NULL DEFAULT 0,                -- >=0
    used_visits INT NOT NULL DEFAULT 0,                     -- >=0
    remaining_visits INT NOT NULL DEFAULT 0,                -- >=0
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_course_entitlement
        PRIMARY KEY (course_id, patient_id),

    CONSTRAINT chk_course_entitlement_visits
        CHECK (
            allocated_visits >= 0
                AND used_visits >= 0
                AND remaining_visits >= 0
            ),

    CONSTRAINT fk_course_entitlement_member
        FOREIGN KEY (course_id, patient_id)
            REFERENCES course_shared_member (course_id, patient_id)
);

CREATE TABLE course_sale_staff_share(
    course_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    share_percent NUMERIC(5, 2)  NOT NULL,                  -- >0, <=100; per-course total = 100
    credited_sales_amount NUMERIC(12, 2) NOT NULL,          -- >=0; per-course sum = course_price
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,

    CONSTRAINT pk_course_sale_staff_share
        PRIMARY KEY (course_id, staff_id),

    CONSTRAINT chk_course_sale_staff_share_percent
        CHECK (share_percent > 0 AND share_percent <= 100),

    CONSTRAINT chk_course_sale_staff_share_amount
        CHECK (credited_sales_amount >= 0),

    CONSTRAINT fk_course_sale_staff_share_course
        FOREIGN KEY (course_id) REFERENCES course (course_id),

    CONSTRAINT fk_course_sale_staff_share_staff
        FOREIGN KEY (staff_id) REFERENCES staff (staff_id),

    CONSTRAINT fk_course_sale_staff_share_created_by
        FOREIGN KEY (created_by) REFERENCES app_user (user_id)
);

CREATE TABLE sales_transaction
(
    transaction_id        BIGSERIAL PRIMARY KEY,                       -- auto-increment
    visit_id              BIGINT,
    patient_id            BIGINT                NOT NULL,
    branch_id             BIGINT                NOT NULL,
    service_id            BIGINT                NOT NULL,
    transaction_type      transaction_type_enum NOT NULL,
    treating_staff_id     BIGINT,
    salesperson_staff_id  BIGINT,

    quantity              INT                   NOT NULL,              -- >=1; CoursePurchase = 1 per sale line
    unit_price            NUMERIC(12, 2)        NOT NULL,              -- >=0
    amount                NUMERIC(12, 2)        NOT NULL,              -- >=0
    transaction_date      TIMESTAMP             NOT NULL,
    transaction_status    BOOLEAN               NOT NULL DEFAULT TRUE, -- 1 : Completed, 0 : Cancelled

    transaction_create_at TIMESTAMP             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    transaction_create_by BIGINT                NOT NULL,
    transaction_update_at TIMESTAMP,
    transaction_update_by BIGINT,

    receipt_id BIGINT NOT NULL,
    line_no INT NOT NULL,                                               -- >=1
    line_discount_type line_discount_type_enum,
    line_discount_value NUMERIC(12, 4),                                 -- Fixed >=0; Percentage 0..100
    line_discount_amount NUMERIC(12, 2) NOT NULL,                       -- >=0
    allocated_bill_discount_amount NUMERIC(12, 2) NOT NULL,             -- >=0
    commissionable_amount NUMERIC(12, 2) NOT NULL,                      -- >=0 ; CoursePurchase only for tier
    item_name_snapshot VARCHAR(200) NOT NULL,
    unit_name_snapshot VARCHAR(30) NOT NULL,

    -- quantity >= 1; CoursePurchase = 1 per sale line
    CONSTRAINT chk_sales_transaction_quantity
        CHECK (
            quantity >= 1
                AND (transaction_type <> 'CoursePurchase' OR quantity = 1)
            ),

    -- line_no >=1; UK(receipt_id, line_no)
    CONSTRAINT uq_sales_transaction_receipt_line
        UNIQUE (receipt_id, line_no),

    CONSTRAINT chk_sales_transaction_line_no
        CHECK (line_no >= 1),

    CONSTRAINT chk_sales_transaction_amounts
        CHECK (
            unit_price >= 0
                AND amount >= 0
                AND line_discount_amount >= 0
                AND allocated_bill_discount_amount >= 0
                AND commissionable_amount >= 0
            ),

    -- line_discount_value ; Fixed >=0, Percentage 0..100
    CONSTRAINT chk_sales_transaction_line_discount
        CHECK (
            (line_discount_type IS NULL
                AND line_discount_value IS NULL
                AND line_discount_amount = 0)
                OR
            (line_discount_type = 'Fixed'
                AND line_discount_value IS NOT NULL
                AND line_discount_value >= 0)
                OR
            (line_discount_type = 'Percentage'
                AND line_discount_value IS NOT NULL
                AND line_discount_value BETWEEN 0 AND 100)
            ),

    CONSTRAINT fk_sales_transaction_visit
        FOREIGN KEY (visit_id)
            REFERENCES patient_visit (visit_id),

    CONSTRAINT fk_sales_transaction_patient
        FOREIGN KEY (patient_id)
            REFERENCES patient (patient_id),

    CONSTRAINT fk_sales_transaction_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch (branch_id),

    CONSTRAINT fk_sales_transaction_service
        FOREIGN KEY (service_id)
            REFERENCES service (service_id),

    CONSTRAINT fk_sales_transaction_treating_staff
        FOREIGN KEY (treating_staff_id)
            REFERENCES staff (staff_id),

    CONSTRAINT fk_sales_transaction_salesperson_staff
        FOREIGN KEY (salesperson_staff_id)
            REFERENCES staff (staff_id),

    CONSTRAINT fk_sales_transaction_create_by
        FOREIGN KEY (transaction_create_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_sales_transaction_update_by
        FOREIGN KEY (transaction_update_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_sales_transaction_receipt
        FOREIGN KEY (receipt_id)
            REFERENCES receipt (receipt_id)
);


-- Treatment tables
CREATE TABLE treatment(
    treatment_id         BIGSERIAL PRIMARY KEY, -- auto-increment
    visit_id             BIGINT    NOT NULL,
    service_id           BIGINT    NOT NULL,
    treating_staff_id    BIGINT,
    sales_transaction_id BIGINT,
    notes                TEXT,

    treatment_create_by  BIGINT    NOT NULL,
    treatment_create_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    treatment_update_at  TIMESTAMP,
    treatment_update_by  BIGINT,

    CONSTRAINT fk_treatment_visit
        FOREIGN KEY (visit_id)
            REFERENCES patient_visit (visit_id),

    CONSTRAINT fk_treatment_service
        FOREIGN KEY (service_id)
            REFERENCES service (service_id),

    CONSTRAINT fk_treatment_treating_staff
        FOREIGN KEY (treating_staff_id)
            REFERENCES staff (staff_id),

    CONSTRAINT fk_treatment_sales_transaction
        FOREIGN KEY (sales_transaction_id)
            REFERENCES sales_transaction (transaction_id),

    CONSTRAINT fk_treatment_create_by
        FOREIGN KEY (treatment_create_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_treatment_update_by
        FOREIGN KEY (treatment_update_by)
            REFERENCES app_user (user_id)
);

CREATE TABLE course_transfer(
    transfer_id BIGSERIAL PRIMARY KEY,                  -- auto-increment
    course_id BIGINT NOT NULL,
    from_patient_id BIGINT NOT NULL,
    to_patient_id BIGINT NOT NULL,
    qty_transferred INT NOT NULL,                       -- >=1
    transfer_date TIMESTAMP NOT NULL,
    remark VARCHAR(255),
    transfer_status transfer_status_enum NOT NULL,

    transfer_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    transfer_create_by BIGINT NOT NULL,
    transfer_update_at TIMESTAMP,
    transfer_update_by BIGINT,

    CONSTRAINT chk_course_transfer_qty
        CHECK (qty_transferred >= 1),

    CONSTRAINT chk_course_transfer_different_patient
        CHECK (from_patient_id <> to_patient_id),       -- ผู้โอนกับผู้รับคอร์สต้องเป็นคนละคนกัน

    CONSTRAINT fk_course_transfer_course
        FOREIGN KEY (course_id)
            REFERENCES course(course_id),

    CONSTRAINT fk_course_transfer_from_patient
        FOREIGN KEY (from_patient_id)
            REFERENCES patient(patient_id),

    CONSTRAINT fk_course_transfer_to_patient
        FOREIGN KEY (to_patient_id)
            REFERENCES patient(patient_id),

    CONSTRAINT fk_course_transfer_create_by
        FOREIGN KEY (transfer_create_by)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_course_transfer_update_by
        FOREIGN KEY (transfer_update_by)
            REFERENCES app_user(user_id)
);

CREATE TABLE course_visit_usage(
    visit_usage_id BIGSERIAL PRIMARY KEY,           -- auto-increment
    visit_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    treating_staff_id BIGINT NOT NULL,
    case_owner_staff_id BIGINT NOT NULL,

    visit_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    visit_qty INT NOT NULL,                                         -- =1

    commission_allocation_per_visit NUMERIC(12, 2),                 -- >=0
    gross_commission_allocation NUMERIC(12, 2),                     -- >=0
    treatment_fee_rule_id BIGINT,
    substitute_treatment_fee_amount NUMERIC(12, 2),                 -- >=0
    owner_net_commission NUMERIC(12, 2),                   -- >=0

    usage_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usage_create_by BIGINT NOT NULL,
    usage_update_at TIMESTAMP,
    usage_update_by BIGINT,

    treatment_id BIGINT,
    usage_status usage_status_enum NOT NULL,
    print_required BOOLEAN NOT NULL,                                -- Completed usage: required = 1

    CONSTRAINT chk_course_visit_usage_qty
        CHECK (visit_qty = 1),

    CONSTRAINT chk_course_visit_usage_allocation
        CHECK (commission_allocation_per_visit >= 0),

    CONSTRAINT chk_course_visit_usage_gross_commission
        CHECK (gross_commission_allocation >= 0),

    CONSTRAINT chk_course_visit_usage_substitute_fee
        CHECK (substitute_treatment_fee_amount >= 0),

    CONSTRAINT chk_course_visit_usage_owner_commission
        CHECK (owner_net_commission >= 0),

    -- Completed usage: required = 1
    CONSTRAINT chk_course_visit_usage_print_required
        CHECK (usage_status <> 'Completed' OR print_required = TRUE),

    CONSTRAINT fk_course_visit_usage_visit
        FOREIGN KEY (visit_id)
            REFERENCES patient_visit (visit_id),

    CONSTRAINT fk_course_visit_usage_course
        FOREIGN KEY (course_id)
            REFERENCES course (course_id),

    CONSTRAINT fk_course_visit_usage_patient
        FOREIGN KEY (patient_id)
            REFERENCES patient (patient_id),

    CONSTRAINT fk_course_visit_usage_treating_staff
        FOREIGN KEY (treating_staff_id)
            REFERENCES staff (staff_id),

    CONSTRAINT fk_course_visit_usage_case_owner_staff
        FOREIGN KEY (case_owner_staff_id)
            REFERENCES staff (staff_id),

    CONSTRAINT fk_course_visit_usage_treatment_fee_rule
        FOREIGN KEY (treatment_fee_rule_id)
            REFERENCES treatment_fee_rule (treatment_fee_rule_id),

    CONSTRAINT fk_course_visit_usage_create_by
        FOREIGN KEY (usage_create_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_course_visit_usage_update_by
        FOREIGN KEY (usage_update_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_course_visit_usage_treatment
        FOREIGN KEY (treatment_id)
            REFERENCES treatment (treatment_id)
);

CREATE TABLE course_ledger(
    ledger_id        BIGSERIAL PRIMARY KEY,                     -- auto-increment
    course_id        BIGINT                        NOT NULL,
    patient_id       BIGINT                        NOT NULL,
    entry_type       entry_type_enum NOT NULL,
    qty_change       INT                           NOT NULL,
    balance_after    INT                           NOT NULL,    -- >= 0
    remark           VARCHAR(255),                              -- Required when entry_type=Adjustment

    ledger_create_at TIMESTAMP                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ledger_create_by BIGINT                        NOT NULL,

    transfer_id BIGINT,                 -- Required for TransferOut/TransferIn
    visit_usage_id BIGINT,              -- Required for Treatment; one active consumption per usage
    reverses_ledger_id BIGINT,          -- Required for Reversal; unique when
    event_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sales_transaction_id BIGINT,        -- Required for Purchase; equals Course.sale_transaction_id
    adjustment_approved_by BIGINT,      -- Required for Adjustment; approver permission checked by service
    adjustment_approved_at TIMESTAMP,   -- Required for Adjustment

    CONSTRAINT chk_course_ledger_qty_direction
        CHECK (
            (entry_type IN ('Purchase', 'TransferIn') AND qty_change > 0)
                OR (entry_type IN ('Treatment', 'TransferOut') AND qty_change < 0)
                OR (entry_type IN ('Adjustment', 'Reversal') AND qty_change <> 0)
        ),

    CONSTRAINT chk_course_ledger_balance_after
        CHECK (balance_after >= 0),

    CONSTRAINT fk_course_ledger_course
        FOREIGN KEY (course_id)
            REFERENCES course (course_id),

    -- Required for Reversal; unique when
    CONSTRAINT uq_course_ledger_reverses_ledger
        UNIQUE (reverses_ledger_id),

    CONSTRAINT chk_course_ledger_entry_source
        CHECK (
            -- transfer_id: Required for TransferOut/TransferIn
            (entry_type IN ('TransferOut', 'TransferIn')
                AND transfer_id IS NOT NULL
                AND visit_usage_id IS NULL
                AND sales_transaction_id IS NULL
                AND reverses_ledger_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL)
                OR
            -- visit_usage_id: Required for Treatment; one active consumption per usage
            (entry_type = 'Treatment'
                AND visit_usage_id IS NOT NULL
                AND transfer_id IS NULL
                AND sales_transaction_id IS NULL
                AND reverses_ledger_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL)
                OR
            -- sales_transaction_id: Required for Purchase; equals Course.sale_transaction_id
            (entry_type = 'Purchase'
                AND sales_transaction_id IS NOT NULL
                AND transfer_id IS NULL
                AND visit_usage_id IS NULL
                AND reverses_ledger_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL)
                OR
            -- adjustment_approved_by, adjustment_approved_at: Required for Adjustment; approver permission checked by service
            (entry_type = 'Adjustment'
                AND NULLIF(BTRIM(remark), '') IS NOT NULL
                AND adjustment_approved_by IS NOT NULL
                AND adjustment_approved_at IS NOT NULL
                AND transfer_id IS NULL
                AND visit_usage_id IS NULL
                AND sales_transaction_id IS NULL
                AND reverses_ledger_id IS NULL)
                OR
            (entry_type = 'Reversal'
                AND reverses_ledger_id IS NOT NULL
                AND transfer_id IS NULL
                AND visit_usage_id IS NULL
                AND sales_transaction_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL)
        ),

    CONSTRAINT fk_course_ledger_patient
        FOREIGN KEY (patient_id)
            REFERENCES patient (patient_id),

    CONSTRAINT fk_course_ledger_create_by
        FOREIGN KEY (ledger_create_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_course_ledger_transfer
        FOREIGN KEY (transfer_id)
            REFERENCES course_transfer (transfer_id),

    CONSTRAINT fk_course_ledger_visit_usage
        FOREIGN KEY (visit_usage_id)
            REFERENCES course_visit_usage (visit_usage_id),

    CONSTRAINT fk_course_ledger_reverses_ledger
        FOREIGN KEY (reverses_ledger_id)
            REFERENCES course_ledger (ledger_id),

    CONSTRAINT fk_course_ledger_sales_transaction
        FOREIGN KEY (sales_transaction_id)
            REFERENCES sales_transaction (transaction_id),

    CONSTRAINT fk_course_ledger_adjustment_approved_by
        FOREIGN KEY (adjustment_approved_by)
            REFERENCES app_user (user_id)
);

CREATE TABLE course_usage_print(
    print_id          BIGSERIAL PRIMARY KEY,
    visit_usage_id    BIGINT    NOT NULL,
    course_id         BIGINT    NOT NULL,

    issued_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    issued_by         BIGINT    NOT NULL,
    document_version  INT       NOT NULL,           -- >=1; UK(visit_usage_id, document_version)
    document_snapshot JSONB     NOT NULL,

    CONSTRAINT chk_course_usage_print_version
        CHECK (document_version >= 1),

    CONSTRAINT uq_course_usage_print_version
        UNIQUE (visit_usage_id, document_version),

    CONSTRAINT fk_course_usage_print_visit_usage
        FOREIGN KEY (visit_usage_id)
            REFERENCES course_visit_usage (visit_usage_id),

    CONSTRAINT fk_course_usage_print_course
        FOREIGN KEY (course_id)
            REFERENCES course (course_id),

    CONSTRAINT fk_course_usage_print_issued_by
        FOREIGN KEY (issued_by)
            REFERENCES app_user (user_id)
);

CREATE TABLE course_usage_print_event(
    print_event_id BIGSERIAL PRIMARY KEY,
    print_id       BIGINT                             NOT NULL,
    printed_at     TIMESTAMP                          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    printed_by     BIGINT                             NOT NULL,
    event_type     event_type_enum NOT NULL,

    CONSTRAINT fk_course_usage_print_event_print
        FOREIGN KEY (print_id)
            REFERENCES course_usage_print (print_id),

    CONSTRAINT fk_course_usage_print_event_printed_by
        FOREIGN KEY (printed_by)
            REFERENCES app_user (user_id)
);
-- Issued ได้หนึ่งครั้งต่อ print_id; Reprint ได้หลายครั้ง
CREATE UNIQUE INDEX uq_course_usage_print_event_issued
    ON course_usage_print_event (print_id)
    WHERE event_type = 'Issued';


-- Transaction Tables
CREATE TABLE payment_transaction(
    payment_id BIGSERIAL PRIMARY KEY,               -- auto-increment
    patient_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,

    amount NUMERIC(12, 2) NOT NULL,                 -- >=0
    payment_method_id BIGINT NOT NULL,
    payment_date TIMESTAMP NOT NULL,
    payment_status BOOLEAN NOT NULL DEFAULT TRUE,   -- 1 : Completed, 0 : Voided
    receipt_id BIGINT NOT NULL,

    payment_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_create_by BIGINT NOT NULL,
    payment_update_at TIMESTAMP,
    payment_update_by BIGINT,

    CONSTRAINT chk_payment_transaction_amount
        CHECK (amount >= 0),

    CONSTRAINT fk_payment_transaction_patient
        FOREIGN KEY (patient_id)
            REFERENCES patient (patient_id),

    CONSTRAINT fk_payment_transaction_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch (branch_id),

    CONSTRAINT fk_payment_transaction_payment_method
        FOREIGN KEY (payment_method_id)
            REFERENCES payment_method (payment_method_id),

    CONSTRAINT fk_payment_transaction_receipt
        FOREIGN KEY (receipt_id)
            REFERENCES receipt (receipt_id),

    CONSTRAINT fk_payment_transaction_create_by
        FOREIGN KEY (payment_create_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_payment_transaction_update_by
        FOREIGN KEY (payment_update_by)
            REFERENCES app_user (user_id)
);

CREATE TABLE transaction_cancellation(
    cancellation_id BIGSERIAL PRIMARY KEY,          -- auto-increment
    permission_id BIGINT NOT NULL,
    transaction_id BIGINT,
    payment_id BIGINT,
    transfer_id BIGINT,
    visit_usage_id BIGINT,

    reason VARCHAR(255) NOT NULL,
    cancelled_by BIGINT NOT NULL,
    cancelled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancellation_type cancellation_type_enum NOT NULL,

    -- Required void/refund/reversal reason; exactly one target ID per cancellation
    CONSTRAINT chk_transaction_cancellation_one_target
        CHECK (
            num_nonnulls(
                    transaction_id,
                    payment_id,
                    transfer_id,
                    visit_usage_id
            ) = 1
        ),
    -- ป้องกันใส่ reason ด้วยช่องว่าง
    CONSTRAINT chk_transaction_cancellation_reason
        CHECK (BTRIM(reason) <> ''),

    CONSTRAINT fk_transaction_cancellation_permission
        FOREIGN KEY (permission_id)
            REFERENCES permission(permission_id),

    CONSTRAINT fk_transaction_cancellation_transaction
        FOREIGN KEY (transaction_id)
            REFERENCES sales_transaction(transaction_id),

    CONSTRAINT fk_transaction_cancellation_payment
        FOREIGN KEY (payment_id)
            REFERENCES payment_transaction(payment_id),

    CONSTRAINT fk_transaction_cancellation_transfer
        FOREIGN KEY (transfer_id)
            REFERENCES course_transfer(transfer_id),

    CONSTRAINT fk_transaction_cancellation_visit_usage
        FOREIGN KEY (visit_usage_id)
            REFERENCES course_visit_usage(visit_usage_id),

    CONSTRAINT fk_transaction_cancellation_cancelled_by
        FOREIGN KEY (cancelled_by)
            REFERENCES app_user(user_id)
);

CREATE TABLE refund_transaction(
    refund_id           BIGSERIAL PRIMARY KEY,
    cancellation_id     BIGINT             NOT NULL,
    original_payment_id BIGINT             NOT NULL,
    refund_amount       NUMERIC(12, 2)     NOT NULL,    -- >0
    payment_method_id   BIGINT             NOT NULL,

    refund_status       refund_status_enum NOT NULL,
    refunded_at         TIMESTAMP,
    refunded_by         BIGINT,
    requested_at        TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    requested_by        BIGINT             NOT NULL,

    CONSTRAINT chk_refund_transaction_amount
        CHECK (refund_amount > 0),

    CONSTRAINT chk_refund_transaction_completion
        CHECK (
            (refund_status = 'Completed'
                AND refunded_at IS NOT NULL
                AND refunded_by IS NOT NULL)
                OR
            (refund_status <> 'Completed'
                AND refunded_at IS NULL
                AND refunded_by IS NULL)
            ),

    CONSTRAINT fk_refund_transaction_cancellation
        FOREIGN KEY (cancellation_id)
            REFERENCES transaction_cancellation (cancellation_id),

    CONSTRAINT fk_refund_transaction_original_payment
        FOREIGN KEY (original_payment_id)
            REFERENCES payment_transaction (payment_id),

    CONSTRAINT fk_refund_transaction_payment_method
        FOREIGN KEY (payment_method_id)
            REFERENCES payment_method (payment_method_id),

    CONSTRAINT fk_refund_transaction_refunded_by
        FOREIGN KEY (refunded_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_refund_transaction_requested_by
        FOREIGN KEY (requested_by)
            REFERENCES app_user (user_id)
);

-- Commission after course Tables
CREATE TABLE monthly_commission_closing(
    closing_id BIGSERIAL PRIMARY KEY,                       -- auto-increment
    closing_month CHAR(7) NOT NULL,
    permission_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,

    monthly_course_sales NUMERIC(12, 2) NOT NULL,           -- >=0
    commission_scheme_id BIGINT NOT NULL,
    commission_scheme_version INT NOT NULL,                 -- >=1

    calculated_commission_rate NUMERIC(5, 4) NOT NULL,      -- 0 to 1
    locked_commission_rate NUMERIC(5, 4),                   -- 0 to 1
    closing_status BOOLEAN NOT NULL DEFAULT FALSE,          -- 1 : Closed, 0 : Open

    closed_at TIMESTAMP,
    closed_by BIGINT,

    CONSTRAINT uq_monthly_closing_month_staff
        UNIQUE (closing_month, staff_id),

    -- yyyy-mm
    CONSTRAINT chk_monthly_closing_month
        CHECK (closing_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),

    CONSTRAINT chk_monthly_closing_monthly_sales
        CHECK (monthly_course_sales >= 0),

    CONSTRAINT chk_monthly_closing_scheme_version
        CHECK (commission_scheme_version >= 1),

    CONSTRAINT chk_monthly_closing_calculated_rate
        CHECK (calculated_commission_rate BETWEEN 0 AND 1),

    CONSTRAINT chk_monthly_closing_locked_rate
        CHECK (locked_commission_rate BETWEEN 0 AND 1),

    CONSTRAINT fk_monthly_closing_permission
        FOREIGN KEY (permission_id)
            REFERENCES permission(permission_id),

    CONSTRAINT fk_monthly_closing_staff
        FOREIGN KEY (staff_id)
            REFERENCES staff(staff_id),

    CONSTRAINT fk_monthly_closing_commission_scheme
        FOREIGN KEY (commission_scheme_id)
            REFERENCES commission_scheme(commission_scheme_id),

    CONSTRAINT fk_monthly_closing_closed_by
        FOREIGN KEY (closed_by)
            REFERENCES app_user(user_id)
);

CREATE TABLE commission_allocation_ledger(
    allocation_id BIGSERIAL PRIMARY KEY,                        -- auto-increment
    visit_usage_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    case_owner_staff_id BIGINT NOT NULL,
    treating_staff_id BIGINT NOT NULL,

    visit_date DATE NOT NULL,

    gross_commission_allocation NUMERIC(12, 2) NOT NULL,
    treatment_fee_rule_id BIGINT,
    treatment_fee_type fee_type_enum,
    treatment_fee_rate_or_amount NUMERIC(12, 4),                -- >=0
    treatment_fee_calculation_base VARCHAR(50),
    treatment_fee_amount NUMERIC(12, 2) NOT NULL,

    owner_net_commission NUMERIC(12, 2) NOT NULL,
    company_top_up_amount NUMERIC(12, 2),

    overflow_policy_used overflow_policy_enum,
    adjustment_reference_id BIGINT,

    allocation_create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    allocation_create_by BIGINT NOT NULL,
    allocation_update_at TIMESTAMP,
    allocation_update_by BIGINT,

    entry_type commission_allocation_entry_type_enum NOT NULL,
    adjustment_reason VARCHAR(255),
    adjustment_approved_by BIGINT,
    adjustment_approved_at TIMESTAMP,
    reversal_reason VARCHAR(255),

    CONSTRAINT chk_commission_allocation_rate_or_amount
        CHECK (treatment_fee_rate_or_amount >= 0),

    CONSTRAINT chk_commission_allocation_amount_direction
        CHECK (
            (entry_type = 'Allocation'
                AND gross_commission_allocation >= 0
                AND treatment_fee_amount >= 0
                AND owner_net_commission >= 0
                AND (company_top_up_amount IS NULL OR company_top_up_amount >= 0))
                OR
            (entry_type = 'Reversal'
                AND gross_commission_allocation <= 0
                AND treatment_fee_amount <= 0
                AND owner_net_commission <= 0
                AND (company_top_up_amount IS NULL OR company_top_up_amount <= 0))
                OR entry_type = 'Adjustment'
        ),

    CONSTRAINT chk_commission_allocation_entry_details
        CHECK (
            (entry_type = 'Allocation'
                AND adjustment_reason IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL
                AND reversal_reason IS NULL)
                OR
            (entry_type = 'Adjustment'
                AND NULLIF(BTRIM(adjustment_reason), '') IS NOT NULL
                AND adjustment_approved_by IS NOT NULL
                AND adjustment_approved_at IS NOT NULL
                AND reversal_reason IS NULL)
                OR
            (entry_type = 'Reversal'
                AND adjustment_reference_id IS NOT NULL
                AND NULLIF(BTRIM(reversal_reason), '') IS NOT NULL
                AND adjustment_reason IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL)
            ),

    CONSTRAINT fk_commission_allocation_visit_usage
        FOREIGN KEY (visit_usage_id)
            REFERENCES course_visit_usage (visit_usage_id),

    CONSTRAINT fk_commission_allocation_course
        FOREIGN KEY (course_id)
            REFERENCES course (course_id),

    CONSTRAINT fk_commission_allocation_patient
        FOREIGN KEY (patient_id)
            REFERENCES patient (patient_id),

    CONSTRAINT fk_commission_allocation_case_owner_staff
        FOREIGN KEY (case_owner_staff_id)
            REFERENCES staff (staff_id),

    CONSTRAINT fk_commission_allocation_treating_staff
        FOREIGN KEY (treating_staff_id)
            REFERENCES staff (staff_id),

    CONSTRAINT fk_commission_allocation_treatment_fee_rule
        FOREIGN KEY (treatment_fee_rule_id)
            REFERENCES treatment_fee_rule (treatment_fee_rule_id),

    CONSTRAINT fk_commission_allocation_adjustment_reference
        FOREIGN KEY (adjustment_reference_id)
            REFERENCES commission_allocation_ledger (allocation_id),

    CONSTRAINT fk_commission_allocation_create_by
        FOREIGN KEY (allocation_create_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_commission_allocation_update_by
        FOREIGN KEY (allocation_update_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_commission_allocation_adjustment_approved_by
        FOREIGN KEY (adjustment_approved_by)
            REFERENCES app_user (user_id)
);
-- one Reversal per original allocation entry
CREATE UNIQUE INDEX uq_commission_allocation_reversal_reference
    ON commission_allocation_ledger (adjustment_reference_id)
    WHERE entry_type = 'Reversal';
-- One initial Allocation per course usage
CREATE UNIQUE INDEX uq_commission_allocation_initial_usage
    ON commission_allocation_ledger (visit_usage_id)
    WHERE entry_type = 'Allocation';

CREATE TABLE commission_rate_override_audit(
    override_id BIGSERIAL PRIMARY KEY,          -- auto-increment
    permission_id BIGINT NOT NULL,
    closing_id BIGINT,
    course_id BIGINT,

    old_rate NUMERIC(5, 4) NOT NULL,            -- 0 to 1
    new_rate NUMERIC(5, 4) NOT NULL,            -- 0 to 1

    changed_by BIGINT NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(255) NOT NULL,

    CONSTRAINT chk_commission_override_target
        CHECK (closing_id IS NOT NULL OR course_id IS NOT NULL),

    CONSTRAINT chk_commission_override_old_rate
        CHECK (old_rate BETWEEN 0 AND 1),

    CONSTRAINT chk_commission_override_new_rate
        CHECK (new_rate BETWEEN 0 AND 1),

    CONSTRAINT fk_commission_override_permission
        FOREIGN KEY (permission_id)
            REFERENCES permission (permission_id),

    CONSTRAINT fk_commission_override_closing
        FOREIGN KEY (closing_id)
            REFERENCES monthly_commission_closing (closing_id),

    CONSTRAINT fk_commission_override_course
        FOREIGN KEY (course_id)
            REFERENCES course (course_id),

    CONSTRAINT fk_commission_override_changed_by
        FOREIGN KEY (changed_by)
            REFERENCES app_user (user_id)
);


-- Audit Tables
CREATE TABLE audit_log(
    audit_id BIGSERIAL PRIMARY KEY,             -- auto-increment
    table_name VARCHAR(50) NOT NULL,
    record_id VARCHAR(50) NOT NULL,
    action audit_action_enum NOT NULL,

    old_value TEXT,
    new_value TEXT,

    changed_by BIGINT NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_log_changed_by
        FOREIGN KEY (changed_by)
            REFERENCES app_user (user_id)
);


-- Circular Foreign Key
ALTER TABLE branch
    ADD CONSTRAINT fk_branch_create_by
        FOREIGN KEY (branch_create_by)
            REFERENCES app_user(user_id);

ALTER TABLE branch
    ADD CONSTRAINT fk_branch_update_by
        FOREIGN KEY (branch_update_by)
            REFERENCES app_user(user_id);


ALTER TABLE role
    ADD CONSTRAINT fk_role_create_by
        FOREIGN KEY (role_create_by)
            REFERENCES app_user(user_id);

ALTER TABLE role
    ADD CONSTRAINT fk_role_update_by
        FOREIGN KEY (role_update_by)
            REFERENCES app_user(user_id);


ALTER TABLE service
    ADD CONSTRAINT fk_service_create_by
        FOREIGN KEY (service_create_by)
            REFERENCES app_user(user_id);

ALTER TABLE service
    ADD CONSTRAINT fk_service_update_by
        FOREIGN KEY (service_update_by)
            REFERENCES app_user(user_id);


ALTER TABLE permission
    ADD CONSTRAINT fk_permission_create_by
        FOREIGN KEY (permission_create_by)
            REFERENCES app_user(user_id);

ALTER TABLE permission
    ADD CONSTRAINT fk_permission_update_by
        FOREIGN KEY (permission_update_by)
            REFERENCES app_user(user_id);


ALTER TABLE payment_method
    ADD CONSTRAINT fk_payment_method_create_by
        FOREIGN KEY (payment_method_create_by)
            REFERENCES app_user(user_id);

ALTER TABLE payment_method
    ADD CONSTRAINT fk_payment_method_update_by
        FOREIGN KEY (payment_method_update_by)
            REFERENCES app_user(user_id);


ALTER TABLE room
    ADD CONSTRAINT fk_room_create_by
        FOREIGN KEY (room_create_by)
            REFERENCES app_user(user_id);

ALTER TABLE room
    ADD CONSTRAINT fk_room_update_by
        FOREIGN KEY (room_update_by)
            REFERENCES app_user(user_id);


ALTER TABLE master_data_type
    ADD CONSTRAINT fk_master_data_type_create_by
        FOREIGN KEY (type_create_by)
            REFERENCES app_user(user_id);

ALTER TABLE master_data_type
    ADD CONSTRAINT fk_master_data_type_update_by
        FOREIGN KEY (type_update_by)
            REFERENCES app_user(user_id);


ALTER TABLE staff
    ADD CONSTRAINT fk_staff_create_by
        FOREIGN KEY (staff_create_by)
            REFERENCES app_user(user_id);

ALTER TABLE staff
    ADD CONSTRAINT fk_staff_update_by
        FOREIGN KEY (staff_update_by)
            REFERENCES app_user(user_id);


-- ALTER TABLE app_user
--     ADD CONSTRAINT fk_app_user_create_by
--         FOREIGN KEY (user_create_by)
--             REFERENCES app_user(user_id);
--
-- ALTER TABLE app_user
--     ADD CONSTRAINT fk_app_user_update_by
--         FOREIGN KEY (user_update_by)
--             REFERENCES app_user(user_id);


ALTER TABLE course
    ADD CONSTRAINT fk_course_sale_transaction
        FOREIGN KEY (sale_transaction_id)
            REFERENCES sales_transaction(transaction_id);
