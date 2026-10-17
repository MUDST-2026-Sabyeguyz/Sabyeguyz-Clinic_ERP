-- V1002: commission requirements (from feat/database V3, adapted to be applied on top of V1001 and on populated databases).
--
-- Differences from the original V3 (see docs/commission-schema-migration.md):
--   * version number V1002 (V1001 may already be applied; Flyway rejects a lower pending version)
--   * data that cannot be derived is never invented: CHECK/FOREIGN KEY constraints added to existing tables are
--     created NOT VALID (enforced for every new or changed row at once), then validated at the end; if old rows
--     violate one, a NOTICE names it and it stays NOT VALID until the rows are corrected
--   * NOT NULL columns are backfilled from real data before they are made NOT NULL
--   * data in columns that are dropped is first copied to legacy_v1002_* tables

-- Guard: old Adjustment ledger entries must be converted to Staff_pay_adjustment by hand, never dropped silently
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM commission_allocation_ledger WHERE entry_type = 'Adjustment') THEN
        RAISE EXCEPTION 'V1002: commission_allocation_ledger contains Adjustment entries; convert them to staff_pay_adjustment before this migration';
    END IF;
END $$;

-- Snapshots of data in columns this migration drops
CREATE TABLE legacy_v1002_course_owner AS
    SELECT course_id, seller_staff_id, case_owner_staff_id FROM course;
CREATE TABLE legacy_v1002_usage_owner AS
    SELECT visit_usage_id, course_id, case_owner_staff_id FROM course_visit_usage;
CREATE TABLE legacy_v1002_fee_rule_scope AS
    SELECT treatment_fee_rule_id, staff_id, staff_group_id, service_id, service_group_id, percentage_base::text AS percentage_base
    FROM treatment_fee_rule;
CREATE TABLE legacy_v1002_staff_termination_policy AS
    SELECT staff_id, commission_after_termination_policy::text AS policy, termination_policy_locked_at
    FROM staff WHERE commission_after_termination_policy IS NOT NULL;

-- CREATE ENUM TYPES
CREATE TYPE closing_status_enum AS ENUM (
    'Open', 'Closed', 'Finalized'
);

CREATE TYPE adjustment_status_enum AS ENUM (
    'Pending', 'Approved', 'PartiallyApplied', 'Applied', 'ExternalReview', 'Cancelled'
);

CREATE TYPE application_status_enum AS ENUM (
    'Draft', 'Applied', 'Cancelled'
);


-- ALTER TABLE: fixed table and constraint
-- Service
ALTER TABLE service
    DROP CONSTRAINT chk_service_default_total_visits;

ALTER TABLE service
    ADD CONSTRAINT chk_service_default_total_visits
        CHECK (
            (service_type = 'Course'
                AND default_total_visits IS NOT NULL
                AND default_total_visits >= 1)
                OR
            (service_type = 'SingleVisit'
                AND default_total_visits IS NULL)
        ) NOT VALID;

-- Staff
ALTER TABLE staff
    DROP CONSTRAINT chk_staff_termination_policy,
    DROP COLUMN commission_after_termination_policy,
    DROP COLUMN termination_policy_locked_at;

ALTER TABLE staff
    ADD CONSTRAINT chk_staff_termination_date
        CHECK (termination_date IS NULL OR termination_date >= hire_date) NOT VALID;

-- Appointment Status History
ALTER TABLE appointment_status_history
    ALTER COLUMN changed_by DROP NOT NULL;

ALTER TABLE appointment_status_history
    ADD CONSTRAINT chk_appointment_status_history_auto_reason
        CHECK (
            changed_by IS NOT NULL
                OR NULLIF(BTRIM(reason), '') IS NOT NULL
        ) NOT VALID;

-- Patient Visit
ALTER TABLE patient_visit
    ADD COLUMN treating_staff_id BIGINT,
    ADD CONSTRAINT fk_patient_visit_treating_staff
        FOREIGN KEY (treating_staff_id)
            REFERENCES staff (staff_id) NOT VALID;

-- Treating staff of a started visit: the PT recorded on its course usage, else on its treatment, else the assigned PT
UPDATE patient_visit v
SET treating_staff_id = COALESCE(
        (SELECT u.treating_staff_id FROM course_visit_usage u WHERE u.visit_id = v.visit_id ORDER BY u.visit_usage_id LIMIT 1),
        (SELECT t.treating_staff_id FROM treatment t WHERE t.visit_id = v.visit_id AND t.treating_staff_id IS NOT NULL
         ORDER BY t.treatment_id LIMIT 1),
        v.assigned_staff_id)
WHERE v.visit_status IN ('InService', 'Completed');

ALTER TABLE patient_visit
    ADD CONSTRAINT chk_patient_visit_treating_staff
        CHECK (
            visit_status NOT IN ('InService', 'Completed')
            OR treating_staff_id IS NOT NULL
        ) NOT VALID,
    ADD CONSTRAINT chk_patient_visit_checkout
        CHECK (
            (visit_status = 'Completed'
                AND checkout_time IS NOT NULL
                AND checkout_time >= visit_date)
            OR
            (visit_status <> 'Completed'
                AND checkout_time IS NULL)
        ) NOT VALID;

-- Commission Tier
ALTER TABLE commission_tier
    ADD CONSTRAINT uq_commission_tier_scheme_order
        UNIQUE (commission_scheme_id, tier_order);

-- Treatment Fee Rule
ALTER TABLE treatment_fee_rule
DROP CONSTRAINT chk_treatment_fee_rule_percentage_base,
    DROP COLUMN staff_id,
    DROP COLUMN staff_group_id,
    DROP COLUMN service_id,
    DROP COLUMN service_group_id,
    DROP COLUMN percentage_base,
    DROP CONSTRAINT chk_treatment_fee_rule_value;

ALTER TABLE treatment_fee_rule
    ADD CONSTRAINT chk_treatment_fee_rule_value
        CHECK (
            (fee_type = 'Fixed' AND fee_value >= 0)
                OR
            (fee_type = 'Percentage' AND fee_value BETWEEN 0 AND 100)
            ) NOT VALID;

-- Course
ALTER TABLE course
    DROP CONSTRAINT chk_course_commission_amounts,
    DROP COLUMN case_owner_staff_id,
    DROP COLUMN gross_commission_allocated_total,
    DROP COLUMN entitled_visits,
    ALTER COLUMN commission_outstanding DROP NOT NULL,
    ADD COLUMN treatment_fee_base_amount NUMERIC(12, 2);

ALTER TABLE course
    ADD CONSTRAINT chk_course_commission_amounts
        CHECK (
            (total_course_commission_pool IS NULL
                OR total_course_commission_pool >= 0)
                AND (commission_allocation_per_visit IS NULL
                OR commission_allocation_per_visit >= 0)
                AND owner_net_commission_released_total >= 0
                AND substitute_treatment_fee_total >= 0
                AND (commission_outstanding IS NULL
                OR commission_outstanding >= 0)
            ) NOT VALID,
    ADD CONSTRAINT chk_course_locked_commission
        CHECK (
            commission_status <> 'Locked'
            OR (
                locked_commission_rate IS NOT NULL
                AND total_course_commission_pool IS NOT NULL
                AND commission_allocation_per_visit IS NOT NULL
                AND commission_generated_date IS NOT NULL
            )
        ) NOT VALID,
    ADD CONSTRAINT chk_course_visit_balance
        CHECK (visits_used + remaining_visits <= total_visits) NOT VALID,
    ADD CONSTRAINT chk_course_treatment_fee_base
        CHECK (treatment_fee_base_amount >= 0) NOT VALID;

-- Frozen net amount paid for the course: the sale line amount after the bill discount; falls back to the price
UPDATE course c
SET treatment_fee_base_amount = COALESCE(
        (SELECT st.amount - st.allocated_bill_discount_amount FROM sales_transaction st WHERE st.transaction_id = c.sale_transaction_id),
        c.course_price);

ALTER TABLE course
    ALTER COLUMN treatment_fee_base_amount SET NOT NULL;

-- Course Shared Member
ALTER TABLE course_shared_member
    ADD CONSTRAINT chk_course_shared_member_dates
        CHECK (ended_at IS NULL OR ended_at >= joined_at) NOT VALID;

-- Sales Transaction
ALTER TABLE sales_transaction
    ADD CONSTRAINT chk_sales_transaction_line_totals
        CHECK (
            amount = quantity * unit_price - line_discount_amount
                AND allocated_bill_discount_amount <= amount
                AND commissionable_amount = amount
            ) NOT VALID,
    ADD CONSTRAINT chk_sales_transaction_required_staff_visit
        CHECK (
            (transaction_type <> 'SingleVisit' OR visit_id IS NOT NULL)
            AND (transaction_type <> 'CoursePurchase'
                 OR salesperson_staff_id IS NOT NULL)
            AND (transaction_type <> 'SingleVisit'
                 OR transaction_status = FALSE
                 OR treating_staff_id IS NOT NULL)
        ) NOT VALID;

-- Course Transfer
ALTER TABLE course_transfer
    ADD CONSTRAINT fk_course_transfer_from_entitlement
        FOREIGN KEY (course_id, from_patient_id)
            REFERENCES course_entitlement (course_id, patient_id) NOT VALID,
    ADD CONSTRAINT fk_course_transfer_to_entitlement
        FOREIGN KEY (course_id, to_patient_id)
            REFERENCES course_entitlement (course_id, patient_id) NOT VALID;

-- Course Visit Usage
ALTER TABLE course_visit_usage
    DROP CONSTRAINT chk_course_visit_usage_print_required,
    DROP COLUMN case_owner_staff_id,
    DROP COLUMN gross_commission_allocation,
    DROP COLUMN print_required,
    ADD COLUMN usage_sequence INT,
    ADD COLUMN replaces_usage_id BIGINT,
    ADD CONSTRAINT chk_course_visit_usage_sequence
        CHECK (usage_sequence >= 1) NOT VALID,
    ADD CONSTRAINT fk_course_visit_usage_replaces
        FOREIGN KEY (replaces_usage_id)
        REFERENCES course_visit_usage (visit_usage_id) NOT VALID,
    ADD CONSTRAINT uq_course_visit_usage_replaces
        UNIQUE (replaces_usage_id),
    ADD CONSTRAINT chk_course_visit_usage_completed_fee
        CHECK (
            usage_status <> 'Completed'
            OR substitute_treatment_fee_amount IS NOT NULL
        ) NOT VALID;

    -- One Completed usage per course and usage_sequence
CREATE UNIQUE INDEX uq_course_visit_usage_completed_sequence
    ON course_visit_usage (course_id, usage_sequence)
    WHERE usage_status = 'Completed';

-- Position of each usage inside its course (all users together): Completed usages are numbered in order of use;
-- a Reversed usage takes the position of the Completed usage that follows it, or the next free position
UPDATE course_visit_usage u
SET usage_sequence = n.seq
FROM (
    SELECT visit_usage_id,
           ROW_NUMBER() OVER (PARTITION BY course_id ORDER BY visit_usage_id) AS seq
    FROM course_visit_usage
    WHERE usage_status = 'Completed'
) n
WHERE u.visit_usage_id = n.visit_usage_id;

UPDATE course_visit_usage r
SET usage_sequence = COALESCE(
        (SELECT MIN(c.usage_sequence) FROM course_visit_usage c
         WHERE c.course_id = r.course_id AND c.usage_status = 'Completed' AND c.visit_usage_id > r.visit_usage_id),
        (SELECT COALESCE(MAX(c.usage_sequence), 0) + 1 FROM course_visit_usage c
         WHERE c.course_id = r.course_id AND c.usage_status = 'Completed'))
WHERE r.usage_status <> 'Completed';

ALTER TABLE course_visit_usage
    ALTER COLUMN usage_sequence SET NOT NULL;

-- Course Ledger
ALTER TABLE course_ledger
    DROP CONSTRAINT chk_course_ledger_qty_direction,
    DROP CONSTRAINT chk_course_ledger_entry_source;

    -- NEW ENUM to add 'Cancellation'
CREATE TYPE entry_type_new_enum AS ENUM (
    'Purchase', 'Treatment', 'TransferOut', 'TransferIn',
    'Adjustment', 'Reversal', 'Cancellation'
);

ALTER TABLE course_ledger
ALTER COLUMN entry_type TYPE entry_type_new_enum
        USING entry_type::text::entry_type_new_enum;

DROP TYPE entry_type_enum;

ALTER TYPE entry_type_new_enum
    RENAME TO entry_type_enum;

ALTER TABLE course_ledger
    ADD COLUMN course_refund_item_id BIGINT,
    ADD COLUMN cancellation_id BIGINT,
    ADD CONSTRAINT fk_course_ledger_entitlement
        FOREIGN KEY (course_id, patient_id)
        REFERENCES course_entitlement (course_id, patient_id) NOT VALID,
    ADD CONSTRAINT fk_course_ledger_cancellation
        FOREIGN KEY (cancellation_id)
        REFERENCES transaction_cancellation (cancellation_id) NOT VALID;
    -- fk: course_refund_item_id

ALTER TABLE course_ledger
    ADD CONSTRAINT chk_course_ledger_qty_direction
        CHECK (
            (entry_type IN ('Purchase', 'TransferIn') AND qty_change > 0)
                OR (entry_type IN ('Treatment', 'TransferOut', 'Cancellation')
                AND qty_change < 0)
                OR (entry_type IN ('Adjustment', 'Reversal')
                AND qty_change <> 0)
            ) NOT VALID,
    ADD CONSTRAINT chk_course_ledger_entry_source
        CHECK (
            (entry_type IN ('TransferOut', 'TransferIn')
                AND transfer_id IS NOT NULL
                AND visit_usage_id IS NULL
                AND sales_transaction_id IS NULL
                AND reverses_ledger_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL
                AND course_refund_item_id IS NULL
                AND cancellation_id IS NULL)
            OR
            (entry_type = 'Treatment'
                AND visit_usage_id IS NOT NULL
                AND transfer_id IS NULL
                AND sales_transaction_id IS NULL
                AND reverses_ledger_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL
                AND course_refund_item_id IS NULL
                AND cancellation_id IS NULL)
            OR
            (entry_type = 'Purchase'
                AND sales_transaction_id IS NOT NULL
                AND transfer_id IS NULL
                AND visit_usage_id IS NULL
                AND reverses_ledger_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL
                AND course_refund_item_id IS NULL
                AND cancellation_id IS NULL)
            OR
            (entry_type = 'Adjustment'
                AND NULLIF(BTRIM(remark), '') IS NOT NULL
                AND adjustment_approved_by IS NOT NULL
                AND adjustment_approved_at IS NOT NULL
                AND transfer_id IS NULL
                AND visit_usage_id IS NULL
                AND sales_transaction_id IS NULL
                AND reverses_ledger_id IS NULL
                AND course_refund_item_id IS NULL
                AND cancellation_id IS NULL)
            OR
            (entry_type = 'Reversal'
                AND reverses_ledger_id IS NOT NULL
                AND NOT (transfer_id IS NOT NULL AND visit_usage_id IS NOT NULL)
                AND sales_transaction_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL
                AND course_refund_item_id IS NULL
                AND cancellation_id IS NULL)
            OR
            (entry_type = 'Cancellation'
                AND num_nonnulls(course_refund_item_id, cancellation_id) = 1
                AND transfer_id IS NULL
                AND visit_usage_id IS NULL
                AND sales_transaction_id IS NULL
                AND reverses_ledger_id IS NULL
                AND adjustment_approved_by IS NULL
                AND adjustment_approved_at IS NULL)
        ) NOT VALID;

-- Course Usage Print
    -- course_usage_print.course_id == course_visit_usage.course_id
ALTER TABLE course_visit_usage
    ADD CONSTRAINT uq_course_visit_usage_id_course
        UNIQUE (visit_usage_id, course_id);

ALTER TABLE course_usage_print
    ADD CONSTRAINT fk_course_usage_print_usage_course
        FOREIGN KEY (visit_usage_id, course_id)
            REFERENCES course_visit_usage (visit_usage_id, course_id) NOT VALID;

-- Refund Transaction
ALTER TABLE refund_transaction
    ADD COLUMN approved_by BIGINT,
    ADD COLUMN approved_at TIMESTAMP,
    ADD CONSTRAINT fk_refund_transaction_approved_by
        FOREIGN KEY (approved_by)
        REFERENCES app_user (user_id) NOT VALID,
    ADD CONSTRAINT chk_refund_transaction_approval
        CHECK (
            (approved_by IS NULL AND approved_at IS NULL)
            OR
            (approved_by IS NOT NULL AND approved_at IS NOT NULL)
        ) NOT VALID,
    ADD CONSTRAINT chk_refund_transaction_completed_approval
        CHECK (
            refund_status <> 'Completed'
            OR approved_by IS NOT NULL
        ) NOT VALID;

-- Course Refund Item
CREATE TABLE course_refund_item (
    course_refund_item_id BIGSERIAL PRIMARY KEY,        -- auto-increment
    refund_id             BIGINT         NOT NULL,
    course_id             BIGINT         NOT NULL,
    refund_amount         NUMERIC(12, 2) NOT NULL,      -- > 0
    cancelled_visits      INT            NOT NULL,      -- >= 1

    CONSTRAINT uq_course_refund_item_refund_course
        UNIQUE (refund_id, course_id),

    CONSTRAINT chk_course_refund_item_amount
        CHECK (refund_amount > 0),

    CONSTRAINT chk_course_refund_item_cancelled_visits
        CHECK (cancelled_visits >= 1),

    CONSTRAINT fk_course_refund_item_refund
        FOREIGN KEY (refund_id)
            REFERENCES refund_transaction (refund_id),

    CONSTRAINT fk_course_refund_item_course
        FOREIGN KEY (course_id)
            REFERENCES course (course_id)
);

-- Monthly Commission Closing
ALTER TABLE monthly_commission_closing
    ALTER COLUMN closing_status DROP DEFAULT;

ALTER TABLE monthly_commission_closing
ALTER COLUMN closing_status TYPE closing_status_enum
    USING (
        CASE
            WHEN closing_status = TRUE THEN 'Closed'
            ELSE 'Open'
        END
    )::closing_status_enum;

ALTER TABLE monthly_commission_closing
    ALTER COLUMN closing_status SET DEFAULT 'Open',
    ADD COLUMN finalized_at TIMESTAMP,
    ADD COLUMN finalized_by BIGINT,
    ADD CONSTRAINT fk_monthly_closing_finalized_by
        FOREIGN KEY (finalized_by)
            REFERENCES app_user (user_id) NOT VALID,
    ADD CONSTRAINT chk_monthly_closing_status_details
        CHECK (
            (closing_status = 'Open'
                AND closed_at IS NULL
                AND closed_by IS NULL
                AND finalized_at IS NULL
                AND finalized_by IS NULL)
            OR
            (closing_status = 'Closed'
                AND closed_at IS NOT NULL
                AND closed_by IS NOT NULL
                AND finalized_at IS NULL
                AND finalized_by IS NULL)
            OR
            (closing_status = 'Finalized'
                AND closed_at IS NOT NULL
                AND closed_by IS NOT NULL
                AND finalized_at IS NOT NULL
                AND finalized_by IS NOT NULL)
        ) NOT VALID,
    ADD CONSTRAINT chk_monthly_closing_locked_rate_required
        CHECK (
            closing_status = 'Open'
            OR locked_commission_rate IS NOT NULL
        ) NOT VALID;

-- Staff Monthly Payment
ALTER TABLE staff_monthly_payment
    DROP CONSTRAINT chk_staff_monthly_payment_paid_at;

    -- NEW ENUM
CREATE TYPE payment_status_new_enum
    AS ENUM ('Open', 'Closed', 'Paid');

ALTER TABLE staff_monthly_payment
    ALTER COLUMN payment_status TYPE payment_status_new_enum
    USING (
        CASE payment_status::text
            WHEN 'Unpaid' THEN 'Open'
            ELSE payment_status::text
        END
    )::payment_status_new_enum;

DROP TYPE payment_status_enum;

ALTER TYPE payment_status_new_enum
    RENAME TO payment_status_enum;

    --
ALTER TABLE staff_monthly_payment
    ADD COLUMN closing_id BIGINT,
    ADD COLUMN course_commission_amount NUMERIC(12, 2),
    ADD COLUMN single_visit_fee_amount NUMERIC(12, 2),
    ADD COLUMN substitute_fee_amount NUMERIC(12, 2),
    ADD COLUMN adjustment_amount NUMERIC(12, 2),
    ADD COLUMN net_payment_amount NUMERIC(12, 2),
    ADD CONSTRAINT fk_staff_monthly_payment_closing
        FOREIGN KEY (closing_id)
            REFERENCES monthly_commission_closing (closing_id) NOT VALID;

ALTER TABLE staff_monthly_payment
    ADD CONSTRAINT chk_staff_monthly_payment_paid_at
        CHECK (
            (payment_status = 'Paid' AND paid_at IS NOT NULL)
                OR
            (payment_status IN ('Open', 'Closed') AND paid_at IS NULL)
            ) NOT VALID,
    ADD CONSTRAINT chk_staff_monthly_payment_amounts
        CHECK (
            (course_commission_amount IS NULL OR course_commission_amount >= 0)
            AND (single_visit_fee_amount IS NULL OR single_visit_fee_amount >= 0)
            AND (substitute_fee_amount IS NULL OR substitute_fee_amount >= 0)
            AND (net_payment_amount IS NULL OR net_payment_amount >= 0)
            AND (
                payment_status = 'Open'
                OR (
                    course_commission_amount IS NOT NULL
                    AND single_visit_fee_amount IS NOT NULL
                    AND substitute_fee_amount IS NOT NULL
                    AND adjustment_amount IS NOT NULL
                    AND net_payment_amount IS NOT NULL
                    AND net_payment_amount =
                        course_commission_amount
                        + single_visit_fee_amount
                        + substitute_fee_amount
                        + adjustment_amount
                )
            )
        ) NOT VALID;

-- Commission Allocation Ledger
DROP INDEX uq_commission_allocation_initial_usage;
DROP INDEX uq_commission_allocation_reversal_reference;

    -- fix it: referenced 'Adjustment'
ALTER TABLE commission_allocation_ledger
    DROP CONSTRAINT chk_commission_allocation_amount_direction,
    DROP CONSTRAINT chk_commission_allocation_entry_details,
    DROP CONSTRAINT chk_commission_allocation_rate_or_amount;

    -- NEW ENUM: commission_allocation_entry_type_enum -> compensation_entry_type_enum
CREATE TYPE commission_allocation_entry_type_new_enum
    AS ENUM ('Allocation', 'Reversal');

ALTER TABLE commission_allocation_ledger
    ALTER COLUMN entry_type TYPE commission_allocation_entry_type_new_enum
        USING entry_type::text::commission_allocation_entry_type_new_enum;

DROP TYPE commission_allocation_entry_type_enum;

ALTER TYPE commission_allocation_entry_type_new_enum
    RENAME TO compensation_entry_type_enum;        -- for commission_allocation_ledger and Single_visit_fee_ledger

    -- fixed: Commission
ALTER TABLE commission_allocation_ledger
    RENAME COLUMN case_owner_staff_id TO owner_staff_id;

ALTER TABLE commission_allocation_ledger
    RENAME COLUMN adjustment_reference_id TO reverses_allocation_id;

ALTER TABLE commission_allocation_ledger
    ALTER COLUMN treatment_fee_calculation_base TYPE NUMERIC(12, 4)
        USING CASE WHEN treatment_fee_calculation_base ~ '^[0-9]+(\.[0-9]+)?$'
                   THEN treatment_fee_calculation_base::NUMERIC(12, 4) END,       -- >= 0
    DROP COLUMN gross_commission_allocation,
    DROP COLUMN company_top_up_amount,
    DROP COLUMN overflow_policy_used,
    DROP COLUMN adjustment_reason,
    DROP COLUMN adjustment_approved_by,
    DROP COLUMN adjustment_approved_at,
    ADD COLUMN allocation_version INT;                              -- >= 1

-- Allocations of one usage are versioned in order; a Reversal carries the version of the allocation it reverses
UPDATE commission_allocation_ledger a
SET allocation_version = n.v
FROM (
    SELECT allocation_id, ROW_NUMBER() OVER (PARTITION BY visit_usage_id ORDER BY allocation_id) AS v
    FROM commission_allocation_ledger
    WHERE entry_type = 'Allocation'
) n
WHERE a.allocation_id = n.allocation_id;

UPDATE commission_allocation_ledger r
SET allocation_version = COALESCE(
        (SELECT o.allocation_version FROM commission_allocation_ledger o WHERE o.allocation_id = r.reverses_allocation_id),
        (SELECT MAX(o.allocation_version) FROM commission_allocation_ledger o
         WHERE o.visit_usage_id = r.visit_usage_id AND o.entry_type = 'Allocation'),
        1)
WHERE r.entry_type = 'Reversal';

ALTER TABLE commission_allocation_ledger
    ALTER COLUMN allocation_version SET NOT NULL;

ALTER TABLE commission_allocation_ledger
    ADD CONSTRAINT chk_commission_allocation_version
        CHECK (allocation_version >= 1) NOT VALID,

    ADD CONSTRAINT chk_commission_allocation_fee_snapshot
        CHECK (
            treatment_fee_rate_or_amount IS NULL
            OR (
                treatment_fee_type IS NOT NULL
                AND (
                    (treatment_fee_type = 'Fixed'
                        AND treatment_fee_rate_or_amount >= 0)
                    OR
                    (treatment_fee_type = 'Percentage'
                        AND treatment_fee_rate_or_amount BETWEEN 0 AND 100)
                )
            )
        ) NOT VALID,

    ADD CONSTRAINT chk_commission_allocation_calculation_base
        CHECK (
            treatment_fee_calculation_base IS NULL
            OR treatment_fee_calculation_base >= 0
        ) NOT VALID,

    ADD CONSTRAINT chk_commission_allocation_amount_direction
        CHECK (
            (entry_type = 'Allocation'
                AND treatment_fee_amount >= 0
                AND owner_net_commission >= 0)
            OR
            (entry_type = 'Reversal'
                AND treatment_fee_amount <= 0
                AND owner_net_commission <= 0)
        ) NOT VALID,

    ADD CONSTRAINT chk_commission_allocation_entry_details
        CHECK (
            (entry_type = 'Allocation'
                AND reverses_allocation_id IS NULL
                AND reversal_reason IS NULL)
            OR
            (entry_type = 'Reversal'
                AND reverses_allocation_id IS NOT NULL
                AND NULLIF(BTRIM(reversal_reason), '') IS NOT NULL)
        ) NOT VALID,

    ADD CONSTRAINT uq_commission_allocation_usage_version_type
        UNIQUE (visit_usage_id, allocation_version, entry_type);

    -- One Reversal per original Allocation
CREATE UNIQUE INDEX uq_commission_allocation_reversal_reference
    ON commission_allocation_ledger (reverses_allocation_id)
    WHERE entry_type = 'Reversal';

-- Single Visit Fee Ledger
CREATE TABLE single_visit_fee_ledger (
    fee_id                BIGSERIAL                    PRIMARY KEY,
    visit_id              BIGINT                       NOT NULL,
    staff_id              BIGINT                       NOT NULL,
    treatment_fee_rule_id BIGINT                       NOT NULL,
    service_date          DATE                         NOT NULL,

    fee_type              fee_type_enum                NOT NULL,
    fee_value             NUMERIC(12, 4)               NOT NULL,
    calculation_base      NUMERIC(12, 2)               NOT NULL,    -- >= 0
    fee_amount            NUMERIC(12, 2)               NOT NULL,

    entry_type            compensation_entry_type_enum NOT NULL,
    reverses_fee_id       BIGINT,
    monthly_payment_id    BIGINT,
    reason                VARCHAR(255),

    created_at            TIMESTAMP                    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            BIGINT                       NOT NULL,
    fee_version           INT                          NOT NULL,    -- >= 1

    CONSTRAINT uq_single_visit_fee_version_type
        UNIQUE (visit_id, fee_version, entry_type),

    -- One Reversal per original fee event
    CONSTRAINT uq_single_visit_fee_reverses
        UNIQUE (reverses_fee_id),

    CONSTRAINT chk_single_visit_fee_version
        CHECK (fee_version >= 1),

    CONSTRAINT chk_single_visit_fee_value
        CHECK (
            (fee_type = 'Fixed' AND fee_value >= 0)
            OR
            (fee_type = 'Percentage' AND fee_value BETWEEN 0 AND 100)
        ),

    CONSTRAINT chk_single_visit_fee_base
        CHECK (calculation_base >= 0),

    CONSTRAINT chk_single_visit_fee_entry
        CHECK (
            (entry_type = 'Allocation'
                AND fee_amount >= 0
                AND reverses_fee_id IS NULL
                AND reason IS NULL)
                OR
            (entry_type = 'Reversal'
                AND fee_amount <= 0
                AND reverses_fee_id IS NOT NULL
                AND NULLIF(BTRIM(reason), '') IS NOT NULL)
            ),

    CONSTRAINT fk_single_visit_fee_visit
        FOREIGN KEY (visit_id)
            REFERENCES patient_visit (visit_id),

    CONSTRAINT fk_single_visit_fee_staff
        FOREIGN KEY (staff_id)
            REFERENCES staff (staff_id),

    CONSTRAINT fk_single_visit_fee_rule
        FOREIGN KEY (treatment_fee_rule_id)
            REFERENCES treatment_fee_rule (treatment_fee_rule_id),

    CONSTRAINT fk_single_visit_fee_reverses
        FOREIGN KEY (reverses_fee_id)
            REFERENCES single_visit_fee_ledger (fee_id),

    CONSTRAINT fk_single_visit_fee_monthly_payment
        FOREIGN KEY (monthly_payment_id)
            REFERENCES staff_monthly_payment (salary_payment_id),

    CONSTRAINT fk_single_visit_fee_created_by
        FOREIGN KEY (created_by)
            REFERENCES app_user (user_id)
);

-- Staff Pay Adjustment
CREATE TABLE staff_pay_adjustment (
    adjustment_id       BIGSERIAL PRIMARY KEY,
    staff_id            BIGINT                 NOT NULL,
    original_month      CHAR(7)                NOT NULL,
    applied_month       CHAR(7)                NOT NULL,    -- > original_month
    amount              NUMERIC(12, 2)         NOT NULL,    -- !=0

    allocation_id       BIGINT,
    single_visit_fee_id BIGINT,
    course_id           BIGINT,
    visit_id            BIGINT,
    sale_transaction_id BIGINT,

    reason              VARCHAR(255)           NOT NULL,
    approved_by         BIGINT,
    approved_at         TIMESTAMP,

    processed_amount    NUMERIC(12, 2)         NOT NULL DEFAULT 0,
    remaining_amount    NUMERIC(12, 2)         NOT NULL,
    adjustment_status   adjustment_status_enum NOT NULL DEFAULT 'Pending',

    created_at          TIMESTAMP              NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT                 NOT NULL,

    CONSTRAINT chk_staff_pay_adjustment_months
        CHECK (
            original_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'
            AND applied_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'
            AND applied_month > original_month
        ),

    CONSTRAINT chk_staff_pay_adjustment_amount
        CHECK (amount <> 0),

    CONSTRAINT chk_staff_pay_adjustment_source
        CHECK (
            num_nonnulls(
                allocation_id,
                single_visit_fee_id,
                course_id,
                visit_id,
                sale_transaction_id
            ) >= 1
        ),

    CONSTRAINT chk_staff_pay_adjustment_reason
        CHECK (NULLIF(BTRIM(reason), '') IS NOT NULL),

    CONSTRAINT chk_staff_pay_adjustment_approval
        CHECK (
            (approved_by IS NULL AND approved_at IS NULL)
            OR
            (approved_by IS NOT NULL AND approved_at IS NOT NULL)
        ),

    CONSTRAINT chk_staff_pay_adjustment_status_approval
        CHECK (
            adjustment_status NOT IN (
                'Approved', 'PartiallyApplied', 'Applied', 'ExternalReview'
            )
            OR approved_by IS NOT NULL
        ),

    CONSTRAINT chk_staff_pay_adjustment_balance
        CHECK (
            processed_amount >= 0
            AND processed_amount <= ABS(amount)
            AND remaining_amount = ABS(amount) - processed_amount
        ),

    CONSTRAINT fk_staff_pay_adjustment_staff
        FOREIGN KEY (staff_id)
        REFERENCES staff (staff_id),

    CONSTRAINT fk_staff_pay_adjustment_allocation
        FOREIGN KEY (allocation_id)
        REFERENCES commission_allocation_ledger (allocation_id),

    CONSTRAINT fk_staff_pay_adjustment_single_visit_fee
        FOREIGN KEY (single_visit_fee_id)
        REFERENCES single_visit_fee_ledger (fee_id),

    CONSTRAINT fk_staff_pay_adjustment_course
        FOREIGN KEY (course_id)
        REFERENCES course (course_id),

    CONSTRAINT fk_staff_pay_adjustment_visit
        FOREIGN KEY (visit_id)
        REFERENCES patient_visit (visit_id),

    CONSTRAINT fk_staff_pay_adjustment_sale_transaction
        FOREIGN KEY (sale_transaction_id)
        REFERENCES sales_transaction (transaction_id),

    CONSTRAINT fk_staff_pay_adjustment_approved_by
        FOREIGN KEY (approved_by)
        REFERENCES app_user (user_id),

    CONSTRAINT fk_staff_pay_adjustment_created_by
        FOREIGN KEY (created_by)
        REFERENCES app_user (user_id)
);

-- Adjustment Application
CREATE TABLE adjustment_application (
    application_id     BIGSERIAL PRIMARY KEY,
    adjustment_id      BIGINT                  NOT NULL,
    monthly_payment_id BIGINT                  NOT NULL,
    amount             NUMERIC(12, 2)          NOT NULL,    -- !=0
    application_status application_status_enum NOT NULL DEFAULT 'Draft',

    approved_by        BIGINT,
    approved_at        TIMESTAMP,
    approval_reference VARCHAR(255),

    created_at         TIMESTAMP               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         BIGINT                  NOT NULL,

    CONSTRAINT chk_adjustment_application_amount
        CHECK (amount <> 0),

    CONSTRAINT chk_adjustment_application_approval_pair
        CHECK (
            (approved_by IS NULL AND approved_at IS NULL)
            OR
            (approved_by IS NOT NULL AND approved_at IS NOT NULL)
        ),

    CONSTRAINT chk_adjustment_application_applied_approval
        CHECK (
            application_status <> 'Applied'
                OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
        ),

    CONSTRAINT chk_adjustment_application_deduction_reference
        CHECK (
            application_status <> 'Applied'
                OR amount > 0
                OR NULLIF(BTRIM(approval_reference), '') IS NOT NULL
        ),

    CONSTRAINT fk_adjustment_application_adjustment
        FOREIGN KEY (adjustment_id)
            REFERENCES staff_pay_adjustment (adjustment_id),

    CONSTRAINT fk_adjustment_application_monthly_payment
        FOREIGN KEY (monthly_payment_id)
            REFERENCES staff_monthly_payment (salary_payment_id),

    CONSTRAINT fk_adjustment_application_approved_by
        FOREIGN KEY (approved_by)
            REFERENCES app_user (user_id),

    CONSTRAINT fk_adjustment_application_created_by
        FOREIGN KEY (created_by)
            REFERENCES app_user (user_id)
);

-- Audit Log
ALTER TABLE audit_log
    ALTER COLUMN changed_by DROP NOT NULL;


-- ALTER FK
ALTER TABLE course_ledger
    ADD CONSTRAINT fk_course_ledger_refund_item
        FOREIGN KEY (course_refund_item_id)
            REFERENCES course_refund_item (course_refund_item_id) NOT VALID;


-- DROP OLD ENUM TYPES
DROP TYPE commission_after_termination_policy_enum;
DROP TYPE percentage_base_enum;
DROP TYPE overflow_policy_enum;


-- Waiting for fixed code then DROP TABLE: Keep them in V3, Drop them in a later migration after backend stops using it.
    -- user_branch_access
    -- service_group
    -- service_group_member
    -- Staff_group
    -- Staff_group_member
    -- Course_sale_staff_share


-- Validate the constraints that were added NOT VALID. Old rows that violate one are reported; the constraint then
-- stays NOT VALID (it already applies to every new or changed row) until those rows are corrected.
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN SELECT * FROM (VALUES
        ('service', 'chk_service_default_total_visits'),
        ('staff', 'chk_staff_termination_date'),
        ('appointment_status_history', 'chk_appointment_status_history_auto_reason'),
        ('patient_visit', 'fk_patient_visit_treating_staff'),
        ('patient_visit', 'chk_patient_visit_treating_staff'),
        ('patient_visit', 'chk_patient_visit_checkout'),
        ('treatment_fee_rule', 'chk_treatment_fee_rule_value'),
        ('course', 'chk_course_commission_amounts'),
        ('course', 'chk_course_locked_commission'),
        ('course', 'chk_course_visit_balance'),
        ('course', 'chk_course_treatment_fee_base'),
        ('course_shared_member', 'chk_course_shared_member_dates'),
        ('sales_transaction', 'chk_sales_transaction_line_totals'),
        ('sales_transaction', 'chk_sales_transaction_required_staff_visit'),
        ('course_transfer', 'fk_course_transfer_from_entitlement'),
        ('course_transfer', 'fk_course_transfer_to_entitlement'),
        ('course_visit_usage', 'chk_course_visit_usage_sequence'),
        ('course_visit_usage', 'fk_course_visit_usage_replaces'),
        ('course_visit_usage', 'chk_course_visit_usage_completed_fee'),
        ('course_ledger', 'fk_course_ledger_entitlement'),
        ('course_ledger', 'fk_course_ledger_cancellation'),
        ('course_ledger', 'chk_course_ledger_qty_direction'),
        ('course_ledger', 'chk_course_ledger_entry_source'),
        ('course_usage_print', 'fk_course_usage_print_usage_course'),
        ('refund_transaction', 'fk_refund_transaction_approved_by'),
        ('refund_transaction', 'chk_refund_transaction_approval'),
        ('refund_transaction', 'chk_refund_transaction_completed_approval'),
        ('monthly_commission_closing', 'fk_monthly_closing_finalized_by'),
        ('monthly_commission_closing', 'chk_monthly_closing_status_details'),
        ('monthly_commission_closing', 'chk_monthly_closing_locked_rate_required'),
        ('staff_monthly_payment', 'fk_staff_monthly_payment_closing'),
        ('staff_monthly_payment', 'chk_staff_monthly_payment_paid_at'),
        ('staff_monthly_payment', 'chk_staff_monthly_payment_amounts'),
        ('commission_allocation_ledger', 'chk_commission_allocation_version'),
        ('commission_allocation_ledger', 'chk_commission_allocation_fee_snapshot'),
        ('commission_allocation_ledger', 'chk_commission_allocation_calculation_base'),
        ('commission_allocation_ledger', 'chk_commission_allocation_amount_direction'),
        ('commission_allocation_ledger', 'chk_commission_allocation_entry_details'),
        ('course_ledger', 'fk_course_ledger_refund_item')
    ) AS v (tbl, name)
    LOOP
        BEGIN
            EXECUTE format('ALTER TABLE %I VALIDATE CONSTRAINT %I', c.tbl, c.name);
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'V1002: existing rows of % violate % (left NOT VALID): %', c.tbl, c.name, SQLERRM;
        END;
    END LOOP;
END $$;
