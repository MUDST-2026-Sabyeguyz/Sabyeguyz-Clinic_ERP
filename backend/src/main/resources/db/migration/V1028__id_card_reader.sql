-- Counter workstations with a card reader, and the audit of reads (who, when, where, why; never the card's data).
CREATE TABLE card_reader_workstation (
    workstation_id      BIGSERIAL PRIMARY KEY,
    workstation_name    VARCHAR(100) NOT NULL,
    branch_id           BIGINT NOT NULL REFERENCES branch (branch_id),
    token_hash          VARCHAR(64),
    pairing_code_hash   VARCHAR(64),
    pairing_expires_at  TIMESTAMPTZ,
    workstation_status  VARCHAR(10) NOT NULL DEFAULT 'Active',
    last_seen_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          BIGINT REFERENCES app_user (user_id),
    CONSTRAINT chk_card_reader_workstation_status CHECK (workstation_status IN ('Active', 'Revoked')),
    CONSTRAINT uq_card_reader_workstation_token UNIQUE (token_hash),
    CONSTRAINT uq_card_reader_workstation_pairing UNIQUE (pairing_code_hash)
);
CREATE INDEX idx_card_reader_workstation_branch ON card_reader_workstation (branch_id);

CREATE TABLE id_card_read_audit (
    audit_id        BIGSERIAL PRIMARY KEY,
    workstation_id  BIGINT NOT NULL REFERENCES card_reader_workstation (workstation_id),
    user_id         BIGINT NOT NULL REFERENCES app_user (user_id),
    branch_id       BIGINT NOT NULL REFERENCES branch (branch_id),
    purpose         VARCHAR(10) NOT NULL,
    outcome         VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_id_card_read_purpose CHECK (purpose IN ('REGISTER')),
    CONSTRAINT chk_id_card_read_outcome CHECK (outcome IN
        ('SUCCESS', 'NO_CARD', 'NO_READER', 'ERROR', 'INVALID_CARD', 'TIMEOUT'))
);
CREATE INDEX idx_id_card_read_audit_workstation ON id_card_read_audit (workstation_id, created_at);
