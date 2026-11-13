-- Status for branch closing, separate from individual closing status.
CREATE TYPE branch_closing_status_enum AS ENUM ('Open', 'Closed');

-- Records explicit monthly closing for each branch, including empty months.
CREATE TABLE branch_monthly_closing (
    branch_monthly_closing_id BIGSERIAL PRIMARY KEY,
    branch_id                 BIGINT      NOT NULL,
    closing_month             CHAR(7)     NOT NULL,
    closing_status branch_closing_status_enum NOT NULL DEFAULT 'Open',

    closed_at                 TIMESTAMP,
    closed_by                 BIGINT,

    CONSTRAINT uq_branch_monthly_closing_branch_month
        UNIQUE (branch_id, closing_month),

    CONSTRAINT chk_branch_monthly_closing_month
        CHECK (
            closing_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),

    CONSTRAINT chk_branch_monthly_closing_details
        CHECK (
            (
                closing_status = 'Open'
                AND closed_at IS NULL
                AND closed_by IS NULL
            )OR(
                closing_status = 'Closed'
                AND closed_at IS NOT NULL
                AND closed_by IS NOT NULL
            )
        ),

    CONSTRAINT fk_branch_monthly_closing_branch
        FOREIGN KEY (branch_id)
            REFERENCES branch (branch_id),

    CONSTRAINT fk_branch_monthly_closing_closed_by
        FOREIGN KEY (closed_by)
            REFERENCES app_user (user_id)
);