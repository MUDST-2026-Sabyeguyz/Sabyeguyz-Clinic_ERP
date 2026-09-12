LA BALANCE ‐ Tier Commission & Treatment Allocation System
Requirement
Business Requirement / Functional Specification
For Development Team, Product Owner, Finance / Accounting
29 August 2026
สารบัญ
Executive Summary . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 3
1. Objective 3
2. Commission Tier 3
3. Core Principle 4
3.1 Event A ‐ Sale . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 4
3.2 Event B ‐ Monthly Closing . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 4
4. Critical Rule: Commission Rate Must Follow the Course 4
5. Commission Calculation 5
5.1 Total Course Commission Pool . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 5
5.2 Commission Allocation Per Visit . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 5
6. Commission Release 5
7. Monthly Commission Concepts 5
7.1 Commission Generated . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 6
7.2 Gross Commission Allocated . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 6
7.3 Owner Net Commission Released . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 6
7.4 Commission Outstanding . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 6
8. Course ID 6
9. Required Course Database Fields 6
9.1 Identity . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 6
9.2 Sale . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 6
9.3 Commission . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 7
9.4 Usage . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 7
9.5 Status . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 7
10. Monthly Commission Closing Table 7
11. Monthly Closing Workflow 7
12. Monthly Closing Must Be Immutable 8
13. Visit Usage 8
14. Seller / Case Owner PT vs Treating PT 8
14.1 Seller / Case Owner PT . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 8
1\n\n14.2 Treating PT . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 9
15. Substitute Treatment Fee Allocation 9
15.1 Treatment Fee Configuration 9
A. Fixed Amount . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 9
B. Percentage . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 10
15.2 Financial Allocation Rule 10
16. Shared Course 10
17. Shared Course Member Table 11
18. Shared Course + Different Therapist 11
19. Treatment Fee 11
20. Employee Has No Sale in Current Month 12
21. Employee Resignation 12
22. Refund / Cancellation 12
23. Commission Tier Configuration 12
24. Tier Versioning 13
25. Audit Requirement 13
26. Recommended Dashboard 14
26.1 Sales . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 14
26.2 Commission . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 14
26.3 Treatment . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 14
26.4 Total Variable Pay . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 14
27. Important Business Logic 14
28. Simplified System Flow 15
29. Acceptance Criteria 15
Case 1 ‐ Original Tier Must Persist . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 15
Case 2 ‐ No New Sale, Old Course Still Releases . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 15
Case 3 ‐ Substitute PT Fixed Fee . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 15
Case 4 ‐ Shared Course . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 15
Case 5 ‐ Course Usage Limit . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 16
Case 6 ‐ Historical Audit . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 16
Case 7 ‐ Admin Changes Tier Configuration . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 16
Case 8 ‐ Invalid Tier Range . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 16
Case 9 ‐ Substitute PT Fixed Treatment Fee . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 16
Case 10 ‐ Substitute PT Percentage Treatment Fee . . . . . . . . . . . . . . . . . . . . . . . . . . . . 16
Case 11 ‐ Treatment Fee Exceeds Allocation . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 16
30. Key Development Principle 16
Appendix A ‐ Recommended Allocation Ledger Fields 17
Appendix B ‐ Recommended Admin Settings 17
Appendix C ‐ Key Terms 17
2\n\nExecutive Summary
ระบบนี้ใช้สําหรับคํานวณและบันทึกค่าตอบแทนจากการขาย Course/Package ของนักกายภาพบําบัด โดยมีหลักสําคัญ 4 ข้อ:
1. Commission Tier คํานวณจากยอดขาย Course รวมของพนักงานในแต่ละเดือน และต้องปรับแต่งได้จาก Settings
2. เม่ือปิดเดือนระบบต้องFreezeCommissionRateของCourseแต่ละรายการเพ่ือให้คอร์สเดิมจดจําTierของเดือนที่ขาย
ไว้ถาวร
3. บริษทั ไม่จ่าย Commission ทัง้ ก้อนในเดือนที่ขาย แต่ทยอย Release ตามจํานวน Visit ที่คนไข้ใช้จริง
4. หากCaseOwnerไม่ได้รักษาเองและมีPTคนอ่ืนรักษาแทนTreatmentFeeของผู้รักษาแทนต้องถูกหักจากCommission
Allocation ของ Visit นัน้ ไม่ใช่ค่าใช้จ่ายเพิ่มซ้อนบน Commission เดิม
1. Objective
ระบบต้องสามารถคํานวณ Sales Commission จากการขาย Course/Package ตามยอดขายรวมรายเดือนของพนักงาน และต้อง
สามารถ จดจํา Commission Tier ของคอร์สแต่ละรายการอย่างถาวร แม้ว่าคนไข้จะกลับมาใช้บริการในเดือนถัด ๆ ไป
บริษทั ไม่จ่าย Sales Commission ทัง้ หมดทันทีในเดือนท่ีขายคอร์ส
Commission จะถูกทยอยจ่ายตามจํานวนครัง้ ที่คนไข้มาใช้บริการจริง เพ่ือ:
1. รักษากระแสเงินสดของบริษทั
2. กระตุ้นให้นักกายภาพติดตามคนไข้ให้มารักษาต่อเน่ือง
3. ลดความเสี่ยงกรณีพนักงานขายคอร์สแล้วลาออกก่อนให้บริการครบ
4. สามารถตรวจสอบ Commission ย้อนหลังได้อย่างชัดเจน
2. Commission Tier
Commission Rate คํานวณจาก ยอดขาย Course รวมของพนักงานแต่ละคนภายในเดือนเดียวกัน
Commission Tier ต้องเป็น Configurable Setting ในระบบ และห้าม Hard‐code ค่าไว้ใน Program
Admin ที่ได้รับสิทธิต์ ้องสามารถปรับแต่งได้ทัง้ :
• ช่วงยอดขายขัน้ ต่าํ ของแต่ละ Tier (Minimum Monthly Course Sales)
• ช่วงยอดขายสูงสุดของแต่ละ Tier (Maximum Monthly Course Sales)
• Commission Rate (%) ของแต่ละ Tier
• เพิ่ม Tier ใหม่
• ลบ Tier ที่ยังไม่ได้ถูกใช้งาน
• เปิด / ปิดการใช้งาน Tier
• กําหนดลําดับ Tier
ค่าเริ่มต้นของระบบ:
Monthly Course Sales Commission Rate
0 ‐ 59,999 บาท 5%
60,000 ‐ 69,999 บาท 6%
70,000 ‐ 79,999 บาท 7%
80,000 ‐ 89,999 บาท 8%
90,000 บาทข้ึนไป 10%
ตัวเลขข้างต้นเป็นเพียง Default Configuration และต้องแก้ไขได้จากหน้า Settings โดยไม่ต้องแก้ Source Code หรือ Deploy
ใหม่
ตัวอย่าง Configuration ใหม่ที่ Admin อาจตัง้ ได้:
3\n\nMonthly Course Sales Commission Rate
0 ‐ 49,999 บาท 4%
50,000 ‐ 79,999 บาท 6%
80,000 ‐ 119,999 บาท 8%
120,000 บาทข้ึนไป 12%
ระบบต้อง Validate Configuration เพ่ือป้องกัน:
• ช่วงยอดขายซ้อนกัน (Overlapping Range)
• มีช่องว่างระหว่าง Tier โดยไม่ได้ตัง้ ใจ (Gap)
• Minimum Sales มากกว่า Maximum Sales
• Commission Rate ติดลบ
• Tier มากกว่าหน่ึงรายการที่ครอบคลุมยอดขายเดียวกัน
Commission Tier คิดจากยอดขาย Course เท่านัน้
3. Core Principle
ระบบต้องแยกออกเป็น 2 เหตุการณ์หลัก
3.1 Event A ‐ Sale
วันที่พนักงานขาย Course ระบบบันทึก:
• Course ID
• Receipt No.
• Patient / HN
• Package
• Sale Date
• Seller / Case Owner PT
• Course Price
• Total Visits
ในระหว่างเดือน Commission Rate ยังสามารถอยู่ในสถานะ Pending / Provisional เน่ืองจากยอดขายของเดือนยังไม่สิน้ สุด
3.2 Event B ‐ Monthly Closing
เม่อื สิน้ เดือน ระบบรวมยอดขาย Course ของพนักงานแต่ละคน
ตัวอย่าง: หมอกิฟ๊ ขาย Course เดือน August รวม 75,000 บาท ระบบกําหนด Commission Tier = 7%
หลัง Monthly Closing ระบบต้อง Freeze Commission Rate = 7% และคอร์สทุกคอร์สที่หมอกิฟ๊ ขายในเดือน August ต้องถูกผูก
กับ Rate 7%
Rate นี้ต้องไม่เปลี่ยนตามยอดขายของเดือนถัดไป
4. Critical Rule: Commission Rate Must Follow the Course
Commission Rate ต้องผูกกับ Course ID / Sale Transaction ไม่ใช่ผูกกับ Current Employee Rate
ตัวอย่าง August:
• Seller = หมอกิฟ๊
• Monthly Sales = 75,000 บาท
• Tier = 7%
RE-2206315
• Course ID =
4\n\n• Course Price = 10,000 บาท
• Total Visits = 10
ระบบต้องบันทึก:
• Sale Month = Aug‐2026
• Locked Commission Rate = 7%
• Total Course Commission Pool = 700 บาท
• Commission Allocation / Visit = 70 บาท
RE-2206315
ในSeptemberหากหมอกิฟ๊ ขายใหม่รวม95,000บาทและได้Tier10%คอร์ส ที่ขายในAugustยังคงใช้7%
Database ต้องเก็บ Commission Rate ของ Course ไว้เอง
5. Commission Calculation
5.1 Total Course Commission Pool
สูตร:
Course Price x Locked Commission Rate
ตัวอย่าง:
10,000 x 7% = 700 บาท
5.2 Commission Allocation Per Visit
สูตร:
Total Course Commission Pool / Total Course Visits
ตัวอย่าง:
700 / 10 = 70 บาท / Visit
Course Record ต้องเก็บอย่างน้อย:
• Locked Commission Rate
• Total Course Commission Pool
• Total Visits
• Commission Allocation / Visit
6. Commission Release
บริษทั ไม่จ่าย Commission Pool ทัง้ หมดทันที แต่จะ Allocate / Release ตาม Visit ที่ถูกใช้จริง
RE-2206315
ตัวอย่าง Course :
• Commission Allocation / Visit = 70 บาท
• September คนไข้ใช้ 3 Visits
• Gross Commission Allocation = 3 x 70 = 210 บาท
• Commission Pool Remaining = 700 ‐ 210 = 490 บาท
หมายเหตุ: Gross Commission Allocation 210 บาท ยังอาจถูกแบ่งเป็น Owner Net Commission และ Substitute Treatment
Fee หากผู้รักษาจริงไม่ใช่ Case Owner
7. Monthly Commission Concepts
ระบบต้องแยกอย่างน้อย 4 ตัวเลข
5\n\n7.1 Commission Generated
Commission Pool ที่เกิดจาก Course ใหม่ที่ขายในเดือนนัน้
7.2 Gross Commission Allocated
จํานวนเงินจาก Commission Pool ที่ถูกตัดตาม Visit Usage ในเดือนนัน้ ก่อนแบ่งค่ามือให้ Substitute PT
7.3 Owner Net Commission Released
เงิน Commission ที่ Case Owner ได้รับจริง หลังหัก Substitute Treatment Fee ใน Visit ที่ไม่ได้รักษาเอง
7.4 Commission Outstanding
Commission Pool ที่ยังไม่ถูก Allocate เน่ืองจาก Course ยังเหลือ Visit
สูตร:
Total Course Commission Pool ‐ Gross Commission Allocated To Date
8. Course ID
Course ID ต้องเป็น Unique Identifier ของการซ้ือ Course แต่ละครัง้
RE-2206315
สามารถใช้เลขที่ใบเสร็จ เช่น ได้ หาก 1 Receipt = 1 Course
หาก 1 Receipt มีหลาย Course ให้เพิ่ม Sequence เช่น:
• RE‐2206315‐01
• RE‐2206315‐02
Course ID ไม่เท่ากับ HN และไม่เท่ากับ Package Name
ตัวอย่าง:
HN260001
• HN =
RE-2206315
• Course ID =
Routine Pain 10 Visits
• Package =
9. Required Course Database Fields
9.1 Identity
• course_id
• receipt_no
• patient_hn
• patient_name
• package_id
• package_name
9.2 Sale
• sale_date
• sale_month
• seller_employee_id
• case_owner_employee_id
• seller_name
• course_price
• total_visits
6\n\n9.3 Commission
• commission_status
• commission_scheme_id
• commission_scheme_version
• provisional_commission_rate
• locked_commission_rate
• total_course_commission_pool
• commission_allocation_per_visit
• commission_generated_date
9.4 Usage
• visits_used
• remaining_visits
• gross_commission_allocated_total
• owner_net_commission_released_total
• substitute_treatment_fee_total
• commission_outstanding
9.5 Status
• active
• completed
• cancelled
• refunded
10. Monthly Commission Closing Table
ควรมีตารางแยกสําหรับ Monthly Closing อย่างน้อย:
• closing_id
• month
• employee_id
• monthly_course_sales
• commission_scheme_id
• commission_scheme_version
• calculated_commission_rate
• locked_commission_rate
• closing_status
• closed_at
• closed_by
Closing Status:
• Open
• Closed
11. Monthly Closing Workflow
ตัวอย่าง August:
1. ระบบค้นหา Course Sales ของ Seller = หมอกิฟ๊ และ Sale Month = August 2026
2. รวมยอด = 75,000 บาท
3. อ่าน Tier Configuration ที่ Effective ในเดือนนัน้
4. ระบบแสดง Suggested Commission Rate = 7%
5. Admin / Finance กด Close Month
6. ระบบบันทึก Monthly Closing
7\n\n7. Lock Commission Rate = 7%
8. Update Course ทุก Course ของหมอกิฟ๊ ที่ขายใน August
9. คํานวณ Total Course Commission Pool
10. คํานวณ Commission Allocation / Visit
11. เปลี่ยน Commission Status เป็น Locked
12. Monthly Closing Must Be Immutable
เม่อื Closing Status = Closed ข้อมูล Commission Rate ไม่ควรแก้ไขได้โดย User ทัว่ ไป
หากต้องแก้ย้อนหลังต้องใช้ Admin Override และมี Audit Log:
• Old Rate
• New Rate
• Changed By
• Changed Date
• Reason
13. Visit Usage
ทุกครัง้ ที่มีการใช้ Course ระบบบันทึก:
• visit_id
• visit_date
• course_id
• patient_hn
• treating_employee_id
• visit_qty
• case_owner_employee_id
• commission_allocation_per_visit
• gross_commission_allocation
• treatment_fee_rule_id (ถ้ามี)
• substitute_treatment_fee_amount
• owner_net_commission
Case Owner ต้องถูกดึงจาก Course ไม่ควรให้ User เลือกใหม่ทุกครัง้
14. Seller / Case Owner PT vs Treating PT
ต้องแยก Field และสิทธิท์ างการเงินอย่างชัดเจน
14.1 Seller / Case Owner PT
พนักงานที่เป็นเจ้าของคนไข้และเป็นผู้ขาย Course เป็นเจ้าของ Course Commission Pool ที่เกิดจาก Locked Commission
Rate ของเดือนที่ขาย
ตัวอย่าง:
• Course Value = 10,000 บาท
• Total Visits = 10
• Locked Commission Rate = 7%
• Total Course Commission Pool = 700 บาท
• Commission Allocation / Visit = 70 บาท
8\n\n14.2 Treating PT
นักกายภาพที่ให้บริการจริงใน Visit นัน้
• ถ้า Treating PT = Case Owner PT: Release Commission Allocation ของ Visit ให้ Case Owner ตามปกติ
• ถ้า Treating PT != Case Owner PT: ใช้ Substitute Treatment Fee Allocation
15. Substitute Treatment Fee Allocation
กรณี Case Owner PT ไม่สามารถรักษาได้ และมีนักกายภาพคนอ่ืนรักษาแทน เงินค่ามือของผู้รักษาแทนต้องถูกหักจาก
Commission Allocation ของ Course Owner ใน Visit นัน้ ไม่ใช่เป็นค่าใช้จ่ายเพิ่มเติมซ้อนบน Commission เดิม
ตัวอย่าง:
• Course ID = RE‐2206315
• Price = 10,000 บาท
• Visits = 10
• Case Owner = หมอกิฟ๊
• Locked Commission Rate = 7%
• Total Course Commission Pool = 700 บาท
• Commission Allocation / Visit = 70 บาท
Visit #1 ‐ หมอกิฟ๊ รักษาเอง
Treating PT = หมอกิฟ๊
• Gross Commission Allocation = 70 บาท
• Substitute Treatment Fee = 0 บาท
• Owner Net Commission = 70 บาท
Visit #2 ‐ หมอก้องรักษาแทน
Treating PT = หมอก้อง
สมมติ Treatment Fee ของหมอก้อง = 45 บาท / Visit
ระบบต้องทํารายการ:
• Gross Commission Allocation = 70 บาท
• Treatment Fee Transfer ‐> หมอก้อง = 45 บาท
• Owner Net Commission ‐> หมอกิฟ๊ = 25 บาท
เงิน45บาทที่หมอก้องได้รับไม่ใช่CommissionTier7%ของหมอก้องและไม่ทําให้เกิดCommissionใหม่แต่เป็นการTransfer
เงินบางส่วนจาก Commission Allocation ของ Course
สูตร:
Owner Net Commission / Visit = Gross Commission Allocation / Visit ‐ Substitute Treatment Fee
ระบบต้องเก็บ Gross Allocation, Treatment Fee Transfer และ Owner Net Commission แยก Transaction เพ่ือ Audit
15.1 Treatment Fee Configuration
Treatment Fee ต้องเป็น Configurable Setting และรองรับอย่างน้อย 2 วิธี
A. Fixed Amount
ตัวอย่าง: 45 บาท / Visit
9\n\nB. Percentage
ตัวอย่าง: 3% / Visit
สําหรับ Percentage Treatment Fee ระบบต้องมี Calculation Base ที่กําหนดชัดเจน เช่น:
Course Value Per Visit = Course Price / Total Visits
ตัวอย่าง:
• Course = 10,000 บาท / 10 Visits
• Course Value Per Visit = 1,000 บาท
• Treatment Fee = 3%
• Treatment Fee Transfer = 30 บาท
• Commission Allocation / Visit = 70 บาท
• Owner Net Commission = 40 บาท
Treatment Fee Configuration ควรมี Fields:
• treatment_fee_rule_id
• employee_id หรือ employee_group
• service_id / service_group (optional)
• fee_type: FIXED / PERCENTAGE
• fee_value
• percentage_base: COURSE_VALUE_PER_VISIT / OTHER_CONFIGURED_BASE
• effective_from
• effective_to
• active
Treatment Fee Rule ต้องมี Version / Effective Date เพ่ือให้ Visit เก่าย้อนหลังยังคํานวณตามกติกาที่ใช้ ณ วันที่ให้บริการ
15.2 Financial Allocation Rule
เม่อื Course ถูกใช้ 1 Visit ระบบต้องคํานวณตามลําดับ:
1. ดึง Commission Allocation / Visit จาก Course ที่ Freeze ไว้
2. ตรวจ Case Owner PT
3. ตรวจ Treating PT
4. ถ้า Case Owner PT = Treating PT ให้ Release Allocation ทัง้ จํานวนเป็น Owner Net Commission
5. ถ้า Case Owner PT != Treating PT ให้คํานวณ Treatment Fee ของ Treating PT
6. หัก Treatment Fee ออกจาก Gross Commission Allocation ของ Visit
7. จ่าย Treatment Fee ให้ Treating PT
8. Release ส่วนที่เหลือเป็น Owner Net Commission ให้ Case Owner PT
9. บันทึก Gross Allocation, Treatment Fee Transfer และ Owner Net Commission แยกกัน
ระบบต้องไม่เพิ่ม Commission Pool ใหม่จากการเปลี่ยน Treating PT
ยอดเงินรวมที่ Allocate จาก Course ต่อ Visit ต้องไม่เกิน Commission Allocation / Visit เว้นแต่บริษทั ตัง้ Business Rule อ่ืน
ไว้โดยเฉพาะ
หาก Treatment Fee > Commission Allocation / Visit ระบบต้องใช้ Configurable Policy:
CAP_AT_COMMISSION
• : จ่าย Treatment Fee สูงสุดไม่เกิน Gross Commission Allocation ของ Visit
COMPANY_TOP_UP
• : บริษทั จ่ายส่วนต่างเพิ่ม
BLOCK_AND_REQUIRE_APPROVAL
• : ไม่อนุญาตให้ Complete Visit จนได้รับอนุมัติ
Default Recommendation: CAP_AT_COMMISSION
16. Shared Course
ระบบต้องรองรับ 1 Course มีผู้ใช้มากกว่า 1 Patient
10\n\nตัวอย่าง:
• A ซ้ือ Course 10 Visits / 10,000 บาท
• Seller / Case Owner = หมอกิฟ๊
• B แชร์ Course กับ A
ต้องใช้ Course ID เดิม ไม่สร้างยอดขายใหม่ ไม่สร้าง Commission ใหม่
A และ B ใช้ Visit จาก Balance เดียวกัน เช่น A ใช้ 6 และ B ใช้ 4 รวม 10 Visits แล้ว Course = Completed
17. Shared Course Member Table
แนะนําให้มี:
• course_id
• patient_hn
• patient_name
• role
• status
Role:
• Owner
• Shared Member
หรือเก็บ Owner ใน Course Table และ Shared Member ใน Mapping Table
18. Shared Course + Different Therapist
ตัวอย่าง:
A ซ้ือ Course กับหมอกิฟ๊ , B แชร์ Course และ B รักษากับหมอก้อง
ระบบต้องคํานวณ:
• Gross Commission Allocation ‐> มาจาก Course ของหมอกิฟ๊ ตาม Locked Rate เดิม
• Substitute Treatment Fee ‐> หมอก้อง ตาม Treatment Fee Rule
• Owner Net Commission ‐> หมอกิฟ๊ หลังหัก Treatment Fee
ไม่สร้าง Sales Commission ใหม่ให้หมอก้อง
19. Treatment Fee
Treatment Fee ต้องผูกกับ Treating PT และตัง้ ค่าได้ตาม Employee / Role / Service ตาม Business Rule ของบริษทั
Treatment Fee ไม่ถือเป็น Commission Tier ของ Treating PT
ตัวอย่างการตัง้ ค่า:
Employee Fee Type Fee Value Calculation Base
หมอกิฟ๊ FIXED 45 บาท Per Visit
หมอก้อง PERCENTAGE 3% Course Value / Visit
หมอแนน FIXED 60 บาท Per Visit
เม่อื Visit Completed ระบบต้องบันทึกอย่างน้อย:
• case_owner_employee_id
• treating_employee_id
• gross_commission_allocation
11\n\n• treatment_fee_rule_id
• treatment_fee_amount
• owner_net_commission
• allocation_date
• visit_id
• course_id
หาก Treating PT = Case Owner PT ระบบไม่จําเป็นต้องสร้าง Treatment Fee Transfer แยก เว้นแต่นโยบายบริษทั กําหนดให้
มีค่ามือเพิ่มเติม
20. Employee Has No Sale in Current Month
ถ้า September หมอกิฟ๊ ไม่มี Course Sale ใหม่ ไม่จําเป็นต้องสร้าง Commission Tier ใหม่
แต่หากคนไข้จาก August มาใช้ Course ระบบยังต้อง Allocate Commission จาก Original Course Rate ได้ตามปกติ
ตัวอย่าง August Commission Allocation / Visit = 70 บาท และ September ใช้ 3 Visits:
• Gross Commission Allocated = 210 บาท
• การแบ่ง Owner Net / Treatment Fee ให้พิจารณาตาม Treating PT ของแต่ละ Visit
21. Employee Resignation
ระบบต้องรองรับสถานะพนักงานลาออก และมี Configurable Policy เช่น:
Option A
Outstanding Commission ถูก Forfeit หลังวันสิน้ สุดการทํางาน
Option B
ยังจ่าย Outstanding Commission ต่อจน Course หมด
Fields:
• termination_date
• commission_after_termination_policy
22. Refund / Cancellation
หาก Course ถูก Cancel / Refund ระบบต้อง:
1. หยุด Commission Allocation ใหม่
2. คํานวณ Gross Commission ที่ Allocate ไปแล้ว
3. คํานวณ Owner Net Commission และ Treatment Fee ที่เกิดแล้ว
4. คํานวณ Commission Adjustment ตาม Policy
5. บันทึกเหตุผล
6. สร้าง Audit Trail
ห้ามลบ Transaction เดิม ควรใช้ Adjustment Transaction
23. Commission Tier Configuration
Tier ไม่ควร Hard‐code ใน Program
ต้องมีหน้า Commission Tier Settings และ Configuration Table
12\n\nตัวอย่าง:
tier minimum_sales maximum_sales rate active
1 0 59,999 0.05 Yes
2 60,000 69,999 0.06 Yes
3 70,000 79,999 0.07 Yes
4 80,000 89,999 0.08 Yes
5 90,000 NULL 0.10 Yes
Admin ต้องแก้ได้ทัง้ Monthly Course Sales Range และ Commission Rate
Recommended Fields:
• commission_scheme_id
• tier_id
• tier_order
• minimum_monthly_sales
• maximum_monthly_sales
• commission_rate
• active
• effective_from
• effective_to
• created_by
• created_at
• updated_by
• updated_at
maximum_monthly_sales = NULL
ใช้แทน Tier สูงสุดที่ไม่มีเพดาน
การแก้ Tier Configuration ต้องไม่ทําให้ Course หรือ Monthly Closing ที่ Closed ไปแล้วเปลี่ยน Rate ย้อนหลัง
24. Tier Versioning
Commission Scheme ต้องมี Version
ตัวอย่าง:
• Commission Scheme V1: Effective 01/01/2026 ‐ 31/12/2026
• Commission Scheme V2: Effective ตัง้ แต่ 01/01/2027
เม่อื Admin เปลี่ยน Monthly Course Sales Range หรือ Commission Rate ระบบควรสร้าง Scheme Version ใหม่แทนการแก้
ประวัติเดิม
Monthly Closing ต้องเลือก Scheme Version ตาม Effective Date และเม่ือปิดเดือนแล้วต้อง Freeze:
• commission_scheme_id
• commission_scheme_version
• locked_commission_rate
25. Audit Requirement
Admin ต้องเปิด Course ใดก็ได้และตอบได้ว่า:
• ใครเป็นคนขาย / Case Owner
• ขายวันที่เท่าไร
• ราคาเท่าไร
• เดือนนัน้ Seller ขายรวมเท่าไร
• ทําไมได้ Tier นี้
• Scheme Version ใด
13\n\n• Total Commission Pool เท่าไร
• Commission Allocation / Visit เท่าไร
• ใช้แล้วกี่ Visit
• ใครเป็นคนใช้
• ใครเป็น Treating PT แต่ละครัง้
• Gross Commission Allocated เท่าไร
• Substitute Treatment Fee เท่าไร
• Owner Net Commission เท่าไร
• Commission Outstanding เท่าไร
ตัวอย่าง Audit:
• Course: RE‐2206315
• Seller / Case Owner: หมอกิฟ๊
• Sale Date: 18/08/2026
• Monthly Sales: 75,000 บาท
• Tier: 7%
• Course Value: 10,000 บาท
• Total Commission Pool: 700 บาท
• Course Visits: 10
• Commission Allocation / Visit: 70 บาท
• Used: 4
• Gross Allocated: 280 บาท
• Outstanding Pool: 420 บาท
26. Recommended Dashboard
26.1 Sales
• Course Sales This Month
• Number of Courses Sold
• Commission Tier
• Commission Generated
26.2 Commission
• Gross Commission Allocated This Month
• Owner Net Commission Released This Month
• Outstanding Commission Pool
• Commission Adjustment
26.3 Treatment
• Number of Visits Treated
• Substitute Treatment Fee Earned
26.4 Total Variable Pay
สําหรับพนักงานแต่ละคน:
Owner Net Commission + Treatment Fee Earned + Other Bonus = Total Variable Pay
ต้องหลีกเลี่ยงการรวม Gross Commission Allocation กับ Treatment Fee ซ้ํา เพราะ Treatment Fee ในกรณีรักษาแทนถูกหัก
มาจาก Gross Allocation เดิม
27. Important Business Logic
ระบบต้องไม่คํานวณ Commission จาก Current Tier ตอน Visit
14\n\nผิด: คนไข้มาเดือน November ‐> ดูว่า Seller เดือน November อยู่ Tier ไหน
ถูก: คนไข้มาเดือน November ‐> เปิด Course ‐> ดึง Locked Rate และ Commission Allocation / Visit จากเดือนที่ Course
ถูกขาย
course.commission_allocation_per_visit employee.current_commission_rate
ดังนัน้ VisitCalculationต้องใช้ ไม่ใช่
28. Simplified System Flow
Course Sold
‐> เก็บ Seller / Case Owner + Price + Visits
‐> รวมยอด Seller ภายในเดือน
‐> Monthly Closing
‐> Determine Tier จาก Configurable Scheme
‐> Freeze Commission Rate
‐> Calculate Total Commission Pool
‐> Calculate Commission Allocation / Visit
‐> Patient Uses Course
‐> Identify Course ID
‐> Identify Case Owner + Treating PT
‐> Gross Commission Allocation ถูกตัดจาก Course Pool
‐> ถ้ารักษาเอง: Owner Net = Gross Allocation
‐> ถ้ารักษาแทน: Calculate Treatment Fee ‐> Transfer to Treating PT ‐> Owner Net = Gross ‐ Fee
‐> Update Used Visits / Remaining Visits / Outstanding Pool
‐> Write Allocation Ledger
29. Acceptance Criteria
Case 1 ‐ Original Tier Must Persist
หมอ A ขาย Course เดือน August ที่ Tier 7%, เดือน September อยู่ Tier 10%, คนไข้ใช้ Course August ใน September
Expected: ใช้ Rate 7%
Case 2 ‐ No New Sale, Old Course Still Releases
หมอ A ไม่มี Sale ใหม่ใน September แต่คนไข้ August ใช้ Course
Expected: Gross Commission Allocation ยังเกิดตาม Course เดิม
Case 3 ‐ Substitute PT Fixed Fee
• Case Owner A: Gross Allocation = 70 บาท / Visit
• Treating PT B: Treatment Fee = 45 บาท / Visit
Expected:
• Gross Commission Allocation = 70
• Treatment Fee Transfer ‐> B = 45
• Owner Net Commission ‐> A = 25
• ไม่มี Tier Commission ใหม่เกิดให้ B
• Commission Pool ถูกตัดรวม 70 บาท
Case 4 ‐ Shared Course
Patient B แชร์ Course ของ Patient A
Expected: ใช้ Balance Course เดียวกัน, ไม่มี Sale เพิ่ม, ไม่มี Commission Generated ใหม่
15\n\nCase 5 ‐ Course Usage Limit
A ใช้ 6 Visits และ B ใช้ 4 Visits จาก Course 10 Visits
Expected: Course Completed, Remaining = 0, ห้าม Allocate Commission / Treatment Fee จาก Visit ครัง้ ที่ 11
Case 6 ‐ Historical Audit
Admin เปิด Course เก่าจาก 6 เดือนก่อน
Expected: แสดง Original Sale Month, Scheme Version, Original Tier, Commission Allocation / Visit, Treating PT และ
Allocation Ledger ได้
Case 7 ‐ Admin Changes Tier Configuration
Admin เปลี่ยน 60,000 ‐ 69,999 = 6% เป็น 60,000 ‐ 79,999 = 7%
Expected:
• บันทึก Configuration ใหม่ได้โดยไม่แก้ Source Code
• ระบบสร้าง/อ้างอิง Scheme Version ใหม่
• เดือนที่ยังไม่ Closed และอยู่ใน Effective Date ใหม่ใช้ Tier ใหม่
• Course / Monthly Closing ที่ Closed แล้วคง Rate เดิม
Case 8 ‐ Invalid Tier Range
Admin ตัง้ :
• 0 ‐ 59,999 = 5%
• 50,000 ‐ 79,999 = 7%
Expected: ห้าม Save เพราะ Range ซ้อนกัน
Case 9 ‐ Substitute PT Fixed Treatment Fee
• Gross Allocation = 70 บาท
• Fixed Treatment Fee = 45 บาท
Expected: B ได้ 45, A ได้ 25, Pool ถูกตัด 70
Case 10 ‐ Substitute PT Percentage Treatment Fee
• Course Value / Visit = 1,000 บาท
• Gross Allocation = 70 บาท
• Treatment Fee = 3% ของ Course Value / Visit
Expected: Treatment Fee = 30, B ได้ 30, A ได้ 40, Pool ถูกตัด 70
Case 11 ‐ Treatment Fee Exceeds Allocation
• Gross Allocation = 40 บาท
• Treatment Fee Rule = 60 บาท
Expected: ใช้ Overflow Policy ที่ตัง้ ไว้ และห้าม Owner Net ติดลบ
30. Key Development Principle
หน่ึง Course ต้องเป็น Single Source of Truth ของ Commission
ข้อมูลที่ต้อง Freeze หลัง Monthly Closing:
16\n\n• Seller / Case Owner PT
• Sale Month
• Course Price
• Total Visits
• Commission Scheme
• Commission Scheme Version
• Locked Commission Rate
• Total Course Commission Pool
• Commission Allocation / Visit
หลังจากนัน้ ทุก Visit อ้างอิง Course ID เพ่ือดึงข้อมูล Commission เดิม ไม่ควรให้ Admin หรือ Finance จําว่า Course เคยอยู่
Tier กี่เปอร์เซ็นต์
ทุก Visit ต้องมี Allocation Ledger เพ่อื บันทึกว่า Gross Commission Allocation ถูกแบ่งให้ Case Owner และ Treating PT
อย่างไร โดยยอดที่จ่ายให้ Treating PT จากการรักษาแทนต้องไม่ถูกตีความเป็น Commission Tier ของ Treating PT
Appendix A ‐ Recommended Allocation Ledger Fields
• allocation_id
• visit_id
• course_id
• patient_hn
• case_owner_employee_id
• treating_employee_id
• visit_date
• gross_commission_allocation
• treatment_fee_rule_id
• treatment_fee_type
• treatment_fee_rate_or_amount
• treatment_fee_calculation_base
• treatment_fee_amount
• owner_net_commission
• company_top_up_amount (if applicable)
• overflow_policy_used
• created_at
• created_by
• adjustment_reference_id (if applicable)
Appendix B ‐ Recommended Admin Settings
ระบบควรมีหน้า Settings อย่างน้อย:
1. Commission Tier Settings
2. Commission Scheme Version / Effective Date
3. Treatment Fee Rule Settings
4. Treatment Fee Percentage Base Settings
5. Treatment Fee Overflow Policy
6. Employee / Employee Group Assignment
7. Commission After Termination Policy
8. Permission / Admin Override
9. Audit Log
Appendix C ‐ Key Terms
17\n\nTerm Meaning
Case Owner / Seller PT ผู้เป็นเจ้าของคนไข้/ผู้ขาย Course และเป็นเจ้าของ Commission Pool
Treating PT ผู้ให้การรักษาจริงใน Visit นัน้
Commission Tier % Commission ที่ได้จากยอดขายรวมรายเดือน
Locked Commission Rate Rate ที่ Freeze หลังปิดเดือน
Course Commission Pool Commission รวมทัง้ Course
Gross Commission Allocation เงินจาก Pool ที่ถูกตัดตาม Visit ก่อนหักค่ามือผู้รักษาแทน
Substitute Treatment Fee ค่ามือที่โอนให้ Treating PT เม่ือไม่ใช่ Case Owner
Owner Net Commission เงินที่ Case Owner ได้จริงหลังหัก Substitute Treatment Fee
Commission Outstanding Commission Pool ที่ยังไม่ถูก Allocate เพราะ Course ยังเหลือ Visit
Allocation Ledger ประวัติการแบ่งเงินของแต่ละ Visit เพ่ือ Audit
18