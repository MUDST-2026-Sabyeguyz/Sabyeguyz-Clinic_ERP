CREATE TABLE hn_counters (
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    registration_year INTEGER NOT NULL,
    registration_month INTEGER NOT NULL CHECK (registration_month BETWEEN 1 AND 12),
    last_number INTEGER NOT NULL CHECK (last_number BETWEEN 0 AND 9999),
    PRIMARY KEY (branch_id, registration_year, registration_month)
);
