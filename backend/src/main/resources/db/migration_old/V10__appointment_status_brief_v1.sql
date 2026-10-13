-- ปรับสถานะนัดหมายให้ตรงกับ Clinic ERP Complete Workflow Brief V1 หัวข้อ 4
-- SCHEDULED -> CONFIRMED, CHECKED_IN -> ARRIVED
-- สถานะที่เพิ่มใหม่ (IN_SERVICE, RESCHEDULED, NO_SHOW) ยังไม่มีข้อมูลเดิมให้ย้าย
UPDATE appointments SET status = 'CONFIRMED' WHERE status = 'SCHEDULED';
UPDATE appointments SET status = 'ARRIVED' WHERE status = 'CHECKED_IN';
