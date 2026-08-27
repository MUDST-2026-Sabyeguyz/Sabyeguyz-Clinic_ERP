CREATE SEQUENCE branch_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE branches (
    id BIGINT DEFAULT nextval('branch_id_seq') PRIMARY KEY,
    code VARCHAR(2) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE patient_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE patients (
    id BIGINT DEFAULT nextval('patient_id_seq') PRIMARY KEY,
    hn VARCHAR(24) UNIQUE,
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    username VARCHAR(80) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    title VARCHAR(30),
    full_name VARCHAR(160) NOT NULL,
    birth_date DATE NOT NULL,
    gender VARCHAR(30),
    phone VARCHAR(20) NOT NULL,
    identity_document_type VARCHAR(40) NOT NULL,
    identity_number VARCHAR(40) NOT NULL UNIQUE,
    address TEXT NOT NULL,
    drug_allergy BOOLEAN NOT NULL DEFAULT FALSE,
    food_allergy BOOLEAN NOT NULL DEFAULT FALSE,
    chronic_disease TEXT,
    emergency_contact_name VARCHAR(160) NOT NULL,
    emergency_contact_relationship VARCHAR(80) NOT NULL,
    emergency_contact_phone VARCHAR(20) NOT NULL,
    privacy_consent BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_patients_branch_id ON patients(branch_id);
CREATE INDEX idx_patients_full_name ON patients(full_name);

INSERT INTO branches (code, name) VALUES ('RP', 'สาขาราชพฤกษ์');
