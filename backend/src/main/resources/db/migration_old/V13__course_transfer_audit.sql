CREATE SEQUENCE course_transfer_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE course_transfers (
    id BIGINT DEFAULT nextval('course_transfer_id_seq') PRIMARY KEY,
    source_purchase_id BIGINT NOT NULL REFERENCES course_purchases(id),
    destination_purchase_id BIGINT NOT NULL REFERENCES course_purchases(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    actor_id BIGINT,
    reason TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_course_transfers_source ON course_transfers(source_purchase_id);
CREATE INDEX idx_course_transfers_destination ON course_transfers(destination_purchase_id);

CREATE SEQUENCE audit_log_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE audit_logs (
    id BIGINT DEFAULT nextval('audit_log_id_seq') PRIMARY KEY,
    actor_id BIGINT,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
