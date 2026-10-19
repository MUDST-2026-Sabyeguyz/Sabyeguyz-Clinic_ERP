-- A course is extended in two steps: the original owner asks, a Manager approves. Approving writes the existing
-- course_extension row (the audit of what changed) and moves course.expiry_date, which every holder of the course shares.
CREATE TABLE course_extension_request (
    request_id           BIGSERIAL PRIMARY KEY,
    course_id            BIGINT             NOT NULL,
    requested_patient_id BIGINT             NOT NULL,        -- the owner on whose behalf the request is made
    extension_value      INT                NOT NULL,        -- >= 1
    extension_unit       validity_unit_enum NOT NULL,
    reason               VARCHAR(255),
    request_status       VARCHAR(20)        NOT NULL DEFAULT 'Pending',
    requested_by         BIGINT             NOT NULL,
    requested_at         TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_by           BIGINT,
    decided_at           TIMESTAMP,
    decision_note        VARCHAR(255),
    extension_id         BIGINT,                              -- set when approved

    CONSTRAINT chk_course_extension_request_value
        CHECK (extension_value >= 1),
    CONSTRAINT chk_course_extension_request_status
        CHECK (request_status IN ('Pending', 'Approved', 'Rejected')),
    CONSTRAINT fk_course_extension_request_course
        FOREIGN KEY (course_id) REFERENCES course (course_id),
    CONSTRAINT fk_course_extension_request_patient
        FOREIGN KEY (requested_patient_id) REFERENCES patient (patient_id),
    CONSTRAINT fk_course_extension_request_requested_by
        FOREIGN KEY (requested_by) REFERENCES app_user (user_id),
    CONSTRAINT fk_course_extension_request_decided_by
        FOREIGN KEY (decided_by) REFERENCES app_user (user_id),
    CONSTRAINT fk_course_extension_request_extension
        FOREIGN KEY (extension_id) REFERENCES course_extension (extension_id)
);

-- one open request per course at a time
CREATE UNIQUE INDEX uq_course_extension_request_pending
    ON course_extension_request (course_id)
    WHERE request_status = 'Pending';
