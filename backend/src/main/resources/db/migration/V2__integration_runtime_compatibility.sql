-- The authoritative V1 is the latest feat/database schema (7172e9a).
-- Preserve fields used by the integrated v2 workflows without replacing that baseline.
ALTER TABLE appointment ADD COLUMN room_id BIGINT REFERENCES room(room_id);
ALTER TABLE course ADD COLUMN bonus_visits INT CHECK (bonus_visits >= 0);
ALTER TABLE patient ADD COLUMN gender_detail VARCHAR(50);
ALTER TABLE receipt ADD COLUMN taxable_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (taxable_amount >= 0);
ALTER TABLE receipt ADD COLUMN tax_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0);
ALTER TABLE service ADD COLUMN default_bonus_visits INT CHECK (default_bonus_visits >= 0);

-- The integrated appointment workflow includes attendance and no-show states.
ALTER TYPE appointment_status_enum ADD VALUE IF NOT EXISTS 'Arrived';
ALTER TYPE appointment_status_enum ADD VALUE IF NOT EXISTS 'InService';
ALTER TYPE appointment_status_enum ADD VALUE IF NOT EXISTS 'Completed';
ALTER TYPE appointment_status_enum ADD VALUE IF NOT EXISTS 'Rescheduled';
ALTER TYPE appointment_status_enum ADD VALUE IF NOT EXISTS 'NoShow';
ALTER TYPE gender_enum ADD VALUE IF NOT EXISTS 'Other';

CREATE FUNCTION next_receipt_running_number(p_branch_id BIGINT, p_month TEXT)
RETURNS INTEGER LANGUAGE SQL AS $$
    INSERT INTO receipt_sequence(branch_id, year_month, last_running_no)
    VALUES (p_branch_id, p_month, 1)
    ON CONFLICT (branch_id, year_month) DO UPDATE
        SET last_running_no = receipt_sequence.last_running_no + 1,
            last_running_at = CURRENT_TIMESTAMP
    RETURNING last_running_no;
$$;
