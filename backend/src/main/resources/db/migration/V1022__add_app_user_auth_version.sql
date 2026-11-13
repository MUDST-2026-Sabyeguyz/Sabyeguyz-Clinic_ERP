-- ใช้ version นี้ปฏิเสธ token ที่ออกก่อนเปลี่ยนข้อมูลบัญชี
ALTER TABLE app_user
    ADD COLUMN auth_version INTEGER NOT NULL DEFAULT 0;
