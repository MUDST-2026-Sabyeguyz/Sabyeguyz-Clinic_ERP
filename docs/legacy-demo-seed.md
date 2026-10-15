# Seed ข้อมูลตัวอย่างเดิม (develop) บน schema v2 ล่าสุด

ไฟล์: `backend/src/main/resources/db/seed_v2_legacy/V1100__legacy_demo_data.sql`

ข้อมูลตัวอย่างเดิมคือ `clinic_erp_demo.sql` บน `develop` (ผู้ป่วย 160 ราย นัดหมาย 60 รายการ ใบเสร็จ 41 ใบ ฯลฯ)
ถูกแปลงเข้า schema ล่าสุด (V1, V2, V3, V4, V1001, V1002, V1003 ซึ่งตรงกับ Data Dictionary_Sabyeguyz.xlsx 53 ชีต)
แล้วเติมช่องที่ระบบเดิมไม่มีข้อมูลด้วยค่าตัวอย่าง

## วิธีใช้
seed นี้ใช้แทน `db/seed_v2` (ทั้งสองชุดสร้างบัญชี `manager`/`therapist` เหมือนกัน จึงห้ามเปิดคู่กัน)

```
APP_V2_FLYWAY_EXTRA_LOCATIONS=,classpath:db/seed_v2_legacy
```

ใช้กับฐานที่เพิ่งสร้างใหม่เท่านั้น ไม่โหลดใน production บัญชีทดลอง: `manager / manager123`, `therapist / therapist123`, `therapist2 / therapist123`

## ข้อมูลเดิมที่นำมา
ผู้ป่วย นัดหมายและประวัติสถานะ ใบเสร็จ รายการขาย การชำระเงิน การคืนเงิน/ยกเลิก บัญชีผู้ใช้ พนักงาน (RP-T01, RP-T02) สาขา บริการ
ข้อมูลหลัก (กลุ่มลูกค้า ช่องทางรู้จัก ประกัน) และชุดค่าคอมมิชชันเดิม (scheme + 5 tier) แปลงด้วยสคริปต์
`backend/scripts/legacy-migration/migrate-legacy-to-v2.sql` ที่ปรับให้เข้ากับ schema ล่าสุด

## ช่องที่เติมใหม่
| ตาราง | ที่เติม |
|---|---|
| `patient` | เลขบัตรประชาชน 13 หลักที่ checksum ถูกต้อง (เดิมเป็น `demo-id-N`; ขึ้นต้น `19999` เพื่อให้รู้ว่าเป็นเลขสมมติ), ชื่อเล่น, สัญชาติ, หมู่เลือด, กลุ่มลูกค้า, ช่องทางรู้จัก, ประเภทประกัน |
| `branch` | ชื่อสาขาจริง (สาขาพระราม 9, สาขาแบริ่ง), ที่อยู่/เบอร์ตัวอย่าง; `google_calendar_id` ปล่อยว่างเพราะต้องผูกปฏิทินจริง |
| `app_user` | อีเมลเข้าสู่ระบบ (V4); เพิ่ม `therapist2` และพนักงาน `THP-002` ประจำสาขาแบริ่ง |
| `room` / `appointment` | ห้องรักษา 2 ห้องต่อสาขา, ห้องของแต่ละนัด, หมายเหตุนัดที่เคยอยู่แค่ใน `legacy.appointments.note`, ประเภทบริการที่ขอ |
| `patient_visit`, `sales_transaction` | Visit ที่ Completed มีเวลาออกและผู้รักษา; บรรทัดขายแบบครั้งเดียวผูก visit (walk-in 1 ชั่วโมง) และผู้รักษา ตามข้อบังคับของ schema ใหม่ |
| `refund_transaction` | ผู้อนุมัติ/เวลาอนุมัติของการคืนเงินที่เสร็จแล้ว |
| คอร์ส | บริการ `COURSE_10`, `COURSE_5`, กฎค่ามือผู้รักษาแทน 45 บาท และคอร์ส 2 ใบ |

คอร์ส `RE26090013` (10 ครั้ง 10,000 บาท ขายก.ย. โดย THP-001) ปิดยอดเดือน ก.ย. แล้ว (Locked 5%, pool 500, ครั้งละ 50) ใช้ไป 3 ครั้ง
ครั้งที่ 3 โดย RP-T01 แทน (ค่ามือ 45, owner ได้ 5) คอร์ส `RE26100001` (6 ครั้งรวมแถม 1 ขาย ต.ค. โดย RP-T01) ยัง Provisional และยังไม่ใช้
ตัวเลขรวมในตาราง `course`, `course_entitlement`, `course_ledger` และ `commission_allocation_ledger` ตรงกัน (ตรวจด้วย SQL แล้ว)

## ที่ตั้งใจไม่เติม
- ส่วนลดบิล/รายการ: ไม่แต่งส่วนลดเพราะเปลี่ยนยอดเงินจริงของข้อมูลเดิม
- Google Calendar (`external_calendar_event_id`, `calendar_*`): ต้องมาจากการ sync จริง
- `staff_monthly_payment`, `course_transfer`, `course_extension`, `course_usage_print*`, ใบคืนเงินคอร์ส: ยังไม่มีข้อมูลตัวอย่าง

## ข้อควรรู้
- `migrate-legacy-to-v2.sql` ในรีโปยังเป็นรูปแบบก่อน V1002: ใช้กับ schema ล่าสุดไม่ได้โดยตรง (ตาราง `treatment_fee_rule`, `course`,
  `course_visit_usage`, `commission_allocation_ledger`, `monthly_commission_closing` และเงื่อนไข visit/refund ของ schema ใหม่) ตอนสร้าง seed นี้
  ต้องปรับสำเนาชั่วคราวของสคริปต์ ข้อมูลเดิมไม่มีคอร์ส/ledger/กฎค่ามือ จึงข้ามส่วนนั้นได้ แต่ถ้าจะย้ายข้อมูลจริงที่มีคอร์ส ต้องปรับสคริปต์จริงก่อน
- dump เดิมบน `develop` เป็น UTF-16 และข้อความไทยถูกแปลงรหัสซ้ำ (mojibake) ต้องแก้ก่อนโหลด seed นี้จึงเป็นข้อความไทยที่ถูกต้อง
- ไฟล์ `clinic_erp_demo.sql` เดิมยังอยู่ที่รากของ `develop` ไม่ได้ลบ
