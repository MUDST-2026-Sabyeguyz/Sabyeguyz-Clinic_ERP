ALTER TABLE receipt ADD COLUMN request_key VARCHAR(36);
ALTER TABLE receipt ADD COLUMN request_fingerprint VARCHAR(64);
CREATE UNIQUE INDEX uq_receipt_request_key ON receipt (request_key) WHERE request_key IS NOT NULL;
