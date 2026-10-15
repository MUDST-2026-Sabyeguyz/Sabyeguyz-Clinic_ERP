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

## T3: entity ใหม่ของตารางค่าคอมมิชชัน
เพิ่ม entity และ repository สำหรับตารางที่ V1002 สร้างขึ้น พร้อม `StaffMonthlyPayment` (ตารางใหม่อ้างถึง และเดิมไม่มี entity)

| Entity | ตาราง | หมายเหตุ |
|---|---|---|
| `CourseRefundItem` | `course_refund_item` | รายการคอร์สในการคืนเงิน (ยอดคืน, จำนวนครั้งที่ตัดสิทธิ์) หนึ่งคอร์สต่อหนึ่งการคืนเงิน |
| `SingleVisitFeeLedger` | `single_visit_fee_ledger` | ค่ามือรักษารายครั้ง Allocation/Reversal ต่อเวอร์ชัน (ใช้ enum `AllocationEntryType` เดิม) |
| `StaffMonthlyPayment` | `staff_monthly_payment` | สรุปจ่ายรายคนรายเดือน (เดือนที่รักษา) สถานะ `PaymentStatus` Open/Closed/Paid |
| `StaffPayAdjustment` | `staff_pay_adjustment` | ใบปรับยอดหลัง Finalize `AdjustmentStatus` |
| `AdjustmentApplication` | `adjustment_application` | การนำใบปรับยอดไปใช้ในงวด `ApplicationStatus` Draft/Applied/Cancelled |

เชื่อมกับของเดิม: `CourseLedger` มี `courseRefundItem` และ `cancellation` กับค่า enum `Cancellation` ; `CommissionAllocationLedger` มี `ownerMonthlyPayment` และ `treatingMonthlyPayment`
ตรรกะทางธุรกิจของตารางเหล่านี้ (ใครสร้างแถว เมื่อไหร่) ยังไม่ได้ทำ: คือ T10-T17

ทดสอบ: `backend/cypress/e2e-v2/commission-entities.cy.js` (4 ข้อ) ใช้ endpoint ทดสอบ `CypressCommissionEntitiesController` (เฉพาะโปรไฟล์ `cypress-v2`) เตรียมข้อมูลจริงผ่าน API (ผู้ป่วย, ขายรายครั้ง, คืนเงิน, ซื้อคอร์ส) แล้วบันทึก-อ่านกลับ และยืนยันว่า DB ปฏิเสธข้อมูลผิดกติกา (ชื่อ constraint อยู่ในผลที่ตรวจ): รายการคืนเงินซ้ำ/ยอด 0, ค่ามือกลับรายการซ้ำหรือไม่มีเหตุผล, จ่ายสถานะ Paid ที่ไม่มียอด/ยอดรวมไม่ตรง/ไม่มีวันที่จ่าย, ใบปรับยอด (เดือนที่ใช้ไม่หลังเดือนเดิม, ยอด 0, ไม่มีต้นทาง, ไม่มีเหตุผล, ยอดคงเหลือไม่ตรง, สถานะอนุมัติแต่ไม่มีผู้อนุมัติ) และการนำไปใช้ที่ Applied โดยไม่มีผู้อนุมัติหรือการหักที่ไม่มีเลขอ้างอิง
ผล: backend HTTP v2 222/222 (218 เดิม + 4 ใหม่), `SchemaCheck` 49 entities ผ่าน, `RuntimeCheck` ผ่าน

## ชุด A (T4-T7): Scheme/Tier, ยอดขายรายเดือน, กฎค่ามือส่วนกลาง

**Migration V1003** (`V1003__commission_tier_contiguity.sql`): ตาราง Tier ของข้อมูลตัวอย่างเขียนเป็นบาทเต็ม (59,999 แล้ว 60,000) ซึ่งทำให้ 59,999.01-59,999.99 ไม่อยู่ใน Tier ใดเมื่อยอดขายมีสตางค์ จึงปรับขอบบนให้เป็น `ขอบล่างของ Tier ถัดไป - 0.01` (อัตราและขอบล่างไม่เปลี่ยน) เลข V1002/V1003 ถูกใช้แล้ว ฝั่งอื่นใช้ V1004 ขึ้นไป

**T4 Scheme/Tier** (`CommissionSchemeService`, `CommissionSchemeResolver`, `CommissionTierResolver`)
- Tier เป็นบาท 2 ตำแหน่ง ขอบ inclusive ทั้งสองด้าน Tier แรกเริ่ม 0.00, Tier ถัดไปเริ่ม `ขอบบนเดิม + 0.01` พอดี (50,000.00 แล้ว 50,000.01), Tier สุดท้ายไม่มีเพดาน, `tier_order` ไม่ซ้ำ, อัตรา 0-1 ไม่เกิน 4 ตำแหน่ง ; ยอดขายถูกปัดเป็น 2 ตำแหน่งก่อนเทียบ (59,999.995 ไป Tier ถัดไป)
- วันที่ของ Scheme ไม่ซ้อนกัน (ปลายวัน inclusive) ; Scheme เวอร์ชันใหม่แบบไม่มีวันสิ้นสุดจะปิดเวอร์ชันเดิมที่ไม่มีวันสิ้นสุดเป็น `วันเริ่มใหม่ - 1` โดยอัตโนมัติ (ปฏิเสธถ้าเวอร์ชันเดิมถูกใช้กับคอร์สที่ขายตั้งแต่วันนั้นแล้ว) ; resolver ต้องพบ Scheme/Tier เพียงหนึ่งรายการ
- Scheme ที่คอร์สหรือการปิดยอดอ้างอิงแล้ว (`locked` ในการตอบ) แก้ Tier/วันเริ่ม/ปิดใช้งานไม่ได้ (409) แก้ได้เฉพาะชื่อและวันสิ้นสุด ; ทำเวอร์ชันใหม่ด้วย `POST /api/commission/schemes/{id}/versions` ; `PUT` ที่ส่ง `tiers: []` หมายถึงไม่เปลี่ยน Tier ; เวอร์ชันนับต่อชื่อ Scheme

**T5 Owner และค่าที่ล็อกตอนขาย**: ทำไว้แล้วใน T2 (ผู้ขาย = Owner ฟิลด์เดียว, `course_price` = commissionable amount, `treatment_fee_base_amount` = amount - ส่วนลดท้ายบิล, `sale_month`, scheme id/version, `provisional_commission_rate`) ชุดนี้เพิ่มการทดสอบอัตโนมัติ

**T6 ยอดขายสำหรับหา Tier** (`MonthlyTierSalesService`): รวม `course_price` ของคอร์สที่ขายโดยผู้ขายในเดือนขาย เฉพาะที่ยังมีผล (รายการขายไม่ถูก void, ใบเสร็จไม่ Voided, คอร์สไม่ Cancelled) แล้วหักยอด `Course_refund_item.refund_amount` ของการคืนเงินที่ **Completed** (แต่ละรายการครั้งเดียว ไม่หัก Pending/Failed/Voided และไม่หักซ้ำกับคอร์สที่ void/cancel แล้ว) ผลไม่ต่ำกว่า 0 ; ใช้ทั้งตอนขาย (อัตราชั่วคราว), พรีวิว, รายการปิดยอด และตอนปิดยอดจริง ; หลังปิดยอดแล้ว ยอดที่เก็บไว้ไม่ถูกคำนวณใหม่

**T7 กฎค่ามือส่วนกลาง** (`TreatmentFeeRuleService`, `/api/commission/treatment-fees`): สร้าง/แก้/เปิด-ปิดกฎ Fixed (บาทต่อ Visit ทศนิยมไม่เกิน 2) หรือ Percentage (0-100 ทศนิยมไม่เกิน 4) ; ช่วงวันที่ของกฎที่เปิดใช้งานไม่ซ้อนกัน (กฎใหม่แบบไม่มีวันสิ้นสุดปิดกฎเดิมเป็น `วันเริ่มใหม่ - 1`) ; กฎที่ ledger คัดลอกไปแล้ว (`used`) แก้ประเภท/อัตรา/วันเริ่มไม่ได้ (409) ให้สร้างกฎใหม่ ; สูตรค่ามือคอร์ส = `treatment_fee_base_amount x อัตรา / (total_visits x 100)` ปัดผลสุดท้ายครั้งเดียว (ทำไว้ใน T2)

## ชุด B (T8-T12): การใช้คอร์ส, ผู้รักษา, ค่ามือรายครั้ง, การกลับรายการ/คืนเงิน

**Owner commission ยังไม่ทราบก่อนปิดยอด (ตามที่ Data Dictionary กำหนด NULL ไม่ใช่ 0)** (`CourseCommissionService`)
- ตอนใช้คอร์สที่ยังไม่ล็อกอัตรา: ถ้า Owner รักษาเอง `owner_net_commission` และ `commission_allocation_per_visit` ของ usage เป็น **NULL**, ยังไม่มีแถว allocation, `commission_outstanding` เป็น NULL ; ถ้าคนอื่นรักษาหรือ Owner ลาออกแล้ว `owner_net_commission` = 0 ทันทีและมีแถว allocation (ค่ามือเต็ม) เพราะไม่ต้องรู้อัตรา
- ตอนปิดยอดเดือนขาย (`MonthlyCommissionClosingService` → `settle`): ทุก usage ของคอร์สที่เพิ่งล็อกได้อัตราตามตำแหน่ง ; ที่ Owner รักษาเองได้ `owner_net_commission` และสร้างแถว allocation เวอร์ชัน 1 ; ที่คนอื่นรักษาได้อัตราตำแหน่งไว้ตรวจสอบเท่านั้น (Owner ยังได้ 0, ไม่ย้ายไปครั้งอื่น)
- **ข้อสันนิษฐานที่ Data Dictionary ไม่ได้ระบุตรงๆ (ให้ Manager ตรวจ):** จังหวะสร้างแถว allocation ของ session ที่ใช้ก่อนปิดยอดคือ "ตอนปิดยอด" (Dictionary บอกว่า allocation สร้างภายหลัง usage ได้เมื่อปิดยอดเดือนขาย) ; Visit ในวันที่ลาออกพอดียังได้ commission (Dictionary เขียนว่า Visit **หลัง** วันลาออก = 0)
- หลังปิดยอด: session ใหม่ได้ `owner_net` ตามอัตราของตำแหน่งทันที ; `recalculate` (ใช้เมื่อแก้อัตราก่อนจ่าย: override อัตรา, และ Reopen ในชุด C) กลับ allocation เดิมแล้วสร้างเวอร์ชันถัดไป ปฏิเสธถ้า allocation อยู่ใน payment ที่ Paid แล้ว

**T8 ลำดับสิทธิ์และเศษ** (`CommissionAllocationCalculator`): อัตราต่อตำแหน่ง = `ROUND(pool/total_visits,2)` ทุกตำแหน่งยกเว้นครั้งสุดท้ายที่ได้ส่วนที่เหลือของ pool (700/13 → 53.85 x 12 แล้ว 53.80) ; ไม่เกินงบที่เหลือและไม่ติดลบ (pool 0.07 / 12 ครั้ง → 0.01 x 7 ที่เหลือ 0) ; ตำแหน่ง = ตำแหน่งว่างต่ำสุดของคอร์ส (รวมทุกผู้ใช้) ตำแหน่งที่ Reversed ถูกใช้ซ้ำและมี `replaces_usage_id` ; ไม่เกิน `total_visits`

**T9 Owner รักษาเอง**: ข้างบน ; ผู้รักษาของ Visit ต้องเป็นคนเดียวกับของการใช้สิทธิ์ ; ผู้รักษาต้องยังทำงานในวันนั้น (ผู้ที่กำลังจะลาออกยังรักษาได้ถึงวันสุดท้าย), อยู่สาขาเดียวกับ Visit ; Owner ลาออก: `commission_outstanding` = 0 ตั้งแต่วันลาออก

**T10 PT อื่นรักษาแทน**: `POST /api/course-usages` รับ `treatingStaffId` (ค่าเริ่มต้น = Owner) และ `patientId` (ผู้ใช้สิทธิ์ ค่าเริ่มต้น = ผู้ซื้อ/ผู้ป่วยของนัด) ; ค่ามือจ่ายเต็มตามกฎ ณ วันรักษา ไม่จำกัดด้วย commission (ไม่พบกฎ → 409 ไม่ใช้ 0) ; Owner ได้ 0 ; ใบ allocation เก็บ snapshot ของกฎ/ฐานก่อนปัด

**T11 ค่ามือรักษารายครั้ง** (`SingleVisitFeeService`, `SingleVisitFeeAccrual`): เมื่อใบเสร็จชำระครบและรายการ SingleVisit มี Visit ที่รักษาโดย PT หนึ่งคน สร้าง `single_visit_fee_ledger` : Percentage = `ROUND(ฐาน x ค่า / 100, 2)` โดยฐาน = `amount - ส่วนลดท้ายบิลของรายการนั้น` (ไม่รวมคอร์สในบิล), Fixed = หนึ่งครั้งต่อ Visit ไม่คูณจำนวน ; ถ้ายังไม่มีกฎ **ไม่บล็อกการขายและไม่ใส่ 0** รอ `POST /api/commission/single-visit-fees/backfill?from=&to=` หลังตั้งกฎ ; void ใบเสร็จก่อนจ่าย → Reversal (เวอร์ชันเดียวกัน, ครั้งเดียว)

**T12 กลับรายการ/ยกเลิก/คืนเงิน**
- กลับ usage (`reverse`): ถ้ามี allocation ที่ยังไม่ถูกกลับ → Reversal (ค่าตรงข้ามทุกช่อง ครั้งเดียว) ; ถ้ายังไม่มี (Owner commission ยังไม่ทราบ) ไม่มีอะไรให้กลับ ; allocation ที่อยู่ใน payment ที่ Paid แล้ว กลับไม่ได้ (409 ให้ใช้ Adjustment)
- ยกเลิกคอร์ส (`POST /api/course-purchases/{id}/cancel` ต้องมีเหตุผล) และ void ใบขายคอร์ส: void รายการขาย (Transaction_cancellation Void) ตัดสิทธิ์ที่ยังไม่ใช้ทั้งหมดด้วย `Course_ledger.Cancellation` (ผูก `cancellation_id`, ครั้งเดียว) คอร์สเป็น Cancelled ; ค่ามือ/commission ของครั้งที่ใช้ไปแล้วคงเดิม
- คืนเงิน: ใบเสร็จที่มีคอร์สต้องส่ง `items: [{courseId, refundAmount, cancelledVisits}]` (ยอดรวมเท่ากับยอดคืน, ไม่เกินยอดที่จ่ายสำหรับคอร์ส, ไม่เกินสิทธิ์ที่ยังไม่ใช้) ; **คืนคอร์สที่ใช้ไปบางส่วนได้** (ส่วนที่ยังไม่ใช้) ; ตัดสิทธิ์ตอน Completed ใน transaction เดียวกัน ครั้งเดียวต่อรายการ ; Failed/Voided ไม่ตัดสิทธิ์ ; คอร์สเป็น Refunded เมื่อสิทธิ์หมด ไม่เช่นนั้นคง Active ; ไม่เรียกคืนค่ามือ/commission และไม่คำนวณฐานเดิมใหม่ ; `POST /api/refunds/{id}/void` ยกเลิกคำขอที่ยัง Pending
- แก้อัตราคอร์ส (override) ถูกปฏิเสธหลังเดือนขาย Finalize

**ผลต่อ API/ฟรอนต์เอนด์:** `GET /api/commission/treatment-report` คืน `ownerNet`/`gross` เป็น `null` พร้อม `ownerNetPending: true` จนกว่าจะปิดยอด (อิงจาก usage ไม่ใช่ allocation) ; `SchemeResponse` มี `locked` ; `TreatmentFeeResponse` มี `effectiveFrom/effectiveTo/enabled/used` ; ตอบคืนเงินมี `items` ; จอปรับตั้งค่า/คืนเงินในฟรอนต์เอนด์ยังไม่ได้ปรับ (นอกขอบเขตของ backend)

ทดสอบ: `commission.cy.js` (Scheme/Tier/กฎค่ามือ ผ่าน HTTP), `commission-sessions.cy.js` (ตัวอย่างในเอกสาร PDF ข้อ 01, 02, 03, 04, 07, เศษ 53.80, NULL ก่อนปิดยอด, ลาออก, กลับรายการ, ค่ามือรายครั้ง, ยกเลิก/คืนเงิน), `payment-policy.cy.js` (คืนเงินคอร์สที่ใช้บางส่วน + ล็อกแถวคอร์ส), ชุดเก่า `v2-finance` (ตัวคำนวณ, resolver, validation แบบ unit บน stub)
ตัวอย่างข้อ 05 (Reopen) และ 06 (Adjustment หลัง Finalize) อยู่ในชุด C

## ข้อควรรู้ก่อนรวมเข้า branch หลัก
- constraint `NOT VALID` บังคับกับแถวใหม่ทันที (ดูตารางด้านบน) ฐานเดิมที่รันบน V1001 อัปเกรดได้ตามที่ทดสอบ
- seed `V1000__demo_data.sql` รันก่อน V1002 จึงไม่ต้องแก้ ; legacy importer ที่อ้างคอลัมน์ที่ถูกลบต้องแก้ในงานของฝั่ง P9
- เลข migration ที่ใช้แล้ว: V1002 (สคีมาค่าคอมมิชชัน) และ V1003 (Tier ต่อเนื่องระดับสตางค์) ฝั่งอื่นใช้ V1004 ขึ้นไป
- เทียบสคีมาที่ migrate แล้ว (V1-V1003) กับ Data Dictionary (15 ต.ค. 2026, 48 ตาราง) ทีละคอลัมน์ ทั้งชื่อ ชนิดข้อมูล ความยาว/ทศนิยม และ NULL/NOT NULL: **ตรงทุกคอลัมน์** ยกเว้นคอลัมน์ที่แอปมีเพิ่มนอก Dictionary (`patient.gender_detail`, `service.default_bonus_visits`, `course.bonus_visits`, `receipt.taxable_amount/tax_amount`, `appointment.room_id`) และตารางที่ยังคงอยู่ (`course_sale_staff_share` เลิกใช้แต่ยังเก็บข้อมูลเก่า, `staff_group*`, `service_group*`, `user_branch_access`, `legacy_v1002_*` ที่ V1002 เก็บข้อมูลตัดสินใจเดิมไว้ตรวจ)
- ผลทดสอบชุด A+B (15 ต.ค. 2026): backend HTTP v2 **249/249**, ชุดเก่า `test:api` **432/432**, `RuntimeCheck` และ `SchemaCheck` (49 entities) ผ่าน, `check-v1002.sh` ผ่านทั้งฐานว่าง/ฐานมี seed/ฐานมีข้อมูลเก่า, frontend ESLint + unit + type check + build ผ่าน และเบราว์เซอร์ **117/117** (หน้า Tier และหน้าปิดรอบปรับให้ใช้ช่วงยอดระดับสตางค์)
