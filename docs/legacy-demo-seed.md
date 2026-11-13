# Seed ข้อมูลตัวอย่างเดิม (develop) บน schema v2 ล่าสุด

การโหลด seed ต้องเลือกโหมดชัดเจนบนฐานข้อมูล local/demo ที่แยกไว้เท่านั้น: กำหนด `CLINIC_DATABASE_KIND=local-demo` และเลือก `CLINIC_SEED_MODE=full` (หรือ `minimal`) ก่อนเริ่มระบบ. ค่าเริ่มต้น/ค่าที่ไม่ระบุคือ `none`; profile `prod` และ `production` ไม่อนุญาต demo seed. ห้ามใช้ seed mode กับฐานข้อมูลจริง. การเลือก `none` ไม่ลบข้อมูลตัวอย่างที่เคยโหลดแล้ว และฐานที่มี seed history ต้องเริ่มด้วย mode เดิม.

ไฟล์: `backend/src/main/resources/db/seed_v2_legacy/V1100__legacy_demo_data.sql`

ข้อมูลตัวอย่างเดิมคือ `clinic_erp_demo.sql` บน `develop` (ผู้ป่วย 160 ราย นัดหมาย 60 รายการ ใบเสร็จ 41 ใบ ฯลฯ)
ถูกแปลงเข้า schema ล่าสุด (V1–V4 และ V1001–V1007 เทียบกับ Data Dictionary_Sabyeguyz_afterFB3.4.xlsx 49 ชีต)
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
| คอลัมน์ใหม่ของ dictionary afterFB3.4 | `appointment.appointment_end_at` (เวลาสิ้นสุดเดิม, NOT NULL), `app_user.email` ของ system (NOT NULL), `branch` เวลาเปิด-ปิด/ช่วงจองเริ่มต้น/อีเมล/เลขใบอนุญาต/สถานะปฏิทิน (`Disabled`), `room.capacity`, `service.default_duration_minutes` (ระยะเวลาเดิมของบริการ), `patient.email`, `receipt.note` (หมายเหตุใบขายเดิม), `treatment_fee_rule.rule_name`, `appointment.course_id` (นัดของเจ้าของคอร์ส RE26090013 1 รายการ) |
| โอนสิทธิ์/ต่ออายุ/ใบตัดคอร์ส | `course_transfer` (โอน 2 ครั้งให้ผู้ป่วย 3 พร้อม ledger ทั้งสองฝั่ง), `course_extension` (+1 เดือน), `course_usage_print` + `course_usage_print_event` (Issued/Reprint) |
| ค่าตอบแทนรายเดือน | `single_visit_fee_ledger` (ค่ามือ 45 ต่อครั้งเดี่ยวที่ชำระแล้ว), `staff_monthly_payment` (ต.ค. สถานะ Open เชื่อมกับ allocation), `staff_pay_adjustment` + `adjustment_application` (ปรับ +100 รอจ่าย ต.ค.) |
| อื่นๆ | `user_permission` (therapist2 ถูกปฏิเสธ COURSE_REVERSE), `audit_log` 3 รายการ |

คอร์ส `RE26090013` (10 ครั้ง 10,000 บาท ขายก.ย. โดย THP-001) ปิดยอดเดือน ก.ย. แล้ว (Locked 5%, pool 500, ครั้งละ 50) ใช้ไป 3 ครั้ง
ครั้งที่ 3 โดย RP-T01 แทน (ค่ามือ 45, owner ได้ 5) คอร์ส `RE26100001` (6 ครั้งรวมแถม 1 ขาย ต.ค. โดย RP-T01) ยัง Provisional และยังไม่ใช้
ตัวเลขรวมในตาราง `course`, `course_entitlement`, `course_ledger` และ `commission_allocation_ledger` ตรงกัน (ตรวจด้วย SQL แล้ว)

## ที่ตั้งใจไม่เติม
- ส่วนลดบิล/รายการ: ไม่แต่งส่วนลดเพราะเปลี่ยนยอดเงินจริงของข้อมูลเดิม
- Google Calendar (`external_calendar_event_id`, `calendar_*`): ต้องมาจากการ sync จริง
- `course_refund_item`, `commission_rate_override_audit`, `system_setting`: ยังไม่มีข้อมูลตัวอย่าง (dictionary ไม่กำหนดคีย์ตั้งค่า และการคืนเงินคอร์ส/override ต้องมีเหตุการณ์จริง)
- `course_sale_staff_share`, `service_group*`, `staff_group*`: Data Dictionary ระบุว่าเลิกใช้ (ตารางยังอยู่ใน V1002) seed นี้จึงไม่ใส่ข้อมูล

## ข้อควรรู้
- `migrate-legacy-to-v2.sql` ในรีโปยังเป็นรูปแบบก่อน V1002: ใช้กับ schema ล่าสุดไม่ได้โดยตรง (ตาราง `treatment_fee_rule`, `course`,
  `course_visit_usage`, `commission_allocation_ledger`, `monthly_commission_closing` และเงื่อนไข visit/refund ของ schema ใหม่) ตอนสร้าง seed นี้
  ต้องปรับสำเนาชั่วคราวของสคริปต์ ข้อมูลเดิมไม่มีคอร์ส/ledger/กฎค่ามือ จึงข้ามส่วนนั้นได้ แต่ถ้าจะย้ายข้อมูลจริงที่มีคอร์ส ต้องปรับสคริปต์จริงก่อน
- dump เดิมบน `develop` เป็น UTF-16 และข้อความไทยถูกแปลงรหัสซ้ำ (mojibake) ต้องแก้ก่อนโหลด seed นี้จึงเป็นข้อความไทยที่ถูกต้อง
- ไฟล์ `clinic_erp_demo.sql` เดิมยังอยู่ที่รากของ `develop` ไม่ได้ลบ
- ตาราง `google_calendar_connection`, `calendar_busy_block` และ `branch.google_connection_id`/`google_calendar_id` ว่างโดยตั้งใจ เพราะต้องมาจากการเชื่อม Google จริง
- คอลัมน์ที่มีใน migration แต่ไม่อยู่ใน dictionary (เช่น `branch.booking_open_time`/`booking_close_time`/`branch_email` และ `service.default_appointment_minutes` จาก V1004 ที่ V1007 คัดลอกไปคอลัมน์ชื่อตาม dictionary แล้ว, `staff_monthly_payment.paid_date`/`payment_reference`, `monthly_commission_closing.reopen_*`, ตาราง `course_extension_request`) ไม่ได้ใส่ข้อมูล
