-- V1__initial_schema.sql
    -- CONSTRAINT format :
    --     pk_<table_name>
    --     fk_<table_name>_<column_name>
    --     chk_<table_name>_<column_name>

-- ENUM types
CREATE TYPE customer_type_enum AS ENUM ('Thai', 'Foreigner');
-- CREATE TYPE title_enum AS ENUM ('Mr.', 'Ms.', 'Mrs.');          -- waiting for approve
CREATE TYPE gender_enum AS ENUM ('Male', 'Female', 'Other');
CREATE TYPE service_type_enum AS ENUM ('Assessment', 'SingleVisit', 'Course');
CREATE TYPE appointment_status_enum AS ENUM (
    'Confirmed', 'Arrived', 'InService', 'Completed', 'Cancelled', 'Rescheduled', 'NoShow'
);
CREATE TYPE visit_status_enum AS ENUM ('Arrived', 'InService', 'Completed', 'Cancelled');


-- Master tables
CREATE TABLE branch (
    branch_id BIGSERIAL PRIMARY KEY,        -- auto-increment
    branch_code CHAR(2) NOT NULL UNIQUE,    -- NN ; 0-99
    branch_name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    branch_status BOOLEAN NOT NULL DEFAULT TRUE,

    -- NN ; Two-digit branch code: 00-99
    CONSTRAINT chk_branch_code
        CHECK (branch_code ~ '^[0-9]{2}$')
);

CREATE TABLE role (
    role_id BIGSERIAL PRIMARY KEY,          -- auto-increment
    role_code CHAR(2) NOT NULL UNIQUE,      -- NN ; 0-99
    role_name VARCHAR(50) NOT NULL,

    -- NN ; Two-digit branch code: 00-99
    CONSTRAINT chk_role_code
        CHECK (role_code ~ '^[0-9]{2}$')
);

CREATE TABLE service (
    service_id BIGSERIAL PRIMARY KEY,                   -- auto-increment
    service_type service_type_enum NOT NULL,
    service_name VARCHAR(150) NOT NULL,
    service_price NUMERIC(12, 2) NOT NULL DEFAULT 0,    -- >=0
    service_status BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_service_price
        CHECK (service_price >= 0)
);

CREATE TABLE course_package (
    package_id BIGSERIAL PRIMARY KEY,                   -- auto-increment
    package_name VARCHAR(100) NOT NULL,
    package_price NUMERIC(12, 2) NOT NULL DEFAULT 0,    -- >=0
    sessions INT NOT NULL,                              -- >=1 ; each package is different
    bonus_sessions INT NOT NULL DEFAULT 0,              -- >=0
    expiry_days INT NOT NULL,                           -- >0  ; each package is different
    package_status BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_package_price
        CHECK (package_price >= 0),

    CONSTRAINT chk_package_sessions
        CHECK (sessions >= 1),

    CONSTRAINT chk_package_bonus
        CHECK (bonus_sessions >= 0),

    CONSTRAINT chk_package_expiry_days
        CHECK (expiry_days > 0)
);


-- User tables
CREATE TABLE staff (
    staff_id BIGSERIAL PRIMARY KEY,          -- auto-increment
    staff_code VARCHAR(20) NOT NULL UNIQUE,
    staff_fname VARCHAR(100) NOT NULL,
    staff_lname VARCHAR(100) NOT NULL,
    position VARCHAR(50) NOT NULL,
    branch_id BIGINT NOT NULL,
    staff_status BOOLEAN NOT NULL DEFAULT TRUE,
    termination_date DATE,

    CONSTRAINT fk_staff_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id)
);

CREATE TABLE app_user (
    user_id BIGSERIAL PRIMARY KEY,                -- auto-increment
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    staff_id BIGINT NOT NULL UNIQUE,
    user_status BOOLEAN NOT NULL DEFAULT TRUE,    -- active = Account can be use

    CONSTRAINT fk_user_staff
        FOREIGN KEY (staff_id)
            REFERENCES staff(staff_id)
);

CREATE TABLE user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    CONSTRAINT pk_user_role
        PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_role_user
        FOREIGN KEY (user_id)
            REFERENCES app_user(user_id),

    CONSTRAINT fk_user_role_role
        FOREIGN KEY (role_id)
            REFERENCES role(role_id)
);


-- Patient tables
CREATE TABLE patient (
    patient_id BIGSERIAL PRIMARY KEY,       -- auto-increment
    hn CHAR(10) NOT NULL UNIQUE,            -- YYBBMMNNNN
    customer_type customer_type_enum NOT NULL,
    -- title title_enum NOT NULL,
    title VARCHAR(20) NOT NULL,
    patient_fname VARCHAR(100) NOT NULL,
    patient_lname VARCHAR(100) NOT NULL,
    gender gender_enum NOT NULL,
    phone VARCHAR(20) NOT NULL,
    branch_id BIGINT NOT NULL,
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- YYBBMMNNNN
    CONSTRAINT chk_patient_hn
        CHECK (hn ~ '^[0-9]{10}$'),

    CONSTRAINT fk_patient_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id)
);

CREATE TABLE hn_sequence (
    branch_id BIGINT NOT NULL,
    year_month CHAR(4) NOT NULL,            -- YYMM
    last_running_no INT NOT NULL DEFAULT 0, -- >=0

    CONSTRAINT pk_hn_sequence
     PRIMARY KEY (branch_id, year_month),

    CONSTRAINT fk_hn_sequence_branch
        FOREIGN KEY (branch_id)
         REFERENCES branch(branch_id),

    -- YYMM
    CONSTRAINT chk_hn_sequence_year_month
        CHECK (year_month ~ '^[0-9]{4}$'),

    CONSTRAINT chk_hn_sequence_running_no
     CHECK (last_running_no >= 0)
);


-- Appointment tables
CREATE TABLE appointment (
    appointment_id BIGSERIAL PRIMARY KEY,   -- auto-increment
    patient_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    therapist_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    starts_at TIMESTAMP NOT NULL,
    appointment_status appointment_status_enum NOT NULL,
    created_by BIGINT NOT NULL,

    CONSTRAINT fk_appointment_patient
        FOREIGN KEY (patient_id)
            REFERENCES patient(patient_id),

    CONSTRAINT fk_appointment_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id),

    CONSTRAINT fk_appointment_therapist
        FOREIGN KEY (therapist_id)
            REFERENCES staff(staff_id),

    CONSTRAINT fk_appointment_service
        FOREIGN KEY (service_id)
            REFERENCES service(service_id),

    CONSTRAINT fk_appointment_created_by
        FOREIGN KEY (created_by)
            REFERENCES app_user(user_id)
);

CREATE TABLE appointment_status_history (
    history_id BIGSERIAL PRIMARY KEY,           -- auto-increment
    appointment_id BIGINT NOT NULL,
    from_status appointment_status_enum,
    to_status appointment_status_enum NOT NULL,
    changed_by BIGINT NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(255),

    CONSTRAINT fk_appointment_status_history_appointment
        FOREIGN KEY (appointment_id)
            REFERENCES appointment(appointment_id),

    CONSTRAINT fk_fk_appointment_status_history_changed_by
        FOREIGN KEY (changed_by)
            REFERENCES app_user(user_id)
);


-- Visit and treatment tables
CREATE TABLE patient_visit (
    visit_id BIGSERIAL PRIMARY KEY,             -- auto-increment
    appointment_id BIGINT,
    patient_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    therapist_id BIGINT NOT NULL,
    visit_date TIMESTAMP NOT NULL,
    visit_status visit_status_enum NOT NULL,
    completed_at TIMESTAMP,

    CONSTRAINT fk_patient_visit_appointment
        FOREIGN KEY (appointment_id)
            REFERENCES appointment(appointment_id),

    CONSTRAINT fk_patient_visit_patient
        FOREIGN KEY (patient_id)
            REFERENCES patient(patient_id),

    CONSTRAINT fk_patient_visit_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch(branch_id),

    CONSTRAINT fk_patient_visit_therapist
        FOREIGN KEY (therapist_id)
            REFERENCES staff(staff_id)
);

CREATE TABLE treatment (
    treatment_id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    notes TEXT,
    created_by BIGINT NOT NULL,
--     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_treatment_visit
        FOREIGN KEY (visit_id)
            REFERENCES patient_visit(visit_id),

    CONSTRAINT fk_treatment_service
        FOREIGN KEY (service_id)
            REFERENCES service(service_id),

    CONSTRAINT fk_treatment_created_by
        FOREIGN KEY (created_by)
            REFERENCES app_user(user_id)
);