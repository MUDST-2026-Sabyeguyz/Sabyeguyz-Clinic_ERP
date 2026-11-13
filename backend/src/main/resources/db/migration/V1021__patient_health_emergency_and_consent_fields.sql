-- เพิ่มข้อมูลสุขภาพ ผู้ติดต่อฉุกเฉิน และความยินยอมในโปรไฟล์ผู้ป่วย
ALTER TABLE patient
    ADD COLUMN drug_allergy BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN food_allergy BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN chronic_disease VARCHAR(500),
    ADD COLUMN emergency_contact_name VARCHAR(160),
    ADD COLUMN emergency_contact_relationship VARCHAR(80),
    ADD COLUMN emergency_contact_phone VARCHAR(20),
    ADD COLUMN privacy_consent BOOLEAN NOT NULL DEFAULT FALSE;
