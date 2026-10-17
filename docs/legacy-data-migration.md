# ย้ายข้อมูลเดิมเข้า schema ใหม่ (v2)

ข้อมูลเดิมเก็บใน schema `legacy`; เฉพาะ field และ behavior ที่ mapping รองรับจะใช้งานผ่าน v2 ได้ ย้ายด้วยสคริปต์:
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
   - หากสร้างด้วย SQL โดยตรง ต้องรัน **ทุก migration ปัจจุบันตาม numeric Flyway order** (ขณะตรวจมี V1/V2/V3/V1001) แล้ว baseline เป็น version สุดท้ายที่ apply ครบจริง ไม่ใช้เลข 3 คงที่. วิธีเปิดผ่าน Flyway ในข้อแรกช่วยบันทึก checksums จริงและลดความคลาดเคลื่อน
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
| `sales`, `sale_items` | `receipt`, `sales_transaction` | id ใบเสร็จ = id ใบขายเดิม; เลขใหม่ `REYYMMNNNN` ต่อสาขา/เดือน (เลขเดิมอยู่ใน `legacy.map_receipt`) |
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
- ตรวจ field นัดหมายเทียบ SQL ปัจจุบันเป็นราย field; อย่าสรุปว่าห้อง/หมายเหตุ/เวลาสิ้นสุดใช้งานใน v2 ครบเพียงเพราะต้นฉบับอยู่ใน `legacy.appointments`
- สคริปต์ใช้ได้กับฐานข้อมูลเปล่าของ schema ใหม่เท่านั้น (ตรวจก่อนทำงาน)

## เงื่อนไขจาก schema ล่าสุด

ผู้ป่วยไทยต้องมีเบอร์โทรศัพท์และเลขบัตรประชาชนจริง 13 หลักก่อนย้ายข้อมูล หากข้อมูลเดิมไม่ครบ importer จะหยุดทั้ง transaction เพื่อให้แก้ข้อมูลต้นทาง ไม่สร้างเลขบัตรแทนผู้ป่วยจริง ส่วน legacy-fixture.sql เติมเลขรูปแบบทดสอบเฉพาะข้อมูล demo ใน CI เท่านั้น

ใบเสร็จใช้ REYYMMNNNN แยก counter ตามสาขาและเดือน และเก็บการชำระหลายช่องทางต่อใบเสร็จ คอร์สเก็บจำนวนรวมครั้งซื้อและ bonus พร้อม entitlement ที่ตรงกับยอด ledger หลังย้าย

## P9 rehearsal และขอบเขตผลตรวจ (15 ตุลาคม 2026)

จาก `backend` รัน `node --test scripts/legacy-migration/ci/rehearse.test.cjs` แล้ว `node scripts/legacy-migration/ci/rehearse.cjs --container <ชื่อ PostgreSQL container ทดสอบ>` หรือไม่ใส่ `--container` เมื่อมี psql/pg_dump และ PGHOST/PGUSER/PGPASSWORD. ต้องใช้บัญชีที่สร้าง disposable databases ได้; ใช้เฉพาะเครื่อง/ฐานทดสอบ. โปรแกรมสร้างชื่อฐานใหม่เอง ไม่ drop/overwrite ฐานที่มีอยู่ และเก็บฐานไว้ให้ตรวจ.

เครื่องมือสร้าง legacy demo+fixture snapshot เดียว แล้ว restore ลง target ใหม่สองฐาน, apply migrations ทุกไฟล์ใน active folder แบบ numeric order, import, reconcile และ compare stable manifests. ตรวจ counts, net ตาม mapping เดิม, payments/refunds, expiry, void, unique IDs, ledger/entitlement และ FK ทุกตาราง. Fault injection เปลี่ยน expiry, receipt total และ FK orphan ต้องถูกจับแล้ว rollback; importer ต้องปฏิเสธ populated target โดยข้อมูลเดิมไม่เปลี่ยน.

ผลอยู่ `backend/target/migration-rehearsal/*.json`; CI upload ก่อน Maven clean package. นี่คือ fixture rehearsal ไม่ใช่ dry run บนสำเนาฐานจริงที่ได้รับอนุญาต และไม่ได้ทดสอบ backup/restore ของ production infrastructure.

**ข้อแตกต่างทางการเงินที่ต้อง review:** importer เดิมใช้ผลรวม `sale_items.line_total` แทน `sales.total` เมื่อมีรายการ. Demo snapshot มี header รวม 45,800 แต่ mapped receipt net 47,300 (ต่าง 15 ใบ). Manifest เก็บ `sourceReceiptHeaderTotal` และ `receiptHeaderDifferences` ทุกใบ จึงไม่อ้างว่ายอดเดิมตรงกันหรืออนุมัติการแปลงนี้แล้ว. Refund เดิมเป็น negative amount ส่วน v2 เก็บ positive refund_amount จึงเทียบ absolute amount แยกจาก payment receipts.

`MIGRATION_REHEARSAL_OK` ยืนยัน technical mapping/repeatability; summary ตั้ง `releaseReady=false`. Q3/Q4 numbering, Q7 expiry, Q10 legacy field mapping และ accounting differences ยังต้องอนุมัติก่อน final P9/release.
