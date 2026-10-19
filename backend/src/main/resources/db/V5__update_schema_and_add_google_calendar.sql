-- Update existing tables to match the latest Data Dictionary
-- and add Google Calendar connection and busy block tables.

-- Branch
    -- Separate from appointment sync status, which includes Deleted.
CREATE TYPE branch_calendar_sync_status_enum AS ENUM (
    'Pending', 'Synced', 'Failed', 'Disabled'
);

ALTER TABLE branch
    ADD COLUMN email VARCHAR(254),
    ADD COLUMN license_no VARCHAR(50),
    ADD COLUMN opening_time TIME,
    ADD COLUMN closing_time TIME,
    ADD COLUMN default_appointment_minutes INT,
    ADD COLUMN google_connection_id BIGINT,
    ADD COLUMN calendar_last_synced_at TIMESTAMP,
    ADD COLUMN calendar_sync_status branch_calendar_sync_status_enum,
    ADD COLUMN calendar_last_error TEXT;

ALTER TABLE branch
    -- Branch contact emails may be shared by multiple branches.
    ADD CONSTRAINT chk_branch_email_format
        CHECK (
            email IS NULL
                OR (
                email = BTRIM(email)
                    AND email ~
                    '^[^@.[:space:]]+([.][^@.[:space:]]+)*@[^@.[:space:]]+([.][^@.[:space:]]+)+$'
                )
            ),

    -- Opening and closing times must be configured together.
    ADD CONSTRAINT chk_branch_opening_hours
        CHECK (
            (opening_time IS NULL AND closing_time IS NULL)
            OR (
                opening_time IS NOT NULL
                AND closing_time IS NOT NULL
                AND closing_time > opening_time
            )
        ),

    ADD CONSTRAINT chk_branch_default_appointment_minutes
        CHECK (default_appointment_minutes >= 1),

    -- Calendar and account connection are required when sync is enabled.
    ADD CONSTRAINT chk_branch_calendar_sync_config
        CHECK (
            calendar_sync_status IS NULL
            OR calendar_sync_status = 'Disabled'
            OR (
                NULLIF(BTRIM(google_calendar_id), '') IS NOT NULL
                AND google_connection_id IS NOT NULL
            )
        );

-- Service
ALTER TABLE service
    ADD COLUMN default_duration_minutes INT;

ALTER TABLE service
    ADD CONSTRAINT chk_service_default_duration_minutes
        CHECK (default_duration_minutes >= 1);

-- Room
ALTER TABLE room
    ADD COLUMN capacity INT,
    ADD CONSTRAINT chk_room_capacity
        CHECK (capacity >= 1);

-- Patient
    -- email
ALTER TABLE patient
    ADD COLUMN email VARCHAR(254);

ALTER TABLE patient
    ADD CONSTRAINT chk_patient_email_format
        CHECK (
            email IS NULL
                OR (
                email = BTRIM(email)
                    AND email ~
                    '^[^@.[:space:]]+([.][^@.[:space:]]+)*@[^@.[:space:]]+([.][^@.[:space:]]+)+$'
                )
            ),

    -- check title
    ADD CONSTRAINT chk_patient_title_customer_type
        CHECK (
            (
                customer_type = 'Thai'
                AND title IN (
                    'นาย', 'นางสาว', 'นาง', 'เด็กชาย', 'เด็กหญิง'
                )
            )
            OR (
                customer_type = 'Foreigner'
                AND title IN (
                    'Mr.', 'Miss', 'Mrs.', 'Boy', 'Girl'
                )
            )
        );

-- Appointment
ALTER TABLE appointment
    ADD COLUMN course_id BIGINT,
    ADD COLUMN appointment_end_at TIMESTAMP,    -- NOT NULL
    ADD COLUMN contact_first_name VARCHAR(100),
    ADD COLUMN contact_last_name VARCHAR(100);

ALTER TABLE appointment
    ADD CONSTRAINT chk_appointment_course_patient_type
        CHECK (
            course_id IS NULL
            OR appointment_patient_type = 'CURRENT'
        ),

    ADD CONSTRAINT chk_appointment_end_at
        CHECK (
            appointment_end_at >
                (appointment_date + appointment_time)   --> appointment start datetime
        ),

    ADD CONSTRAINT fk_appointment_course
        FOREIGN KEY (course_id)
            REFERENCES course (course_id);

-- Existing appointments must have their actual end times populated
-- before enforcing NOT NULL.

-- ALTER TABLE appointment
--     ALTER COLUMN appointment_end_at SET NOT NULL;

-- Treatment Fee Rule
    -- Display name only; not used to select rules by staff or service.
ALTER TABLE treatment_fee_rule
    ADD COLUMN rule_name VARCHAR(100);

-- Receipt
ALTER TABLE receipt
    ADD COLUMN note TEXT;

-- Course
ALTER TABLE course
    ALTER COLUMN substitute_treatment_fee_total SET DEFAULT 0;

-- Sales Transaction
ALTER TABLE sales_transaction
    DROP CONSTRAINT chk_sales_transaction_line_discount;

ALTER TABLE sales_transaction
    ADD CONSTRAINT chk_sales_transaction_line_discount
        CHECK (
            CASE
                WHEN line_discount_type IS NULL THEN
                    line_discount_value IS NULL
                        AND line_discount_amount = 0

                WHEN line_discount_type = 'Fixed' THEN
                    line_discount_value IS NOT NULL
                        AND line_discount_value >= 0
                        AND line_discount_amount = line_discount_value

                WHEN line_discount_type = 'Percentage' THEN
                    line_discount_value IS NOT NULL
                        AND line_discount_value BETWEEN 0 AND 100
                        AND line_discount_amount = ROUND(
                            quantity::NUMERIC * unit_price * line_discount_value / 100,
                            2
                        )

                ELSE FALSE
            END
        ),

    -- A line discount cannot coexist with an allocated bill discount.
    ADD CONSTRAINT chk_sales_transaction_discount_exclusivity
        CHECK (
            line_discount_amount = 0
            OR allocated_bill_discount_amount = 0
        );

-- Google Calendar Connection
CREATE TYPE google_connection_status_enum AS ENUM (
    'Connected', 'ReauthRequired', 'Disconnected'
);

CREATE TABLE google_calendar_connection (
    connection_id        BIGSERIAL PRIMARY KEY,
    google_account_email VARCHAR(254)                  NOT NULL,
    credential_ref       VARCHAR(255),
    connection_status    google_connection_status_enum NOT NULL,
    authorized_by        BIGINT,
    authorized_at        TIMESTAMP,

    created_at           TIMESTAMP                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           BIGINT                        NOT NULL,
    updated_at           TIMESTAMP,
    updated_by           BIGINT,

    CONSTRAINT chk_google_connection_email_format
        CHECK (
            google_account_email = BTRIM(google_account_email)
                AND google_account_email ~
            '^[^@.[:space:]]+([.][^@.[:space:]]+)*@[^@.[:space:]]+([.][^@.[:space:]]+)+$'
        ) ,

    -- Connected requires credentials and authorization details.
    CONSTRAINT chk_google_connection_connected_details
        CHECK (
            connection_status <> 'Connected'
            OR (
                NULLIF(BTRIM(credential_ref), '') IS NOT NULL
                AND authorized_by IS NOT NULL
                AND authorized_at IS NOT NULL
            )
        ),

    CONSTRAINT fk_google_connection_authorized_by
        FOREIGN KEY (authorized_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_google_connection_created_by
        FOREIGN KEY (created_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_google_connection_updated_by
        FOREIGN KEY (updated_by)
            REFERENCES app_user (user_id)
);

    -- Google account emails must be unique regardless of letter case.
CREATE UNIQUE INDEX uq_google_connection_email_ci
    ON google_calendar_connection (LOWER(google_account_email));

-- Calendar Busy Block
CREATE TYPE calendar_busy_block_status_enum AS ENUM (
    'Active', 'Cancelled'
);

CREATE TABLE calendar_busy_block (
    busy_block_id     BIGSERIAL PRIMARY KEY,
    branch_id         BIGINT                          NOT NULL,
    external_event_id VARCHAR(255)                    NOT NULL,
    staff_id          BIGINT,
    starts_at         TIMESTAMP                       NOT NULL,
    ends_at           TIMESTAMP                       NOT NULL, -- > starts_at
    block_status      calendar_busy_block_status_enum NOT NULL,
    event_etag        VARCHAR(255),
    synced_at         TIMESTAMP                       NOT NULL,

    CONSTRAINT uq_calendar_busy_block_branch_event
        UNIQUE (branch_id, external_event_id),

    CONSTRAINT chk_calendar_busy_block_interval
        CHECK (ends_at > starts_at),

    CONSTRAINT fk_calendar_busy_block_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch (branch_id),

    CONSTRAINT fk_calendar_busy_block_staff
        FOREIGN KEY (staff_id)
            REFERENCES staff (staff_id)
);


-- ALTER FK
ALTER TABLE branch
    ADD CONSTRAINT fk_branch_google_connection
        FOREIGN KEY (google_connection_id)
            REFERENCES google_calendar_connection (connection_id);