-- Course Purchase + Ledger ตาม Clinic ERP Complete Workflow Brief V1 §5
-- Course Balance ต้องเป็นบัญชีเดินสะพัดของ movement ไม่ใช่ตัวเลข remaining ที่แก้ทับได้

CREATE SEQUENCE course_purchase_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE course_purchases (
    id BIGINT DEFAULT nextval('course_purchase_id_seq') PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patients(id),
    course_id BIGINT NOT NULL REFERENCES courses(id),
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    -- snapshot ของ course master ณ วันที่ขาย เพื่อให้แก้ราคา/จำนวนครั้งใน master ภายหลังไม่กระทบใบที่ขายไปแล้ว
    course_code VARCHAR(40) NOT NULL,
    course_name VARCHAR(160) NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    sessions INTEGER NOT NULL,
    bonus_sessions INTEGER NOT NULL DEFAULT 0,
    purchased_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_course_purchases_sessions CHECK (sessions > 0),
    CONSTRAINT chk_course_purchases_bonus CHECK (bonus_sessions >= 0),
    CONSTRAINT chk_course_purchases_price CHECK (price >= 0),
    CONSTRAINT chk_course_purchases_period CHECK (expires_at > purchased_at)
);

CREATE INDEX idx_course_purchases_patient ON course_purchases(patient_id);

CREATE SEQUENCE course_movement_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE course_movements (
    id BIGINT DEFAULT nextval('course_movement_id_seq') PRIMARY KEY,
    course_purchase_id BIGINT NOT NULL REFERENCES course_purchases(id),
    type VARCHAR(20) NOT NULL,
    -- ค่าบวกคือเพิ่มสิทธิ์ ค่าลบคือตัดสิทธิ์ remaining คือผลรวมของคอลัมน์นี้
    quantity INTEGER NOT NULL,
    reason TEXT,
    appointment_id BIGINT REFERENCES appointments(id),
    reverses_movement_id BIGINT REFERENCES course_movements(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_course_movements_quantity CHECK (quantity <> 0),
    -- movement หนึ่งรายการถูกกลับรายการได้ครั้งเดียวเท่านั้น
    CONSTRAINT uq_course_movements_reverses UNIQUE (reverses_movement_id)
);

CREATE INDEX idx_course_movements_purchase ON course_movements(course_purchase_id);
