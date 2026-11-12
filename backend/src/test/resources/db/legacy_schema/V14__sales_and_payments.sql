-- ใบขาย/ใบเสร็จ และการรับชำระเงิน
-- หมายเหตุเรื่องเลขเวอร์ชัน: V13 ถูกจองไว้โดย branch feature/complete-v1-workflows (Course Transfer + Audit)
-- ที่ยังไม่ merge จึงข้ามมาใช้ V14 เพื่อไม่ให้ชนกัน

-- ราคาต่อครั้งของบริการ ใช้กับรายการขายแบบ Assessment และ Single Visit
ALTER TABLE services ADD COLUMN price NUMERIC(12,2) NOT NULL DEFAULT 0;
UPDATE services SET price = 1200.00 WHERE code = 'ASSESSMENT';
UPDATE services SET price = 700.00 WHERE code = 'PHYSIO_30';
UPDATE services SET price = 1200.00 WHERE code = 'PHYSIO_60';

-- เลขที่ใบเสร็จเดินตามสาขาและเดือน แบบเดียวกับ HN
CREATE TABLE receipt_counters (
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    receipt_year INTEGER NOT NULL,
    receipt_month INTEGER NOT NULL CHECK (receipt_month BETWEEN 1 AND 12),
    last_number INTEGER NOT NULL CHECK (last_number BETWEEN 0 AND 9999),
    PRIMARY KEY (branch_id, receipt_year, receipt_month)
);

CREATE SEQUENCE sale_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE sales (
    id BIGINT DEFAULT nextval('sale_id_seq') PRIMARY KEY,
    receipt_no VARCHAR(16) NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL REFERENCES patients(id),
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    -- ผู้ขายแยกจากผู้รักษาเสมอ ผู้รักษาถูกบันทึกไว้ที่ treatments ไม่ใช่ที่นี่
    salesperson_staff_id BIGINT REFERENCES staff(id),
    total NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    status VARCHAR(20) NOT NULL,
    note TEXT,
    sold_at TIMESTAMP WITH TIME ZONE NOT NULL,
    voided_at TIMESTAMP WITH TIME ZONE,
    void_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- ยกเลิกแล้วต้องมีคนระบุเหตุผลและเวลาเสมอ ไม่มีการยกเลิกแบบไร้ที่มา
    CONSTRAINT chk_sales_voided CHECK (
        status <> 'VOIDED' OR (voided_at IS NOT NULL AND void_reason IS NOT NULL))
);
CREATE INDEX idx_sales_patient ON sales(patient_id, sold_at);
CREATE INDEX idx_sales_branch_sold ON sales(branch_id, sold_at);

CREATE SEQUENCE sale_item_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE sale_items (
    id BIGINT DEFAULT nextval('sale_item_id_seq') PRIMARY KEY,
    sale_id BIGINT NOT NULL REFERENCES sales(id),
    item_type VARCHAR(20) NOT NULL,
    service_id BIGINT REFERENCES services(id),
    course_id BIGINT REFERENCES courses(id),
    -- ใบซื้อคอร์สที่รายการขายบรรทัดนี้สร้างขึ้น ใช้ตามรอยตอนยกเลิกใบเสร็จ
    course_purchase_id BIGINT REFERENCES course_purchases(id),
    description VARCHAR(160) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    line_total NUMERIC(12,2) NOT NULL CHECK (line_total >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sale_items_reference CHECK (
        (item_type = 'COURSE' AND course_id IS NOT NULL AND service_id IS NULL)
        OR (item_type IN ('ASSESSMENT', 'SINGLE_VISIT') AND service_id IS NOT NULL AND course_id IS NULL))
);
CREATE INDEX idx_sale_items_sale ON sale_items(sale_id);

-- การรับเงินและการคืนเงินเป็นบัญชีเดินสะพัดเหมือน course_movements
-- รายได้ของใบเสร็จ = ผลรวมของ amount ไม่มีการลบหรือแก้แถวเดิม
CREATE SEQUENCE sale_payment_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE sale_payments (
    id BIGINT DEFAULT nextval('sale_payment_id_seq') PRIMARY KEY,
    sale_id BIGINT NOT NULL REFERENCES sales(id),
    payment_method_id BIGINT NOT NULL REFERENCES payment_methods(id),
    payment_method_code VARCHAR(40) NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount <> 0),
    reason TEXT,
    reverses_payment_id BIGINT REFERENCES sale_payments(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- คืนเงินซ้ำสองครั้งสำหรับการรับเงินรายการเดียวกันไม่ได้
    CONSTRAINT uq_sale_payments_reverses UNIQUE (reverses_payment_id)
);
CREATE INDEX idx_sale_payments_sale ON sale_payments(sale_id);
