-- Google service account key pasted by a Manager in the UI. The private key is stored AES-GCM encrypted
-- (key derived from the server secret); only the e-mail is readable. At most one row.
CREATE TABLE IF NOT EXISTS calendar_service_account (
    id            SMALLINT     PRIMARY KEY CHECK (id = 1),
    client_email  VARCHAR(255) NOT NULL,
    encrypted_key TEXT         NOT NULL,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT
);
