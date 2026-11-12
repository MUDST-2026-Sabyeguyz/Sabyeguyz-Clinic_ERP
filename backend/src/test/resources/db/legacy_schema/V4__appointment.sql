CREATE SEQUENCE therapist_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE therapists (
                            id BIGINT DEFAULT nextval('therapist_id_seq') PRIMARY KEY,
                            branch_id BIGINT NOT NULL REFERENCES branches(id),
                            code VARCHAR(20) NOT NULL UNIQUE,
                            full_name VARCHAR(160) NOT NULL,
                            active BOOLEAN NOT NULL DEFAULT TRUE,
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE service_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE services (
                          id BIGINT DEFAULT nextval('service_id_seq') PRIMARY KEY,
                          code VARCHAR(20) NOT NULL UNIQUE,
                          name VARCHAR(120) NOT NULL,
                          default_duration_minutes INTEGER NOT NULL,
                          active BOOLEAN NOT NULL DEFAULT TRUE,
                          created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE appointment_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE appointments (
                              id BIGINT DEFAULT nextval('appointment_id_seq') PRIMARY KEY,
                              branch_id BIGINT NOT NULL REFERENCES branches(id),
                              patient_id BIGINT NOT NULL REFERENCES patients(id),
                              therapist_id BIGINT NOT NULL REFERENCES therapists(id),
                              service_id BIGINT NOT NULL REFERENCES services(id),
                              start_at TIMESTAMP WITH TIME ZONE NOT NULL,
                              end_at TIMESTAMP WITH TIME ZONE NOT NULL,
                              status VARCHAR(20) NOT NULL,
                              note TEXT,
                              cancel_reason TEXT,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT chk_appointments_period CHECK (end_at > start_at)
);

CREATE INDEX idx_appointments_therapist_start ON appointments(therapist_id, start_at);
CREATE INDEX idx_appointments_patient_start ON appointments(patient_id, start_at);
CREATE INDEX idx_appointments_branch_start ON appointments(branch_id, start_at);

INSERT INTO therapists (branch_id, code, full_name)
VALUES
    ((SELECT id FROM branches WHERE code = 'RP'), 'RP-T01', 'นักกายภาพบำบัด คนที่หนึ่ง'),
    ((SELECT id FROM branches WHERE code = 'RP'), 'RP-T02', 'นักกายภาพบำบัด คนที่สอง');

INSERT INTO services (code, name, default_duration_minutes)
VALUES
    ('PHYSIO_30', 'กายภาพบำบัดทั่วไป 30 นาที', 30),
    ('PHYSIO_60', 'กายภาพบำบัดทั่วไป 60 นาที', 60),
    ('ASSESSMENT', 'ประเมินอาการครั้งแรก', 45);