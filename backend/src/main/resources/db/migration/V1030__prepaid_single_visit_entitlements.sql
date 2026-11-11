CREATE TABLE single_visit_entitlement (
    single_visit_entitlement_id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL UNIQUE,
    purchased_quantity INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    CONSTRAINT chk_single_visit_entitlement_quantity CHECK (purchased_quantity > 0),
    CONSTRAINT fk_single_visit_entitlement_transaction
        FOREIGN KEY (transaction_id) REFERENCES sales_transaction (transaction_id),
    CONSTRAINT fk_single_visit_entitlement_created_by
        FOREIGN KEY (created_by) REFERENCES app_user (user_id)
);

CREATE TABLE single_visit_entitlement_usage (
    single_visit_entitlement_usage_id BIGSERIAL PRIMARY KEY,
    single_visit_entitlement_id BIGINT NOT NULL,
    appointment_id BIGINT NOT NULL,
    visit_id BIGINT NOT NULL,
    used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_by BIGINT NOT NULL,
    reversed_at TIMESTAMP,
    reversed_by BIGINT,
    reversal_reason TEXT,
    CONSTRAINT uq_single_visit_entitlement_appointment UNIQUE (appointment_id),
    CONSTRAINT fk_single_visit_entitlement_usage_entitlement
        FOREIGN KEY (single_visit_entitlement_id)
        REFERENCES single_visit_entitlement (single_visit_entitlement_id),
    CONSTRAINT fk_single_visit_entitlement_usage_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointment (appointment_id),
    CONSTRAINT fk_single_visit_entitlement_usage_visit
        FOREIGN KEY (visit_id) REFERENCES patient_visit (visit_id),
    CONSTRAINT fk_single_visit_entitlement_usage_used_by
        FOREIGN KEY (used_by) REFERENCES app_user (user_id),
    CONSTRAINT fk_single_visit_entitlement_usage_reversed_by
        FOREIGN KEY (reversed_by) REFERENCES app_user (user_id)
);

CREATE INDEX ix_single_visit_entitlement_usage_entitlement
    ON single_visit_entitlement_usage (single_visit_entitlement_id, reversed_at);
