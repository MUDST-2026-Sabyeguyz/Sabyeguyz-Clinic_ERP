-- V1__initial_schema.sql

CREATE TABLE branch (
    branch_id BIGSERIAL PRIMARY KEY,        -- auto-increment
    branch_code CHAR(2) NOT NULL UNIQUE,
    branch_name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    branch_status BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE role (
    role_id BIGSERIAL PRIMARY KEY,          -- auto-increment
    role_code CHAR(2) NOT NULL UNIQUE,
    role_name VARCHAR(50) NOT NULL
);

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
    user_id BIGSERIAL PRIMARY KEY,            -- auto-increment
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

    PRIMARY KEY (user_id, role_id),

    FOREIGN KEY (user_id) REFERENCES app_user(user_id),
    FOREIGN KEY (role_id) REFERENCES role(role_id)
);

CREATE TYPE customer_type_enum AS ENUM ('Thai', 'Foreigner');
CREATE TYPE gender_enum AS ENUM ('Male', 'Female', 'Other');

CREATE TABLE patient (
    patient_id BIGSERIAL PRIMARY KEY,       -- auto-increment
    hn CHAR(10) NOT NULL UNIQUE,
    customer_type customer_type_enum NOT NULL,
    title VARCHAR(20) NOT NULL,             -- change to ENUM
    patient_fname VARCHAR(100) NOT NULL,
    patient_lname VARCHAR(100) NOT NULL,
    gender gender_enum NOT NULL,
    phone VARCHAR(20) NOT NULL,
    branch_id BIGINT NOT NULL,
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (branch_id) REFERENCES branch(branch_id)
);
