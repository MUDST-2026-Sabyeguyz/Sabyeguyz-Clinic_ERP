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

## T2: โค้ดตามสคีมาใหม่ (entity และ API)
หลัง V1002 แอปบูตด้วย Hibernate validate ได้แล้ว (SchemaCheck: 44 entities ผ่าน) การเปลี่ยนที่มีผลต่อ API และข้อมูล

| เรื่อง | เดิม | ตอนนี้ (ตาม Data Dictionary) |
|---|---|---|
| เจ้าของคอร์ส (Owner PT) | `case_owner_staff_id` แยกจากผู้ขาย | `Course.seller_staff_id` ฟิลด์เดียว ผู้ขายคือ Owner |
| `POST /api/course-purchases` | `caseOwnerStaffId` เลือกเจ้าของ, `shares` แบ่งเครดิต 2 PT | `caseOwnerStaffId` = PT ที่บันทึกการขายให้ (เป็นผู้ขาย/Owner), ไม่ส่งใช้ผู้ใช้ที่ล็อกอิน ; `shares` ถูกปฏิเสธ 400 (เลิกแบ่งเครดิต, ตาราง `course_sale_staff_share` ไม่ใช้แล้ว) |
| ยอดขายสำหรับ Tier และรายงานยอดขายต่อพนักงาน | รวมจากสัดส่วนเครดิต | รวมราคาคอร์สของผู้ขายตามเดือนขาย |
| ค่ามือผู้รักษาแทน | จำกัดไม่เกิน commission ต่อครั้ง Owner ได้ส่วนต่าง | จ่ายเต็มตามกฎ ไม่จำกัด Owner ได้ 0 ; Owner ที่ลาออกแล้ว (Visit หลังวันลาออก) ได้ 0 |
| กฎค่ามือ | ผูกพนักงาน/บริการ/กลุ่ม, ฐาน CourseValuePerVisit | กฎส่วนกลางเดียว เลือกตัวที่มีผลล่าสุดในวันนั้น ; % = ยอดสุทธิของคอร์ส x % / (ครั้งรวม x 100) ปัดครั้งเดียว ; `GET /api/commission/treatment-fees` ยังคืน `staffId`/`percentageBase` เป็น null |
| ฐานค่ามือของคอร์ส | - | `treatment_fee_base_amount` = ยอดรายการขาย หลังหักส่วนลดท้ายบิล (ตั้งตอนกระจายส่วนลดในใบเสร็จ) ; `course_price` ยังเป็นฐาน commission ก่อนส่วนลดท้ายบิล |
| การใช้สิทธิ์ | - | `usage_sequence` (ตำแหน่งต่อคอร์ส ใช้ตำแหน่งว่างจากการย้อน) และ `replaces_usage_id` ; allocation มี `allocation_version` ; ledger เปลี่ยนชื่อเป็น `owner_staff_id` / `reverses_allocation_id` |
| `commission_outstanding` | pool - gross ที่จัดสรรแล้ว | ต่อครั้ง x ครั้งที่เหลือ ; NULL ก่อนปิดยอด (ยังไม่ทราบอัตรา) |
| ผู้รักษาของ Visit | - | `patient_visit.treating_staff_id` ต้องมีเมื่อ InService/Completed (ค่าเริ่มต้น = ผู้ที่มอบหมาย) ; การใช้คอร์สตั้งค่าให้ถ้ายังว่าง |
| ขายรายครั้ง `POST /api/payments` | ไม่ผูก Visit | สร้าง Visit ที่เสร็จแล้วพร้อมผู้รักษา: `treatingStaffId` (ใหม่, ไม่บังคับ) ถ้าไม่ส่งใช้ผู้ขาย ถ้าไม่มีใช้พนักงานของผู้ล็อกอิน ถ้ายังไม่มีตอบ 400 |
| คืนเงินที่เสร็จ | - | บันทึก `approved_by/approved_at` |
| โอนคอร์ส | สร้างสิทธิ์ผู้รับหลังบันทึกรายการโอน | สร้างสมาชิกและสิทธิ์ของผู้รับก่อน (foreign key ใหม่) |
| ปิดยอดรายเดือน | boolean | enum `Open/Closed/Finalized` (API ยังคืน OPEN/CLOSED, เพิ่ม FINALIZED) |
| `POST /api/staff/{id}/terminate` | ต้องส่ง `commissionAfterTerminationPolicy` | ส่งแค่ `terminationDate` (ฟิลด์เดิมรับแต่ไม่ใช้) และต้องไม่ก่อนวันเริ่มงาน |
| สรุปรายงานค่ามือ `gross` | gross ของ allocation | = owner net + ค่ามือ |

ผลทดสอบ (15 ต.ค. 2026): backend HTTP v2 **218/218**, ชุดเก่า `test:api` **395/395**, `SchemaCheck` และ `RuntimeCheck` ผ่าน, frontend unit/type check/build/ESLint ผ่าน, frontend บนเบราว์เซอร์ **117/117** (รอบแรกบน worktree ใหม่ล้มเกือบหมดเพราะ Vite รอบแรกยังไม่อุ่น รันซ้ำผ่าน) นอกจากนี้ตรวจข้อมูลที่เขียนลง DB จริง: ผู้รักษาของ Visit, ฐานค่ามือ 8,100 เมื่อมีส่วนลดท้ายบิล 900, ลำดับการใช้สิทธิ์/แทนที่, ผู้อนุมัติคืนเงิน, การขายรายครั้งผูก Visit
ยังไม่ได้ทำ (งานถัดไป): การคำนวณตามกติกาเศษ commission ครั้งสุดท้าย (T8), ผู้รักษาที่เลือกเอง/Owner ต่างจากผู้รักษาของนัด (T9/T10), ค่ามือรายครั้ง (T11), Reopen/Finalize/ชำระ (T13-T16), Adjustment (T17)

## ข้อควรรู้ก่อนรวมเข้า branch หลัก
- constraint `NOT VALID` บังคับกับแถวใหม่ทันที (ดูตารางด้านบน) ฐานเดิมที่รันบน V1001 อัปเกรดได้ตามที่ทดสอบ
- seed `V1000__demo_data.sql` รันก่อน V1002 จึงไม่ต้องแก้ ; legacy importer ที่อ้างคอลัมน์ที่ถูกลบต้องแก้ในงานของฝั่ง P9
- ยังไม่ได้เทียบ V3 กับ Data Dictionary ทีละคอลัมน์ ตรวจเฉพาะตารางหลักด้านค่าคอมมิชชัน
