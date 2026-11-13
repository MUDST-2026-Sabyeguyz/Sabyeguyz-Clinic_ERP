-- BACKEND FOLLOW-UP REQUIRED BEFORE DEPLOYMENT:
-- 1. Map TreatmentFeeRule.ruleVersion to rule_version.
-- 2. Assign version 1 to the first rule and increment for new versions.
-- 3. Keep the same version when editing an unused future rule.
-- 4. Prevent duplicate versions during concurrent creation.
-- 5. Map Appointment.contactMiddleName in Entity and request/response DTOs.
--
-- rule_version has no DEFAULT and is NOT NULL.
-- Existing backend inserts will fail unless rule_version is supplied.
-- Deploy this migration together with the backend changes.

-- Optional middle name for an appointment contact.
ALTER TABLE appointment
    ADD COLUMN contact_middle_name VARCHAR(100);


-- Add as nullable first so existing rules can be assigned a version.
ALTER TABLE treatment_fee_rule
    ADD COLUMN rule_version INT;

ALTER TABLE treatment_fee_rule
    ADD CONSTRAINT uq_treatment_fee_rule_version
        UNIQUE (rule_version);


-- Assign versions to existing rules in a stable order.
WITH numbered_rules AS (
    SELECT treatment_fee_rule_id,
           ROW_NUMBER() OVER (
               ORDER BY effective_from, treatment_fee_rule_id
           )::INT AS version_no
    FROM treatment_fee_rule
)
-- Replace missing data in the `rule_version` to SET rule_version NOT NULL
UPDATE treatment_fee_rule AS rule
SET rule_version = numbered.version_no
    FROM numbered_rules AS numbered
WHERE rule.treatment_fee_rule_id = numbered.treatment_fee_rule_id;

ALTER TABLE treatment_fee_rule
    ALTER COLUMN rule_version SET NOT NULL,
    ADD CONSTRAINT chk_treatment_fee_rule_version
        CHECK (rule_version >= 1);