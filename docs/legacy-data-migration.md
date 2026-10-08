# ย้ายข้อมูลเดิมเข้า schema ใหม่ (v2)

ข้อมูลที่ใช้งานอยู่ในระบบเดิม **เก็บไว้ทั้งหมด** และย้ายเข้า schema ใหม่ด้วยสคริปต์เดียว:
[`backend/scripts/legacy-migration/migrate-legacy-to-v2.sql`](../backend/scripts/legacy-migration/migrate-legacy-to-v2.sql)

สคริปต์ทำงานในธุรกรรมเดียว: ถ้าบางส่วนผิดพลาด ทุกอย่างจะถูกย้อนกลับและฐานข้อมูลเหลือเหมือนเดิม
ตารางเก่าไม่ถูกแก้หรือลบ (เก็บใน schema `legacy`) จึงเป็นข้อมูลสำรองครบถ้วนรวมถึงฟิลด์ที่ schema ใหม่ไม่มีคอลัมน์รองรับ

## ขั้นตอนบนระบบจริง

1. **สำรองฐานข้อมูล** (`pg_dump`) และหยุดแอปเดิม
2. ย้าย schema เดิมไปเป็น `legacy` และสร้าง schema ใหม่ที่ว่าง
   ```sql
   ALTER SCHEMA public RENAME TO legacy;
   CREATE SCHEMA public;
   ```
3. สร้างตารางใหม่ โดยเลือกอย่างใดอย่างหนึ่ง
   - เปิดแอป v2 หนึ่งครั้งโดยไม่ตั้ง `APP_V2_FLYWAY_EXTRA_LOCATIONS` (Flyway สร้าง schema ใหม่และข้อมูลอ้างอิง) แล้วปิดแอป หรือ
   - รัน `V1__initial_schema.sql` และ `V1_1__reference_data.sql` ใน `backend/src/main/resources/db/migration_new` ด้วย `psql`
     แล้วเปิดแอปด้วย `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` และ `SPRING_FLYWAY_BASELINE_VERSION=1.1`
4. รันสคริปต์ย้ายข้อมูล
   ```bash
   psql -v ON_ERROR_STOP=1 -f backend/scripts/legacy-migration/migrate-legacy-to-v2.sql
   ```
   สคริปต์ตรวจจำนวนแถวและยอดเงินเทียบกับของเดิมก่อน commit (ผู้ป่วย นัดหมาย ใบเสร็จ ยอดขาย ยอดชำระ คอร์ส ยอดสิทธิ์คงเหลือ ผู้ใช้)
5. อ่านรายการที่ต้องตรวจ: `SELECT * FROM legacy.migration_issue ORDER BY id;`
6. เปิดแอป v2 และใช้งาน (ผู้ใช้เดิมล็อกอินด้วยรหัสผ่านเดิมได้ เพราะเป็น BCrypt แบบเดียวกัน)

ลองซ้ำได้: ถ้าสคริปต์ล้มหรือต้องเริ่มใหม่ ให้ลบ schema `public` ใหม่แล้วสร้างใหม่ตามข้อ 2-3 (ตารางใน `legacy` ไม่ถูกแตะ)

## CI

Workflow **Legacy data migration** สร้างฐานข้อมูลแบบเดิมจากไฟล์ Flyway เก่า (พร้อมข้อมูลตัวอย่าง V20-V25 และข้อมูลเพิ่ม
ใน `ci/legacy-fixture.sql` ครอบคลุมคอร์ส ledger การโอน การยกเลิก การคืนเงิน และค่าคอมมิชชัน) รันสคริปต์ย้ายข้อมูล แล้วเปิดแอป v2
และตรวจผ่าน API (`ci/verify-migrated-app.sh`): ล็อกอินด้วยรหัสผ่านเดิม ข้อมูลผู้ป่วย/นัดหมาย/ledger ครบ
และเมื่อลงทะเบียนผู้ป่วย ขาย หรือนัดหมายใหม่ เลข HN และเลขใบเสร็จต่อจากของเดิมได้

## การจับคู่ข้อมูล (เดิม → ใหม่)

| ข้อมูลเดิม | ข้อมูลใหม่ | หมายเหตุ |
|---|---|---|
| `branch` | `branch` | id เดิมคงไว้ |
| `services` | `service` (SingleVisit) | ระยะเวลา (นาที) ไม่มีในตารางใหม่ เก็บใน `legacy.services` |
| `courses` (แม่แบบคอร์ส) | `service` (Course) | จำนวนครั้ง/โบนัส/อายุเป็นค่าเริ่มต้น; รหัสยาวเกิน 20 ตัวอักษรใช้ `CRS-<id>` |
| `payment_methods` | `payment_method` | จับคู่ตามชื่อ (Cash/Transfer/QR/Credit Card) ที่เหลือเพิ่มใหม่ |
| `app_users` | `app_user` + `user_role` + `user_branch_access` | รหัสผ่านเดิม; MANAGER เข้าได้ทุกสาขา คนอื่นเข้าสาขาของตน; อีเมลไม่มีคอลัมน์ใหม่ (อยู่ใน `legacy`) |
| `staff` และ `therapists` | `staff` | ชื่อเต็มแยกเป็นชื่อ/นามสกุลที่เว้นวรรคแรก; therapist ที่ไม่มีบัญชีเป็นพนักงานที่ไม่ผูกผู้ใช้ |
| `patients` | `patient` | id เดิมคงไว้; ดูรายละเอียดด้านล่าง |
| `hn_counters` | `hn_sequence` | รวมกับเลข HN สูงสุดที่ใช้แล้ว |
| `appointments` | `appointment` + `appointment_status_history` + `patient_visit` | สถานะแปลงชื่อ; เวลาเป็นเวลาไทย; เหตุผลยกเลิกเข้าประวัติสถานะ; นัดที่มาถึงแล้วมี visit |
| `treatments` | `treatment` + `patient_visit` | การรักษาที่ไม่มีนัดสร้าง visit แบบ walk-in |
| `sales`, `sale_items` | `receipt`, `sales_transaction` | id ใบเสร็จ = id ใบขายเดิม; เลขใบเสร็จใหม่ `RE-xxxxxxx` เรียงตามเวลา (เลขเดิมอยู่ใน `legacy.map_receipt`) |
| `sale_payments` (PAYMENT) | `payment_transaction` | |
| `sale_payments` (REFUND) | `transaction_cancellation` + `refund_transaction` (Completed) | |
| ใบขายที่ VOIDED | `receipt` Voided + `transaction_cancellation` (Void) ต่อรายการ | |
| `course_purchases` | `course` + `course_shared_member` (Owner) + `course_sale_staff_share` (100%) | id เดิมคงไว้; เลขคอร์ส = เลขใบเสร็จ; ใบซื้อที่ไม่มีใบขายจะสร้างใบเสร็จให้ |
| `course_movements` | `course_ledger` + `course_visit_usage` + `course_entitlement` | ดูรายละเอียดด้านล่าง |
| `commission_*` | `commission_scheme`, `commission_tier`, `treatment_fee_rule`, `monthly_commission_closing`, `commission_allocation_ledger` | id เดิมคงไว้ |
| `clinic_resources` | `room` | |
| `master_data`, ข้อความอิสระในผู้ป่วย | `master_data_type` / `master_data_value` | กลุ่มลูกค้า ช่องทางรู้จัก ประกัน |
| `audit_logs` | `audit_log` | ชื่อ action เดิมอยู่ใน `new_value` |

### ผู้ป่วย
- **HN:** ถ้าเป็นเลข 10 หลักรูปแบบ `ปี สาขา เดือน ลำดับ` (YYBBMMNNNN) ใช้ค่าเดิม; ไม่ตรงรูปแบบ (เช่นข้อมูลตัวอย่าง) จะออก HN ใหม่ตามเดือนที่ลงทะเบียน
  ต่อจากเลขสูงสุดของสาขานั้น และบันทึกใน `migration_issue`
- **ไทย/ต่างชาติ:** ประเภทเอกสาร `NATIONAL_ID` = ไทย ส่วนอื่น = ต่างชาติ (passport เก็บใน `passport_no`)
- **เลขบัตรประชาชน:** ต้องเป็น 13 หลัก ถ้าไม่ใช่จะเว้นว่างและบันทึกใน `migration_issue` (เลขเดิมอยู่ใน `legacy.patients`)
- **คำนำหน้า:** นาย/นาง/นางสาว/ด.ช./ด.ญ. ใช้ตามเดิม; ค่าอื่นเลือกตามเพศและบันทึกใน `migration_issue`
- **หมู่เลือด:** รับ A/B/O/AB (ตัด +/- ออก)
- ฟิลด์ที่ schema ใหม่ไม่มี (ผู้ติดต่อฉุกเฉิน แพ้ยา/อาหาร โรคประจำตัว ความยินยอมข้อมูลส่วนบุคคล บัญชีผู้ป่วย) **อยู่ใน `legacy.patients` เท่านั้น**
  ถ้าต้องการใช้ต่อ ต้องเพิ่มคอลัมน์ใน schema ใหม่ก่อน (ตัดสินใจกับทีม database)

### คอร์สและ ledger
- ซื้อ → `Purchase`; ใช้สิทธิ์ → `Treatment` (+ `course_visit_usage` + visit); กลับรายการ → `Reversal` ชี้ไปที่รายการเดิม
- การโอนสิทธิ์ระหว่างใบซื้อ (โครงสร้างเดิมเป็นคนละใบซื้อ) และการยกเลิกคอร์สทั้งใบ **เก็บเป็นรายการ `Adjustment` พร้อมหมายเหตุ**
  เพราะ schema ใหม่โอนได้เฉพาะภายในคอร์สเดียวกัน ยอดคงเหลือจึงตรงกับของเดิม แต่ไม่ปรากฏในหน้าประวัติการโอนใหม่
- ระบบเดิมไม่ได้กลับรายการคอมมิชชันเมื่อกลับรายการการใช้สิทธิ์: สคริปต์เพิ่มแถว `Reversal` ติดลบให้ เพื่อให้ยอดรวมของคอร์สถูกต้องตามกติกาใหม่
- แถวจัดสรรคอมมิชชันเดิมที่ไม่ได้ระบุว่าเป็นของการใช้ครั้งไหน จับคู่ตามลำดับภายในใบซื้อเดียวกัน

## จุดที่ต้องรู้
- ผู้สร้างรายการที่ไม่มีข้อมูลในระบบเดิม (เช่น ผู้ลงทะเบียนผู้ป่วย) ใช้ผู้ใช้ `system`
- เวลาในระบบเดิมเป็นเวลาที่มีเขตเวลา: ย้ายเข้า schema ใหม่เป็น UTC (วัน/เวลานัดหมายเป็นเวลาไทย)
- นัดหมายเดิมไม่มีข้อมูลห้อง/หมายเหตุ/เวลาสิ้นสุด ใน schema ใหม่ (อยู่ใน `legacy.appointments`)
- สคริปต์ใช้ได้กับฐานข้อมูลเปล่าของ schema ใหม่เท่านั้น (ตรวจก่อนทำงาน)
