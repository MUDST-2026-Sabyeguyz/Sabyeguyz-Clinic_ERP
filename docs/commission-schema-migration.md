# Migration V1002: สคีมาค่าคอมมิชชัน (T1)

นำ `V3__implement_commission_requirements.sql` ของ `feat/database` (คอมมิต `28fdb46`) เข้า v2 เป็น
`backend/src/main/resources/db/migration/V1002__implement_commission_requirements.sql` ตามกติกาใน Data Dictionary ล่าสุด
ผ่านการทดสอบกับ Flyway จริง ทั้งฐานใหม่และการอัปเกรดฐานที่มีข้อมูล

## ทำไมเลข V1002 ไม่ใช่ V3
- v2 มี `V3__reference_data.sql` อยู่แล้ว (เลขชน)
- ฐานข้อมูลที่ใช้อยู่ได้ apply `V1001` ไปแล้ว ถ้าเพิ่มเลขต่ำกว่า (เช่น V4) Flyway จะปฏิเสธ (migration ที่ยังไม่ apply แต่เลขต่ำกว่าที่ apply แล้ว)

## สิ่งที่ปรับจาก V3 ต้นฉบับ
V3 ต้นฉบับ **ล้มบนฐานที่มีข้อมูล** (ทดสอบแล้ว: `chk_patient_visit_treating_staff` ถูกละเมิดทันที) และรันผ่านเฉพาะฐานว่าง ที่ปรับมี 4 อย่าง

1. **Backfill ก่อนสั่ง NOT NULL** (ค่าที่คำนวณได้จากข้อมูลจริงเท่านั้น)
   - `patient_visit.treating_staff_id` (Visit ที่ InService/Completed): จากผู้รักษาในการใช้สิทธิ์คอร์ส -> จาก `treatment` -> ผู้ที่ได้รับมอบหมาย
   - `course.treatment_fee_base_amount`: ยอดของรายการขาย (`amount - allocated_bill_discount_amount`) ถ้าไม่มีใช้ `course_price`
   - `course_visit_usage.usage_sequence`: ลำดับต่อคอร์สตามลำดับการใช้ (ครั้งที่ย้อนกลับใช้ตำแหน่งของครั้งที่มาแทน หรือตำแหน่งถัดไป)
   - `commission_allocation_ledger.allocation_version`: เรียงตามลำดับ Allocation ต่อ usage ; Reversal ใช้เวอร์ชันเดียวกับรายการที่กลับ
2. **constraint ที่แถวเก่าอาจไม่ผ่าน เพิ่มแบบ `NOT VALID`** (39 ตัว: CHECK และ FOREIGN KEY บนตารางเดิม) ใช้บังคับกับแถวใหม่/ที่แก้ทันที แล้วท้ายไฟล์พยายาม `VALIDATE` ถ้าแถวเก่าไม่ผ่านจะพิมพ์ `NOTICE` ระบุชื่อ constraint และคงไว้เป็น NOT VALID (ไม่แต่งข้อมูลย้อนหลังขึ้นเอง) เมื่อแก้แถวเก่าแล้วสั่ง `ALTER TABLE ... VALIDATE CONSTRAINT ...` เอง
3. **เก็บสำเนาข้อมูลเดิมที่ V3 จะทิ้ง** ในตาราง `legacy_v1002_*`: `course_owner` (ผู้ขายเทียบ case owner เดิม), `usage_owner`, `fee_rule_scope` (การผูกกฎค่ามือกับพนักงาน/บริการ/กลุ่ม), `staff_termination_policy`
   - ระวัง: เมื่อ `case_owner_staff_id` ต่างจาก `seller_staff_id` (ผลที่เห็นในข้อมูลทดสอบ 2 จาก 5 คอร์ส) หลัง migration Owner = ผู้ขาย ตาม Data Dictionary ผู้รับ commission อาจเปลี่ยน ต้องให้ทีมตรวจรายการในตารางนี้
4. **ตัวป้องกัน:** ถ้ามี `commission_allocation_ledger` ชนิด `Adjustment` เก่า migration จะหยุดพร้อมข้อความและ rollback ทั้งหมด (ต้องแปลงเป็น `staff_pay_adjustment` เอง เพราะเป็นข้อมูลเงิน) และป้ายฐานคำนวณที่เป็นข้อความ (เช่น `CourseValuePerVisit`) แปลงเป็นตัวเลขไม่ได้ จึงเป็น `NULL` (ตัวเลขที่เก็บไว้คงเดิม)

การแปลงสถานะ: `closing_status` TRUE -> `Closed`, FALSE -> `Open` ; `payment_status` `Unpaid` -> `Open`

## ผลทดสอบ (15 ต.ค. 2026, PostgreSQL 16)
| กรณี | ผล |
|---|---|
| ฐานใหม่ (V1, V2, V3, V1001, V1002) ด้วย psql ทรานแซกชันเดียว และด้วย Flyway จริง | ผ่าน ทุก constraint validated |
| ฐานมีข้อมูลตัวอย่าง (seed) | ผ่าน |
| อัปเกรดฐานที่อยู่ที่ V1001 และมีข้อมูลที่ระบบเก่าสร้างผ่าน API จริง (5 คอร์ส, 4 การใช้สิทธิ์, 5 allocation, 2 closing, คืนเงิน, ย้อนการใช้สิทธิ์) ด้วย Flyway จริง | ผ่าน ("Successfully applied 1 migration ... now at version v1002") |
| ข้อมูลเก่าขอบเขต: การจ่าย Unpaid/Paid, closing ที่ยังไม่ปิด, ป้ายฐานคำนวณเป็นข้อความ | แปลงถูกต้อง |
| มี `Adjustment` เก่า | migration หยุดและ rollback ตามที่ออกแบบ |
| V3 ต้นฉบับบนข้อมูลเดียวกัน | ล้ม |

ชุดตรวจที่รันซ้ำได้: `bash backend/scripts/commission-migration/check-v1002.sh` (ใช้ Docker, 14 ข้อ) ส่วนการทดสอบกับข้อมูลที่ระบบเก่าสร้างจริงทำมือ: รัน backend ที่ `SPRING_FLYWAY_TARGET=1001`, สร้างข้อมูลด้วย `backend/scripts/commission-migration/seed-old-data.mjs` (แก้ `B` ให้ตรงพอร์ต) แล้วค่อยให้ Flyway ใช้ V1002

## ข้อมูลเก่าที่ยังไม่ผ่านกติกาใหม่ (ได้รับ NOTICE และคง NOT VALID)
- `sales_transaction.chk_sales_transaction_required_staff_visit`: รายการ SingleVisit เก่าไม่มีผู้รักษา/Visit
- `refund_transaction.chk_refund_transaction_completed_approval`: การคืนเงินที่ Completed เก่าไม่มีผู้อนุมัติ
- `staff_monthly_payment.chk_staff_monthly_payment_amounts`: แถวที่ไม่ใช่ Open เก่าไม่มียอดแยกประเภท

## ข้อควรรู้ก่อนรวมเข้า branch หลัก
- **แอปปัจจุบันบูตไม่ขึ้นหลัง V1002** เพราะ Hibernate `validate` ไม่ตรงกับ entity เดิม (ทดสอบแล้ว) ต้องทำ **T2 (ปรับ entity)** ให้เสร็จก่อนหรือพร้อมกัน ห้ามรวม V1002 เดี่ยวๆ เข้า branch ที่มี CI เปิดอยู่
- constraint `NOT VALID` บังคับกับแถวใหม่ทันที เช่น รายการขาย SingleVisit ใหม่ต้องมีผู้รักษาและ Visit ซึ่งโค้ดปัจจุบันยังไม่ทำ (เป็นส่วนของ T2/T10)
- seed `V1000__demo_data.sql` รันก่อน V1002 จึงไม่ต้องแก้สำหรับ migration นี้ (ตรวจแล้ว) แต่ legacy importer และ test fixture ที่อ้างคอลัมน์ที่ถูกลบต้องแก้ใน T2/T18
- ยังไม่ได้เทียบ V3 กับ Data Dictionary ทีละคอลัมน์ ตรวจเฉพาะตารางหลักด้านค่าคอมมิชชัน
