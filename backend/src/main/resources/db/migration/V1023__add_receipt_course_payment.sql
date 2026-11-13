-- Subsequent collections for an existing course are receipt items, not new sales.
-- The original purchase remains linked through course.receipt_id.
CREATE TABLE receipt_course_payment (
    receipt_id                     BIGINT        NOT NULL,
    course_id                      BIGINT        NOT NULL,
    item_name_snapshot             VARCHAR(255)  NOT NULL,
    amount                         DECIMAL(12, 2) NOT NULL,     -- >=0
    allocated_bill_discount_amount DECIMAL(12, 2) NOT NULL,     -- 0 <= discount <= amount

    CONSTRAINT pk_receipt_course_payment
        PRIMARY KEY (receipt_id, course_id),

    CONSTRAINT chk_receipt_course_payment_amount
        CHECK (amount >= 0),

    CONSTRAINT chk_receipt_course_payment_discount
        CHECK (allocated_bill_discount_amount >= 0
            AND allocated_bill_discount_amount <= amount),

    CONSTRAINT fk_receipt_course_payment_receipt
        FOREIGN KEY (receipt_id) REFERENCES receipt (receipt_id),

    CONSTRAINT fk_receipt_course_payment_course
        FOREIGN KEY (course_id) REFERENCES course (course_id)
);

-- Course payment history is queried by course_id across multiple receipts.
CREATE INDEX idx_receipt_course_payment_course
    ON receipt_course_payment (course_id);

-- Buyer matching, cross-table receipt totals, and exclusion of the original
-- purchase receipt must be validated by the backend in the same transaction.
