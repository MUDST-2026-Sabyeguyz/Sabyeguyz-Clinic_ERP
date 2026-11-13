CREATE TABLE payment_status_option (
    status_code VARCHAR(20) PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO payment_status_option (status_code, display_name, enabled) VALUES
    ('INSTALLMENT', 'ผ่อนชำระ', TRUE),
    ('DEPOSIT', 'มัดจำ', TRUE),
    ('PAID', 'ชำระเต็มจำนวน', TRUE);
