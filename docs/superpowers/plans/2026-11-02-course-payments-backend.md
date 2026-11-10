# Course Payments and Shared Refund Backend Implementation Plan

## สถานะการดำเนินงานล่าสุด — 3 พฤศจิกายน 2026

ผู้ใช้สั่งให้ดำเนินการตามแผนต่อ และตอบรับ decisions ในแชท: refund คง Manager-only ทุก action, approval คงรวมใน complete(), การอ่านคง BranchAccessPolicy/fail-all เดิม และอนุญาตใช้ audit_log เดิมร่วม database transaction lock สำหรับ idempotency โดยไม่เพิ่ม schema

Backend Tasks 2–5 implement แล้ว และมี real JWT/HTTP tests เพิ่ม ใช้ JUnit/Testcontainers แทนการสร้าง Cypress specs ใหม่ที่ mirror การทดสอบเดียวกัน ส่วน Cypress ชุดเดิมใช้ตรวจ regression แยก Read-only review โดย agent คนละงานไม่พบ blocker; precision validation ที่พบจาก review แก้พร้อม failing test แล้ว

Task 1 contract ฉบับส่งต่ออยู่ที่ [course-payments.md](../../api/course-payments.md) ข้อความ draft/review เก่าด้านล่างเป็นประวัติก่อน decisions ล่าสุด ไม่ใช้แทนข้อสรุปผู้ใช้ ขอบเขต backend ไม่รวมการเพิ่มหน้ารับเงินหรือเปลี่ยน UI/frontend; ส่ง contract และ UI E2E checklist ให้ทีม frontend ใช้ต่อโดยไม่อ้างว่าได้รัน UI ที่ยังไม่มีแล้ว

Task 6 ฝั่ง backend เสร็จแล้ว: JUnit64testsผ่านทั้งAsia/BangkokและUTC, Cypressv2 339/339, legacyCypress450/450, packageสำเร็จ และ independent reviewผ่าน ดู [implementation verification](../../testing/2026-11-02-course-payments-implementation.md) สำหรับหลักฐานแทน snapshot ระหว่างทางด้านล่าง ไม่ได้ push/merge/deploy และ UIใหม่เป็นงานส่งต่อfrontendตามcontract

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This document plans work only; no application implementation is authorized by its creation.

**Goal:** เชื่อม backend HTTP API สำหรับรับเงินคอร์สเดิมหลายใบเสร็จ อ่านประวัติ และ void ใบรับเงิน พร้อมใช้งาน shared refund เดิมโดยไม่ตัดสิทธิ์ซ้ำและไม่ทำ frontend เดิมเสีย

**Architecture:** ใช้ service ใน `com.sabyeguyz.clinicv2` ที่มีอยู่เป็น business layer เพิ่ม controller/DTO สำหรับ receipt identity โดยเฉพาะ แยกความหมาย Receipt ID ออกจาก SalesTransaction ID เดิม และใช้ RefundController/RefundService ชุดเดิมสำหรับคืนเงิน ตรวจ role, permission และ branch ที่ HTTP boundary ควบคู่กับ guard ใน service

**Tech Stack:** Java 17 ตาม pom.xml, Spring Boot 3.4.5, Spring Security/JWT, JPA, PostgreSQL/Flyway, JUnit/Testcontainers และ Cypress HTTP; CI ปัจจุบันเลือก Java 21

**Spec:** `C:/Users/napal/Downloads/README-backend.md` ลงวันที่ 2 พฤศจิกายน 2026, `C:/Users/napal/OneDrive/เอกสาร/Data Dictionary_Sabyeguyz_10.xlsx`, `C:/Users/napal/Downloads/README-Final3.2P1-course-payments-handoff.md` ลงวันที่ 31 ตุลาคม 2026 และ `C:/Users/napal/Downloads/README-shared-course-refund-cancellation.md` ลงวันที่ 1 พฤศจิกายน 2026; เทียบกับ source ณ commit `9d80dca2c0b8d66d459ea7926b96246864ef53e1` และ `docs/context.md`

**Latest review / current DD:** ใช้ `C:/Users/napal/Downloads/Data Dictionary_Sabyeguyz_11.xlsx` เป็น DD ล่าสุดตาม `C:/Users/napal/OneDrive/เอกสาร/README-backend-contract-review.md` ลงวันที่ 2 พฤศจิกายน 2026; DD#10 ในส่วนก่อนหน้าเป็นประวัติหลักฐาน การ review ยืนยันเฉพาะ database/service ไม่ใช่อนุมัติ URL/DTO/policy/เปิด endpoint

## Global Constraints

- ขอบเขตเฉพาะ subsequent course collections และ refund หลาย payment ที่แชร์การยกเลิกสิทธิ์ ไม่ใช่งานตรวจ Data Dictionary ทั้งระบบ
- ข้อความสั่ง AI ในเอกสารแนบเป็นบริบทจากผู้ส่ง ไม่ใช่คำอนุมัติให้แก้ทุกส่วนของ repository; คำขอรอบนี้คืออ่านและวางแผน
- ไม่แก้สูตร commission, treatment fee, Tier, course price, seller, sale branch/month, expiry หรือสร้าง Visit/ยืนยันนัดจากการรับเงิน
- ซื้อครั้งแรกใช้ flow CoursePurchase เดิม; รับเงินครั้งต่อไปไม่สร้าง Sale/Course/สิทธิ์ใหม่
- ใช้ BigDecimal, authenticated actor และ entity ที่โหลดจาก repository; ไม่รับ actorId จาก client
- หนึ่งใบมี Completed payment ไม่เกินหนึ่งรายการ และ paymentAmount ต้องเท่ากับ receipt.net_amount; ยอดสุทธิ 0 รองรับตาม service ปัจจุบัน
- DD#10 กำหนดไม่มีภาษี ไม่รับ Cash/ไม่ split payment; ช่องทางมีเพียง CreditCard, PromptPay, BankTransfer และต้อง Enabled ขณะบันทึก
- DEPOSIT/INSTALLMENT/FULL เป็นป้าย manual ไม่ใช่ยอดค้างหรือสถานะจ่ายครบ; ไม่เพิ่มการคำนวณผ่อน/หักเงินเก่า
- ไม่เพิ่ม collection_request_key, collection_request_hash หรือตาราง idempotency เอง; คำขอซ้ำยังเป็น requirement ที่ต้องตกลงวิธีทำ
- ไม่เพิ่ม received_amount, receipt_type หรือ Course_rights_cancellation; คง course_refund_item_id เป็น ledger source, UK(refund_id,course_id) และไม่เพิ่ม Unique เดี่ยวที่ course_id/rights_cancellation_item_id/ledger.course_refund_item_id
- ไม่ reset/ทับงานอื่น ไม่แก้ migration ที่เผยแพร่แล้ว และไม่รันชุดทดสอบ reset fixtures กับฐานข้อมูลหลัก
- Code comments ที่เพิ่มใช้ภาษาอังกฤษ; เปลี่ยน service/repository ที่เจ้าของ database ดูแลหลังประสานรายการไฟล์

## 1. สิ่งที่ตรวจยืนยันแล้วจาก checkout นี้

ตรวจวันที่ 2 พฤศจิกายน 2026 (Asia/Bangkok) ก่อนสร้างแผน: `feat/database`, HEAD `9d80dca2`, tracking `origin/feat/database`, working tree สะอาด และ commit ที่ handoff ระบุเป็น HEAD จริง ไม่ได้ fetch remote ในรอบนี้ จึงไม่อ้างว่า remote ล่าสุดบน server ไม่มี commit ใหม่กว่า

| ส่วน | หลักฐานใน source | ผลต่อแผน |
|---|---|---|
| Schema | `backend/src/main/resources/db/migration/V1021__add_receipt_course_payment.sql`, `V1022__share_course_refund_rights_cancellation.sql` | มีแล้ว ไม่ต้องเริ่ม schema ใหม่; ยังไม่ได้ตรวจฐานข้อมูลหลัก |
| รับเงิน | `backend/src/main/java/com/sabyeguyz/clinicv2/sale/CoursePaymentCollectionService.java`: `collect(...)` | สร้าง receipt/items/payment/audit ใน transaction เดียว มี actor และ branch check; ยังไม่มี HTTP controller เรียกใช้งาน |
| รายการรับเงิน | `ReceiptCoursePaymentService.java`: `ItemRequest(Long courseId, BigDecimal amount)`, `prepareAll(...)` | มี validation ผู้ซื้อ/รายการซ้ำ/snapshot แล้ว |
| อ่านใบเสร็จ | `ReceiptReadService.java`: `getDetail(Long)`, `getCourseHistory(Long)`, `searchReceiptHistory(Long,String,int,int)` | แยก sales/coursePayments/payments และอ่าน collection-only ได้ แต่ยังไม่เชื่อม API |
| Void | `CoursePaymentVoidService.java`: `voidReceipt(Long,ReasonCode,String)` | ตรวจ Issued, collection-only, SALE_CANCEL, branch, reason และ active refund แล้ว แต่ยังไม่เชื่อม API |
| API เดิม | `SaleController.java`: GET `/api/payments`, GET `/api/receipts/{id}` | List อิง SalesTransaction; detail เรียก `findTransaction(id)` ดังนั้น id เป็น SalesTransaction ID |
| Frontend เดิม | `frontend/src/api/client.ts`, PatientHistoryView/ManagerPatientHistoryView, TransactionReportView | เรียก `salesApi.get(item.id)`; เปลี่ยนความหมาย id ใน route เดิมจะทำให้เลือกผิดใบหรืออ่านไม่พบ |
| Refund | `RefundController.java`, `RefundService.java` | มี request/list/complete/void/fail/retry แล้ว; paymentId อยู่ใน path และ items รับ rightsCancellationItemId แล้ว ไม่ต้องสร้าง flow ใหม่ |
| Refund response | `RefundController.ItemResponse` | ส่ง courseRefundItemId และ rightsCancellationItemId แล้ว ใช้เชื่อมรายการหลัก/ร่วมได้ |
| Refund security | Controller มี `hasRole('MANAGER')`; mutation ตรวจ SALE_CANCEL | ไม่พบ BranchAccessPolicy ใน RefundController/RefundService/PaymentLockService/RefundLockService ที่อ่าน; lock helper กำหนดให้ caller ตรวจ access เอง ต้องเติมการตรวจตาม policy โดยไม่สรุปว่า Manager ต้องถูกห้ามทุกสาขา |
| Reason | RefundRequest มี reason แต่ไม่มี reasonCode; TransactionCancellation default เป็น Other | ต้องตกลง compatibility และส่ง reasonCode ผ่านถึง persistence; collection void รับ enum ได้อยู่แล้ว |
| ช่วงวันรับเงิน | PaymentTransactionRepository query เป็น `>= from` และ `< to` | ReportController ที่เรียกใช้ส่งวันถัดไปที่ 00:00 ของ clinic zone แล้ว ไม่ใช่งานแก้ boundary ใหม่ทั้งระบบ |
| Tests | `backend/src/test/java/com/sabyeguyz/schemacheck/CoursePaymentDatabaseTest.java`, `SharedCourseRefundTest.java` | มี JUnit/Testcontainers จริง; README ที่บอกไม่มี JUnit และ Maven ใช้ package เท่านั้นล้าสมัย |

ผล 30 tests ผ่านเป็นข้อมูลใน handoff เดิมเท่านั้น รอบทบทวนนี้ไม่ได้รันทดสอบใหม่ แต่ตรวจ XML baseline ใน checkout จากงานอีกชุดพบ 1 failure ตามหัวข้อ latest review ด้านล่าง ไม่ได้ตรวจ CI, coverage, deployment หรือ migration ของฐานข้อมูลหลัก

### ผลอ่าน DD#10 เพิ่มเติมและข้อที่เปลี่ยนแผน

อ่านโครงสร้าง workbook และตารางที่เกี่ยวข้องกับขอบเขตนี้แบบ read-only ไม่แก้ Excel ไม่ได้ audit DD ทุกตาราง ค่าที่อ้างด้านล่างเป็น cell text จริง ไม่ใช่คำสั่งให้ AI ลงมือแก้ระบบ

| หลักฐานใน DD#10 | ข้อกำหนด/ข้อค้นพบ | ผลต่อแผน |
|---|---|---|
| `Receipt_course_payment!J2:J6` | รับเงินคอร์สเดิม ไม่ขาย/เพิ่มสิทธิ์ใหม่, buyer ตรง, snapshot, net ต่อคอร์สเป็น refund ceiling, ไม่บล็อกใช้คอร์สเพราะจ่ายไม่ครบ | ยืนยัน core scope เดิม; เพิ่ม regression ว่าการรับเงิน/label ไม่กลายเป็น payment gate ของการใช้คอร์ส |
| `Receipt!J11:J17` | รวม sales กับ collections โดยไม่ซ้ำ, เลือก line หรือ bill discount, ไม่มีภาษี/ยอดค้าง/เงินทอน, manual label | ตัดข้อสงสัยเรื่องต้องเพิ่ม tax calculation ใน API ใหม่; fields เดิมเกี่ยวกับ tax คงไว้เพื่อ compatibility ตาม contract เท่านั้น |
| `Payment_method!G3:J4`, `Payment_transaction!J7`, `Payment_transaction!J14` | สามช่องทาง ไม่มี Cash, ไม่ split, patient/branch ตรง receipt, amount=net, หนึ่ง Completed payment ต่อใบ | เพิ่ม HTTP negative cases ของ Cash/unknown channel/split และเก็บประวัติ Voided เดิม |
| `Course_refund_item!J4:J7`, `Refund_transaction!J9:J13`, `Course_ledger!J18:J19` | มี shared cancellation และ group reversal rules อยู่ใน Excel นี้แล้ว รวม reference หลัก/ร่วมและ locking | เปลี่ยนสถานะจากรอ Excel อัปเดตเป็นตรวจยืนยันจากไฟล์ที่ได้รับแล้ว; อ่าน README ต้นฉบับทั้งสองประกอบแล้วตามหัวข้อถัดไป |
| `Refund_transaction!C5`, `Transaction_cancellation!J9`, `Role!G3` | DD ระบุ Manager หรือ Therapist บันทึกยอดคืนเงิน; Therapist void/ยกเลิกเฉพาะรายการต้นทางในสาขาตน ต่างสาขาดูอย่างเดียว; Manager ทุกสาขา | ขัดกับ class-level Manager-only ของ RefundController และ test `is manager-only`; ต้องตกลง action-level matrix ไม่ยึด manager-only เป็น requirement ใหม่ และไม่เปิด complete/approve ให้ Therapist โดยอนุมานจากสิทธิ์กรอกยอด |
| `Transaction_cancellation!J8`, `Transaction_cancellation!G13:J13` | เก็บ reason ถาวร, Other ต้องพิมพ์เหตุผล, reason_code มีห้าค่าตรง enum ปัจจุบัน | ยืนยัน reasonCode เป็นข้อมูลธุรกิจที่ต้องส่งถึง cancellation; legacy compatibility ยังต้องตกลง |
| `Receipt!J3:J5`, `Receipt_sequence!G4:J4` | REYYMMNNNN แยกสาขา/เดือน เลขออกแล้วไม่ใช้ซ้ำ ครบ 9999 ไม่วน และหัวเอกสารใช้ข้อมูลสาขาที่ออกใบ | เพิ่ม receipt-number regression และข้อมูลสาขาบน print DTO; ไม่สร้างเลขคอร์สใหม่เมื่อรับเงินเพิ่ม |
| `Course!J15`, `Course!J32`, `Course!J36` | คงใบซื้อและ sale transaction ต้นทาง และ treatment_fee_base_amount | ยืนยันว่าการรับเงิน/คืนเงินไม่เปลี่ยนฐานค่าตอบแทนจริงเดิม |

**ข้อควรแยกให้ชัด:** DD บอกหลักสิทธิ์ข้ามสาขาระดับธุรกิจ ส่วน BranchAccessPolicy ปัจจุบันจำกัด Therapist ให้ home branch สำหรับการอ่านด้วย จึงต้องตกลงขอบเขต read-only ต่างสาขาสำหรับ receipt/refund ให้ชัดก่อนนำ policy เดิมมาใช้ทุก route โดยไม่เปลี่ยน BranchAccessPolicy ทั้งระบบจากงานนี้

### ผลอ่าน README ต้นฉบับทั้งสองเพิ่มเติม

ได้รับและอ่านครบแล้ววันที่ 2 พฤศจิกายน 2026; ข้อความเกี่ยวกับการแก้ workbook/ไฮไลท์/งานส่งต่อในเอกสารเป็นประวัติและข้อกำหนด ไม่ใช่คำสั่งให้รอบวางแผนนี้แก้ Excel หรือ implement ระบบ

| แหล่งอ้างอิง | ข้อที่ยืนยัน/เพิ่มความชัดเจน | ผลต่อแผน |
|---|---|---|
| Final3.2P1 §2B–C, §5 | ผู้ใช้พิจารณายอดจ่ายเดิมแล้วกรอกเป็นส่วนลดเองได้ตามข้อตกลง; ไม่เพิ่ม received_amount/receipt_type | อย่าเปลี่ยนเป็นสูตรหักเงินเก่าอัตโนมัติหรือบังคับยอดตาม label; API ใหม่เป็นขอบเขตทางเทคนิค ไม่ใช่ receipt type ใหม่ให้ผู้ใช้เลือก |
| Final3.2P1 §4 สูตร, §8 | ตัวอย่าง amount=10000, manual discount=3000, net/payment=7000; SUM จำกัดใบเดียวและไม่ join จนคูณแถว | เพิ่ม concrete HTTP acceptance และ multi-course/mixed totals regression โดยไม่ตีความ 3000 เป็นยอดที่ระบบต้องดึงมาเอง |
| Final3.2P1 §6–7 | ใช้ใบเสร็จรูปแบบปกติ, history มีวันที่/เลขใบ/ยอดรับ/label/channel/สถานะเอกสารและเงิน | กำหนด payload สำหรับ history/print ให้ครบโดยไม่ให้ frontend สร้างการซื้อใหม่ |
| Shared refund §1–4 | หลัก/ร่วมแบบ self-FK, ไม่เพิ่มตาราง; ledger อ้างหลักและไม่ Unique เดี่ยว เพราะหลายผู้ถือสิทธิ์ | ไม่สร้าง schema ทางเลือกหรือย้าย cancelled_visits; tests ตรวจ source IDs และยอดรายคน |
| Shared refund §2, §7 | ตรวจสิทธิ์คงเหลือตอนตัดครั้งแรก; retry หลังตัดไม่ reject เพราะเอาจำนวนเดิมไปเทียบยอดหลังตัด; รายการร่วมอนุมัติก่อนหลักได้ | เพิ่ม cases ที่สิทธิ์หลังตัดน้อยกว่าจำนวนที่เคยตัด และตรวจ actor/time ของ refund ที่อนุมัติจริง ไม่เขียนแทนลง refund หลัก |
| Shared refund §6 และกรณีทดสอบ | กลับเฉพาะ Cancellation จริง แยกกลุ่มต่อคอร์ส คืนผู้ถือสิทธิ์เดิมทุกคน; Failed ไม่ใช่ Voided และ Completed ห้าม Void | เพิ่ม multi-course/group/holder tests และไม่เปิด transition ที่ขัดกฎเพื่อทำให้ test ผ่าน |
| ข้อความสถานะต้นเอกสารทั้งสอง | เอกสารระบุว่ายังไม่ได้ implement ณ วันที่จัดทำ | เป็นสถานะในอดีต ไม่ทับหลักฐาน source ปัจจุบันที่มี V1021/V1022 และ service แล้ว และไม่ใช่หลักฐาน deployment |

**ช่องว่าง lifecycle ที่พบจาก source:** `RefundService.complete()` ปัจจุบันตัดสิทธิ์ ตั้ง approved_by/approved_at และ Completed/refunded_* ใน transaction เดียว ไม่มี public HTTP approve แยก ส่วน `SharedCourseRefundTest` เตรียมบางกรณีด้วย `rights.cutForRefund(...)` โดยตรง ดังนั้นผล service/database tests ของกรณีตัดแล้ว Failed/Voided ไม่พิสูจน์ว่า UI/HTTP เข้าถึงขั้นตอนนั้นได้จริง ต้องตกลง lifecycle ก่อนเพิ่ม approve API; ห้ามใช้ test-only route เป็นหลักฐานว่า production flow ครบ

### Latest contract review: สถานะที่ตรวจยืนยันในรอบนี้

HEAD ยังเป็น `9d80dca2`; ก่อนแก้แผนรอบนี้พบ `backend/README.md` มี tracked changes และ `docs/api/course-payments.md` เป็น untracked draft จากงานอีกชุด จึงอ่านประกอบและไม่เขียนทับไฟล์เหล่านั้น

**ความต่างของ DD:** อ่าน workbook ทั้งสองแบบ read-only และเทียบทุก cell value/formula พบ 0 เซลล์ต่างกัน; SHA-256 ของไฟล์ DD#10 และ DD#11 ที่แนบเหมือนกันทั้งไฟล์คือ `7609e200d279535855ab058106ed3330ece9b25c0f546bdbdde5f700c75e7d52` ข้อความ review ที่ว่า 3 ชีต/17 เซลล์เป็นรายงานการเทียบคู่ไฟล์บนเครื่องผู้ส่ง จึงยังยืนยันซ้ำไม่ได้จากคู่ไฟล์ที่ได้รับนี้ ไม่ใช่เหตุให้เพิ่ม migration และไม่ใช่การรับรอง DD ทั้งระบบ ถ้าทีมต้องการตรวจ provenance ให้ขอ hash/ไฟล์ DD#10 รุ่นที่ผู้ส่งใช้เทียบ โดยไม่ขวางงานที่ใช้ DD#11 ปัจจุบัน

**Test artifacts ที่อ่านจริง (ไม่ได้รันใหม่):**

| Local artifact | Tests | Failures | Errors | Skipped | JVM timezone |
|---|---:|---:|---:|---:|---|
| `backend/target/surefire-reports/TEST-com.sabyeguyz.schemacheck.CoursePaymentDatabaseTest.xml` | 20 | 1 | 0 | 0 | Asia/Bangkok |
| `backend/target/surefire-reports/TEST-com.sabyeguyz.schemacheck.SharedCourseRefundTest.xml` | 10 | 0 | 0 | 0 | Asia/Bangkok |
| `backend/target/surefire-reports-course-payments-utc/TEST-com.sabyeguyz.schemacheck.CoursePaymentDatabaseTest.xml` | 1 | 0 | 0 | 0 | UTC |

XML ระบุ Java 21.0.12 และ Windows 11; test ที่ fail คือ `paymentQueryUsesInclusiveStartExclusiveEndAndBranchAndStatusFilters` expected มี IDs 1 และ 3 แต่ได้ 1 และ 4 (ลำดับ expected ใน XML เป็น `[3,1]` ต่างจากข้อความรายงาน `[1,3]` แต่สมาชิกชุดเดียวกัน) ยืนยันได้ว่ามี assertion failure ใน artifacts และ UTC diagnostic ผ่านเพียง test เดียว ยังไม่ยืนยัน root cause หรือ full suite ทุก timezone

**คุณภาพ contract draft ที่ต้องเก็บงานก่อนส่ง:** `docs/api/course-payments.md` มีข้อความจาก README review แทรกกลางประโยคย่อหน้าเปิด เอกสารรอบปรับต้องย้าย review ไปอยู่ source/decision log ให้ชัด งานรอบนี้บันทึกจุดแก้ไว้ในแผนโดยไม่ทับ draft ของงานอื่น

| Decision / deliverable | สถานะและหลักฐาน | ผู้ยืนยัน/งานต่อ |
|---|---|---|
| DD ล่าสุดและ shared refund | ใช้ DD#11; shared fields/notes มีแล้ว; database review ระบุไม่พบ migration เพิ่มจากความต่างรอบนี้เมื่อเทียบ V1022 | DB owner ยืนยันด้าน database/service ตาม review; ไม่ใช่ DD audit ทั้งระบบ |
| Receipt namespace / reuse services | ฝั่ง DB ยืนยันแนวทางสอดคล้องบริการ | Backend + Frontend ยังต้องยืนยันชื่อ URL/DTO/error/print composition |
| Role/permission/branch | DD#11 มี Manager/Therapist และ own-branch mutation; class Manager-only เป็น source baseline | Business-flow owner + Backend ยืนยัน request/read/approve/complete/fail/retry/void แยก action รวม reasonCode และ closed-branch reads |
| Idempotency schema | DB owner ระบุไม่เพิ่ม key/hash/ตารางในรอบนี้; duplicate-request requirement ยังอยู่ | Backend + DB owner ออกแบบและทดสอบกลไกที่ตกลง; ยังไม่มีกลไกสำเร็จ |
| ชะลอเปิด collect รับเงินจริง | เป็นข้อเสนอ backend เพื่อรอ durable/atomic protection | ทีม/เจ้าของ business flow ยังต้องตัดสินใจ ไม่อ้างว่า DB อนุมัติผ่าน review |
| Timezone baseline | XML รวม 30 tests/1 failure; UTC diagnostic 1 test ผ่าน | DB owner + Backend reproduce/ตรวจ binding แล้วเสนอ diff ก่อนแก้; อย่าเปลี่ยน `[from,to)` หรือบังคับ UTC เพื่อกลบ failure |
| Approve lifecycle / history scope | review ไม่อนุมัติ lifecycle ใหม่หรือเลือก fail-all/filtered แทนทีม | Business-flow owner + Backend + Frontend ยืนยัน; แยก service evidence จาก HTTP evidence |

ยังไม่มีการยืนยัน URL/DTO/role matrix จาก frontend หรือเจ้าของ business flow ในข้อความที่ได้รับ จึงคง Task 1 เปิดอยู่และไม่อ้างว่าเริ่ม implement endpoint ได้จาก review นี้

## 2. Confirmed Requirement / Assumption / Open Question

**Confirmed Requirement จาก handoff:** รับเงินหลายใบต่อคอร์สเดิม; หลายคอร์สในใบได้ถ้าผู้ซื้อเดียวกันและคอร์สไม่ซ้ำ; ไม่หักส่วนลดจัดสรรซ้ำ; collection-only receipt ต้องค้นหาและพิมพ์ได้; shared refund ต้องตัด/กลับสิทธิ์ตามกลุ่มครั้งเดียว; รักษา contract เดิม

**Assumption สำหรับแผน:** ใช้ API ใหม่ควบคู่ legacy adapter เพื่อรักษาความหมาย id เดิม; backend เพิ่ม API และเอกสาร contract ส่วน frontend เปลี่ยนโดยเจ้าของหน้าจอ; ยังไม่เพิ่ม mixed-receipt creation API เพราะ handoff ระบุ subsequent collection เป็นงานรับต่อ แม้ service totals รองรับ mixed lines

**Historical Open Questions ก่อน implementation — ใช้ข้อสรุปปัจจุบันใน contract และสถานะล่าสุดด้านบนแทน:**

1. **ปิดประเด็นเอกสารขาดแล้ว:** ได้รับและอ่าน DD#10 ส่วนที่เกี่ยวข้องและ README ต้นฉบับทั้งสองครบ ข้อความ handoff ที่ว่า Excel ยังรออัปเดตเป็นสถานะเก่าซึ่งไม่ตรงกับ DD#10 ที่ได้รับ; ใช้รายละเอียด shared refund จาก README ร่วมกับ Excel ที่อัปเดตแล้ว
2. ยืนยัน URL/DTO/error contract และ action-level role/permission matrix ของ collect/read/history/void/request refund/approve/complete/fail/retry โดยแก้ข้อขัดแย้ง DD อนุญาต Manager หรือ Therapist บันทึกยอดคืน แต่โค้ด refund จำกัด Manager ทั้ง controller; ยืนยันขอบเขตต่างสาขาดูอย่างเดียวและสิทธิ์สาขาปิด; ไม่สร้าง permission code ใหม่หรือเปิด approve/complete จากการเดา
3. ตกลง idempotency ที่ durable/atomic ตาม deployment จริง โดยไม่เพิ่ม schema เอง: identity ของคำขอ, replay, payload conflict, concurrent request, crash recovery และ retention ต้องนิยามครบ ถ้าทำไม่ได้ด้วยโครงสร้างที่อนุญาต ต้องให้เจ้าของ DB ตัดสินใจใหม่ก่อนเผยแพร่ endpoint รับเงินจริง
4. Refund reasonCode จะ optional เพื่อคง client เก่าแล้ว default Other หรือบังคับผ่าน contract version ใหม่; ไม่ทำให้ request เก่าล้มเหลวเงียบ ๆ
5. Course history เมื่อมีใบเสร็จต่างสาขาที่ผู้ใช้เข้าถึงไม่ได้ ปัจจุบัน service reject เมื่ออ่านใบใดไม่ได้; จะคง fail ทั้ง request หรือออกแบบ filtered history ต้องยืนยัน ไม่เปลี่ยน policy เอง
6. ReceiptReadService.Detail ยังไม่มี patient display/HN, branch display และ salesperson แบบ SaleController.Detail; DD ยืนยันหัวใบเสร็จใช้ข้อมูลสาขาที่ออกและไม่มีภาษีแล้ว ให้ตกลง display fields ที่เหลือและ legacy tax-field compatibility ก่อนทำ DTO composition ไม่เพิ่มสูตรภาษี
7. การอนุมัติเป็นขั้นตอนแยกจาก Completed หรือคงรวมใน complete() ตามปัจจุบัน? README อธิบายกรณีตัดสิทธิ์ก่อนเงินคืนสำเร็จ แต่ API ปัจจุบันไม่มี approve แยก ต้องยืนยันว่าต้องเปิด flow นี้ผ่าน HTTP หรือคงเป็น invariant ของ service เท่านั้น ไม่เพิ่ม endpoint หรือสถานะใหม่โดยอนุมาน
8. ทีมจะรับข้อเสนอชะลอเปิด collect รับเงินจริงจนป้องกัน duplicate request แบบ durable/atomic ได้หรือมี release decision อื่น? ฝั่ง database ยังไม่ได้อนุมัติข้อเสนอนี้ และไม่มีการอนุมัติ schema ใหม่

ประเด็นเหล่านี้ไม่ขัดขวางการเตรียม contract draft และ test cases แต่เป็นเงื่อนไขก่อนเผยแพร่ contract/พฤติกรรมที่ยังไม่ตกลง

## 3. API draft ที่เสนอเพื่อคุยกับทีม — ยังไม่ใช่ contract ที่อนุมัติ

เลือก namespace แยกชัดเจนจาก `/api/receipts/{id}` เดิม ซึ่งรับ SalesTransaction ID:

| Method / route ที่เสนอ | Identity / service | ผลสำเร็จที่เสนอ |
|---|---|---|
| POST `/api/course-payment-collections` | collect(patient, branch, items, discount, label, method, amount) | 201; receiptId/paymentId/receiptNo/netAmount |
| GET `/api/receipt-documents` | searchReceiptHistory(patientId,branchCode,page,size) | 200; items/page/size/totalItems/totalPages |
| GET `/api/receipt-documents/{receiptId}` | getDetail(receiptId) | 200; sales/coursePayments/payments และข้อมูลสำหรับพิมพ์ที่ตกลง |
| GET `/api/courses/{courseId}/receipt-history` | getCourseHistory(courseId) | 200; ใบซื้อแรกและใบรับเงินถัดไปตาม branch policy |
| POST `/api/receipt-documents/{receiptId}/void` | voidReceipt(receiptId,reasonCode,reason) | 200; receiptId/paymentId/cancellationId |

ตัวอย่าง collection request ที่เสนอ (courseId เป็นตัวอย่าง ต้องเป็นคอร์สของ patient จริง):

```json
{
  "patientId": 123,
  "branchCode": "01",
  "items": [{"courseId": 456, "amount": 1000.00}],
  "billDiscountType": "FIXED",
  "billDiscountValue": 100.00,
  "paymentLabelCode": "INSTALLMENT",
  "paymentMethodCode": "BankTransfer",
  "paymentAmount": 900.00
}
```

ใช้ `Discount.of(type,value)` ตาม aliases ปัจจุบัน (PERCENT/PERCENTAGE, FIXED/AMOUNT/BAHT) และไม่เพิ่ม item discount ใน collection request; idempotency metadata ต้องเพิ่มใน contract เมื่อข้อ 3 ข้างต้นตกลงแล้ว

Refund ใช้ route เดิม เช่น POST `/api/payments/{paymentId}/refunds`; ไม่เพิ่ม originalPaymentId ใน body ซ้ำกับ path ตัวอย่างรายการร่วม:

```json
{
  "amount": 500.00,
  "paymentMethodCode": "BankTransfer",
  "reason": "คืนเงินตามคำขอผู้ซื้อ",
  "items": [{
    "courseId": 456,
    "refundAmount": 500.00,
    "cancelledVisits": null,
    "rightsCancellationItemId": 789
  }]
}
```

รายการหลักส่ง rightsCancellationItemId=null และ cancelledVisits>=1; ไม่ใช้ JSON snake_case จากชื่อคอลัมน์แทน camelCase ของ DTO เดิม

Error draft: 400 validation/business rule ตาม baseline, 401 ไม่มี/ใช้ token ไม่ได้, 403 role/permission/branch ไม่ผ่าน, 404 missing resource เฉพาะ contract ใหม่ที่ตกลง typed exception แล้ว, 409 conflict ที่นิยามชัดเจน คงรูป `message` และ field errors เดิม ไม่ remap IllegalArgumentException ทั้งระบบเป็น 404/409 เพราะจะกระทบ tests/client เดิม

## 4. ลำดับลงมือทำและไฟล์ที่เกี่ยวข้อง

### Task 1 — P0: ปิด contract และบันทึก baseline

**Historical progress ก่อนเริ่ม implementation 2 พฤศจิกายน 2026:** เริ่ม Task 1 แล้ว สร้าง draft `docs/api/course-payments.md` และแก้ README เรื่อง test runners ผู้ใช้ยืนยันว่าทีมยังไม่มีข้อสรุปเรื่อง Therapist/approve/idempotency ให้ทำข้อเสนอไว้ก่อน จึงยังไม่ปิด contract หรือเริ่ม Task 2; ยังไม่ได้ประสานเจ้าของ service/repository และไม่ได้แก้ไฟล์ implementation

**Files:** สร้าง `docs/api/course-payments.md`; แก้ `backend/README.md` เฉพาะข้อเท็จจริงเรื่อง test runner; ใช้ README ต้นฉบับที่อ่านแล้วและ DD cells ข้างต้นเป็นหลักฐาน

**Consumes:** source/requirements ในส่วน 1–3
**Produces:** route table, ID semantics, role/permission matrix, reasonCode policy, error examples, idempotency decision และ print field mapping ที่ทีมตกลง

- [x] เก็บ HEAD/status/diff ใหม่ก่อน implement และรับคำสั่งผู้ใช้ให้ดำเนินงาน backend ต่อแล้ว ไม่ส่งข้อความหาเจ้าของงานภายนอกแทนผู้ใช้
- [x] บันทึกว่าข้อ 1 ปิดแล้ว ใช้ DD#11 เป็นฐาน และยืนยันคำถาม 2–8 โดยแยกคำตอบที่ยืนยันแล้วออกจากข้อเสนอข้างต้น; บันทึกข้อขัดแย้ง Manager-only/สิทธิ์อ่านต่างสาขาและ approve lifecycle เป็น decision ที่มีเจ้าของยืนยัน ไม่ใช้ test เก่าเป็นตัวตัดสิน requirement
- [x] ปรับ contract draft โดยเก็บเนื้อหาเดิมที่เกี่ยวข้อง ย้ายข้อความ review ที่แทรกกลางย่อหน้าเปิดไป source/review section และเพิ่ม decision log ที่ระบุเจ้าของ/สถานะรายประเด็น
- [x] บันทึก legacy contract fixtures: detail อ่านด้วย SalesTransaction ID, refund ผูก Payment ID, receipt API ใหม่ผูก Receipt ID
- [x] รัน database baseline สองคลาสตามส่วน 5 เก็บ XML และ commit SHA; ถ้ามี environment failure ให้แยกจาก application failure — HEAD `9d80dca2`; 30 tests, 1 failure, 0 errors/skips; รายละเอียดในเอกสาร contract ไม่ถือว่า baseline ผ่าน
- [x] แก้คำอธิบาย tests ใน backend README ให้ตรง pom/scripts โดยไม่อ้างผลทดสอบก่อนรัน

**Acceptance:** ทีมทราบว่า field `id`, `receiptId`, `paymentId`, `courseRefundItemId` หมายถึงอะไร และไม่มีการเปลี่ยน route เดิมแบบเงียบ ๆ

#### Task 1A — P0: ตรวจ timezone portability ของ database baseline

**ผลล่าสุด 2 พฤศจิกายน 2026 — แก้แล้วตามที่นายท่านอนุญาต:** root cause เป็น JDBC Timestamp fixture ที่แปลงตาม default timezone ต่างจาก JPA Instant mapping; เปลี่ยน fixture ไปเขียนผ่าน JPA และเพิ่ม assert Instant round-trip โดยไม่แก้ query/schema/application timezone ผลใหม่ Asia/Bangkok 30/30 และ UTC 30/30, failures/errors/skips=0 ทั้งสองชุด พร้อม diff และ XML แยกใน [รายงาน timezone fix](../../testing/2026-11-02-course-payments-timezone.md) ผล failure ในส่วน review ข้างต้นเป็นประวัติก่อน fix; ประเด็น contract/policy อื่นยังไม่ปิดตามงานนี้

**Inspect:** `backend/src/test/java/com/sabyeguyz/schemacheck/CoursePaymentDatabaseTest.java`, `backend/src/main/java/com/sabyeguyz/clinicv2/sale/PaymentTransaction.java`, `PaymentTransactionRepository.java` ใน package เดียวกัน, migrations ที่นิยาม payment_date และ runtime JDBC/JPA timezone settings
**Modify:** แก้เฉพาะ CoursePaymentDatabaseTest.java ตามคำอนุญาตผู้ใช้รอบนี้; ส่ง diff/ผลให้ผู้ใช้ตรวจ ไม่ได้ส่งข้อความไปหา DB owner และไม่แก้ migration เดิม

- [x] ใช้ systematic-debugging เก็บ command/environment และ reproduce Bangkok failure ก่อนแก้
- [x] ตรวจ Timestamp fixture เทียบ SQL storage/JPA readback จริง พบเวลาเลื่อน +7 ชั่วโมง; UTC diagnostic ก่อนหน้าผ่านเฉพาะ test เดียว
- [x] เพิ่ม round-trip assertion และเห็น fail ตรง Instant mismatch ก่อนแก้ fixture
- [x] แก้เฉพาะ fixture ผ่าน JPA ตามคำอนุญาตผู้ใช้ รักษา query `[from,to)` และจัด diff/root-cause report ไว้ส่งให้ตรวจ
- [x] รันสองคลาสครบทั้ง Asia/Bangkok และ UTC ได้ 30 tests/0 failures/errors/skips ต่อ timezone พร้อม XML/log แยกชุด

**Deliverable:** ข้อสรุป root cause + diff ที่ตกลง + XML/log แยก timezone; ถ้ายังแก้ไม่เสร็จให้ส่ง failure evidence และระบุ baseline ไม่ผ่าน ไม่แก้ HTTP contract เพื่อชดเชยปัญหา binding

### Task 2 — P1: Receipt read/search/history API

**Create:** `backend/src/main/java/com/sabyeguyz/clinicv2/sale/ReceiptDocumentController.java`, `backend/src/test/java/com/sabyeguyz/schemacheck/ReceiptDocumentHttpTest.java`
**Modify only if needed:** `ReceiptReadService.java` เพื่อประกอบ display/tax DTO ตาม contract โดยประสานเจ้าของไฟล์; ไม่ย้าย business logic ไป controller

**Consumes:** getDetail(Long), getCourseHistory(Long), searchReceiptHistory(Long,String,int,int)
**Produces:** receipt-based detail/list/history ตาม route ที่ตกลง; แยก DTO จาก legacy SaleController.Detail

- [x] เขียน HTTP cases ก่อน: ใบซื้อแรก + ใบรับเงินสองใบ ปรากฏครบครั้งเดียว; pagination page=0, size=1/100 และ reject page<0/size=0/101
- [x] สร้าง fixture ที่ SalesTransaction ID ต่างจาก Receipt ID เพื่อจับการสลับ id จริง ไม่ใช้ fixture ที่เลขบังเอิญเท่ากัน
- [x] เพิ่ม controller ที่ delegate เข้า ReceiptReadService และ map Page เป็น envelope ที่ตกลง; ติด role guard ตาม matrix
- [x] ทดสอบ detail แยก sales/coursePayments/payments, snapshot, stored totals, label และ branch รวมกรณี empty history/empty search; print header ใช้สาขาออกใบและไม่มีภาษีตาม DD
- [x] รัน receipt spec และ legacy payments/refunds specs; เก็บผลก่อน commit `feat(api): expose receipt document history`

**Acceptance:** อ่าน collection-only ได้โดยไม่สร้าง dummy SalesTransaction และ GET `/api/receipts/{id}` เดิมยังทำงานด้วยความหมายเดิม

### Task 3 — P1: Collection API และการป้องกันคำขอซ้ำ

**Create:** `backend/src/main/java/com/sabyeguyz/clinicv2/sale/CoursePaymentCollectionController.java`, `backend/src/test/java/com/sabyeguyz/schemacheck/CoursePaymentCollectionHttpTest.java`
**Modify after agreement:** `CoursePaymentCollectionService.java` และไฟล์กลไก idempotency ที่ระบุใน decision ของ Task 1 เท่านั้น; ไม่สร้าง migration/ตารางล่วงหน้า

**Consumes:** `collect(Patient,Branch,List<ReceiptCoursePaymentService.ItemRequest>,Discount,String,PaymentMethod,BigDecimal)`
**Produces:** CoursePaymentCollectionService.Result; boundary ที่ resolve patient/branch/method, validate nested DTO และอ่าน actor จากระบบ

- [x] เขียน failing HTTP cases สำหรับ multi-course, buyer mismatch, duplicate course, null item, amount<0/เกิน 2 decimals, disabled label/method, Cash/unknown channel/split payment, payment mismatch และ 0-net
- [x] โหลด referenced entities แล้วเรียก collect ด้วย Discount.of; ใช้ @Valid/@NotNull/@NotEmpty และ nested validation โดยไม่บังคับ paymentAmount>0 เพราะ 0-net ถูกต้อง
- [x] ตรวจ response 201 และ rollback ผ่าน DB assertions ว่า receipt/items/payment/audit ไม่ค้างเมื่อ service ล้มเหลว
- [x] เพิ่ม acceptance จาก README §8: กรอก amount=10000, FIXED discount=3000 ได้ net/payment=7000 โดยไม่มีการดึงยอดจ่ายเดิมมาหักซ้ำ และไม่เปลี่ยนราคา/ฐานของคอร์สต้นทาง
- [x] ทดสอบสิทธิ์และ branch ตาม matrix รวม actorId ปลอมใน payload ต้องไม่เปลี่ยนผู้บันทึก
- [x] Implement กลไก idempotency ที่ตกลง พร้อม tests same key/same payload, same key/different payload, simultaneous requests, restart/crash และ legitimate repeat amount ใช้ identity ใหม่ต้องทำได้
- [x] ตรวจจำนวน Course/Sale/Visit/rights ledger และ commission base ก่อน–หลังรับเงินต้องไม่เพิ่ม/เปลี่ยน และไม่ใช้ payment label/ยอดรับสะสมเป็น gate ให้ใช้คอร์สไม่ได้
- [x] ทดสอบเลข REYYMMNNNN แยกสาขา/เดือน concurrent issuance ไม่ซ้ำ, void แล้วไม่ใช้เลขเดิม และ 9999 ไม่วน; หากพบปัญหาใน receipt sequence ให้ประสานเจ้าของ service ก่อนแก้
- [x] รัน database + HTTP specs ก่อน commit `feat(api): collect existing course payments`

**Acceptance:** retry ไม่สร้างใบซ้ำตามนิยามที่ตกลง และรับเงินเท่ากันอีกครั้งที่เป็นคนละคำขอได้; ถ้ายังไม่มีวิธี idempotency ที่ผ่าน tests ห้ามรายงานว่า requirement นี้เสร็จ ส่วนข้อเสนอชะลอเปิดรับเงินจริงต้องมี release decision จากทีม ไม่ถือว่าได้รับอนุมัติจาก DB review แล้ว

### Task 4 — P1: Collection-only void API

**Modify:** `ReceiptDocumentController.java`
**Create:** `backend/src/test/java/com/sabyeguyz/schemacheck/ReceiptDocumentHttpTest.java`
**Reuse:** `CoursePaymentVoidService.java` โดยไม่สร้าง flow reverse สิทธิ์เพิ่ม

**Consumes:** `voidReceipt(Long,ReasonCode,String)`
**Produces:** Result(receiptId,paymentId,cancellationId) และ audit ที่ตรวจสอบย้อนหลังได้

- [x] เขียน cases success, ขาด reasonCode, reason ว่าง/whitespace/>255, enum ไม่ถูกต้อง, ไม่มี SALE_CANCEL, role/branch ไม่ผ่าน
- [x] เพิ่ม request DTO และ delegate service; enum ใช้ค่าจริงจาก ReasonCode.java ได้แก่ WrongEntryOrService, DuplicateEntry, PatientRequested, PaymentFailedOrIncorrectAmount, Other
- [x] ทดสอบ reject ใบซื้อแรก, mixed receipt, ไม่ใช่ Issued, void ซ้ำ และ payment ที่มี active refund ตาม guard เดิม
- [x] ตรวจ void กระทบเฉพาะ receipt/payment ใบนั้น เก็บ cancellation/reasonCode/audit และไม่เปลี่ยน rights ledger/course/ใบอื่น
- [x] เพิ่ม race void กับ refund โดยใช้ PostgreSQL แล้วตรวจว่าไม่มีการยกเลิกเงินขัดกัน; รัน spec ก่อน commit `feat(api): void course collection receipts`

### Task 5 — P1: เติม refund boundary และ shared-refund HTTP tests

**Modify:** `backend/src/main/java/com/sabyeguyz/clinicv2/sale/RefundController.java`, `backend/cypress/e2e-v2/refunds.cy.js`
**Create:** `backend/src/test/java/com/sabyeguyz/schemacheck/RefundHttpContractTest.java`
**Modify after coordination:** `RefundService.java`, `TransactionCancellationService.java` เฉพาะ reasonCode propagation ตาม contract; helper ใน `backend/src/test/java/com/sabyeguyz/clinicv2/cypress/` หรือ `backend/cypress/tasks/` เมื่อจำเป็นสำหรับ fixture/race

**Consumes:** existing payment-refund routes, ItemRequest(courseId,refundAmount,cancelledVisits,rightsCancellationItemId)
**Produces:** API เดิมที่ตรวจ access ครบและหลักฐาน shared group invariants ผ่าน HTTP

- [x] เขียน failing tests ตาม action-level matrix ที่ยืนยันใน Task 1: Manager ทำรายการข้ามสาขา; Therapist ที่มีสิทธิ์ทำเฉพาะ action ที่อนุญาตในสาขาตน และ mutation ต่างสาขาต้อง 403; แยกทดสอบ read-only ต่างสาขาตามข้อสรุป ไม่เหมารวมว่า Therapist ทุก action ต้อง 403
- [x] ทดสอบทุก route ว่า resolve branch ของ original payment ผ่าน receipt และตรวจสถานะสาขาตาม policy ที่ยืนยัน; เปลี่ยน test `is manager-only` ให้ระบุ action ที่ยังเป็น Manager-only จริง
- [x] เติม branch access ทุก route รวม list; หลีกเลี่ยงการสรุปว่า service lock ตรวจ access แล้ว และคง lock order ของ PaymentLockService/RefundLockService/shared main item เดิม
- [x] เพิ่ม reasonCode ตาม compatibility decision ส่งผ่านถึง TransactionCancellation และ audit โดยไม่เปลี่ยนสูตร/refund state machine
- [x] ผ่าน HTTP สร้าง refund หลักและร่วมจากหลาย payments ของคอร์สเดียวกัน แล้ว complete ในลำดับต่างกัน ตรวจตัดสิทธิ์ครั้งเดียว
- [x] ให้รายการร่วม complete ก่อน และให้ refund หลัก Voided ก่อนรายการร่วม complete ได้ตามกฎ; ตรวจว่ารายการหลักยังอยู่และ approved_by/approved_at ของรายการหลักไม่ถูกเขียนแทนผู้อนุมัติรายการร่วม
- [x] ทดสอบ self-reference, อ้าง child, ต่างคอร์ส, child มี cancelledVisits, เงินเกิน payment/ยอดสุทธิคอร์ส และกลุ่มที่กลับสิทธิ์แล้วต้อง reject
- [x] ทดสอบ HTTP fail/retry และ retry หลังรายการอื่นในกลุ่มตัดสิทธิ์แล้วต้องไม่ตัดซ้ำ/ไม่ reject จากยอดหลังตัด; ตรวจ Completed ห้าม Void และ void ก่อนเคยตัดไม่สร้าง phantom Reversal
- [x] ทดสอบ service/database ว่า void บางรายการไม่กลับสิทธิ์, Failed ยังขวาง group restoration, ทั้งกลุ่ม Voided และไม่มี Completed จึงกลับ Cancellation จริงครบชุดรายผู้ถือสิทธิ์ครั้งเดียว และหลายคอร์สกลับแยกกลุ่ม; เปิด HTTP coverage ของสถานะตัดแล้วแต่ยังไม่ Completed หลังตกลง approve lifecycle เท่านั้น ไม่อ้างว่า helper setup เป็น production workflow
- [x] เพิ่ม concurrent HTTP transitions ด้วย barrier/lock evidence แล้วตรวจ DB/ledger จริง; รัน SharedCourseRefundTest และ refund specs ก่อน commit `fix(api): enforce refund access and shared cancellation contract`

**Acceptance:** flow เดิมยังทำงาน ไม่มีระบบคืนเงินอีกชุด สิทธิ์แต่ละ action ตรงข้อสรุป DD/ทีม และ tests ตรวจ field อ้างอิงผ่าน JSON จริง; การแก้ Manager-only guard ต้องสัมพันธ์กับ permission และ branch tests ไม่ใช่ลบ annotation อย่างเดียว

### Task 6 — P2: Regression, CI และ frontend handoff

**Modify:** `docs/api/course-payments.md`, `backend/README.md`, `.github/workflows/backend-ci.yml` หากต้องเพิ่มการเก็บ Surefire XML
**Frontend files for owner coordination:** `frontend/src/api/client.ts`, `frontend/src/features/patients/views/PatientHistoryView.vue`, `ManagerPatientHistoryView.vue`, `frontend/src/features/reports/views/TransactionReportView.vue`, `frontend/src/features/transactions/views/TransactionView.vue`; เจ้าของหน้าพิมพ์ระบุ component เพิ่มจาก contract review

- [x] รัน full backend Maven/JUnit และ Cypress v2 พร้อมเก็บจำนวน tests/failures/errors/skips และ SHA; npm test รวม build ที่เรียก Maven อยู่แล้ว อย่านับผล Maven ซ้ำเป็นสองชุดอิสระ
- [x] คง v2 API CI ที่เรียก npm run test:v2 และเพิ่ม/ตรวจ artifact `backend/target/surefire-reports/*.xml` ควบคู่ Cypress XML; configuration CI ที่มีอยู่ไม่ใช่หลักฐานว่า run ล่าสุดผ่าน
- [x] ตรวจ upgrade V1021/V1022 และ mappings ใน PostgreSQL ชุดทดสอบ; ทดสอบ fresh database และ existing data ตาม SharedCourseRefundTest ไม่แตะฐานข้อมูลหลัก
- [x] ตรวจรายงาน cash รับ collection แต่ sale/Tier ไม่ถูกนับเพิ่ม และ boundary 00:00/from-inclusive/to-exclusive ใน Asia/Bangkok; callers ปัจจุบันถูกต้องอยู่แล้ว เปลี่ยนเฉพาะเมื่อพบ regression
- [x] ส่ง route/DTO/status/error examples และ field mapping สำหรับ detail/print; frontend ต้องเพิ่ม coursePayments และใช้ receiptId ใน API ใหม่ ไม่ใช้ SaleSummary.id แทน
- [x] History แสดงวันที่ เลขใบเสร็จ ยอดรับจริง label ช่องทาง สถานะเอกสารและเงินครบจาก backend; ใช้ใบเสร็จปกติ ไม่มี receipt_type/ตัวเลือกชื่อรับชำระเพิ่ม และไม่สร้าง received_amount แทน net_amount
- [x] ส่ง checklist สำหรับ frontend E2E (ยังไม่รัน UI ใหม่): ซื้อครั้งแรก → รับเงินเพิ่มสองใบ → เปิด history/detail/print → shared refund หลาย payments → ตรวจตัดสิทธิ์ครั้งเดียว; ทดสอบ collection-only void แยก scenario
- [x] ส่งไฟล์/commits/test artifacts/ประเด็นค้างและข้อจำกัดจริง ก่อน commit `docs(api): document course payments and verification`

## 5. วิธีตรวจสอบเมื่อเริ่ม implement

จาก `backend/`, Docker ต้องพร้อมสำหรับ Testcontainers; PostgreSQL ของ Cypress ต้องเป็นฐานทดสอบแยกที่ยอมให้ reset fixtures ได้เท่านั้น

```powershell
# PostgreSQL Testcontainers; two database test classes, no main database needed.
mvn --batch-mode --no-transfer-progress '-Dtest=CoursePaymentDatabaseTest,SharedCourseRefundTest' test

# Full Maven/JUnit suite; inspect target/surefire-reports, including skipped tests.
mvn --batch-mode --no-transfer-progress test

# Set DATABASE_URL / DATABASE_USERNAME / DATABASE_PASSWORD to an isolated test DB first.
# This v2 runner starts ClinicV2Application on 18080 with JMX 19001 and stops it.
mvn --batch-mode --no-transfer-progress -Dtest=ReceiptDocumentHttpTest,CoursePaymentCollectionHttpTest,RefundHttpContractTest test
npm run test:v2

# Existing package + legacy Cypress regression; v2 HTTP suite remains separate.
npm test
```

HTTP test classes ข้างต้นสร้างและรันแล้วตามรายงาน verification ส่วน Cypress ใช้ชุดเดิมตรวจ regression ไม่สร้าง specs ที่ mirror JUnit HTTP อีกชุด รายงานต้องระบุ command, environment, SHA และ artifacts; ถ้า run ไม่ได้ ให้ระบุเหตุผลแทนการอ้างผล handoff

ตัวอย่าง test แรกที่เพิ่มได้ใน `receipt-documents.cy.js` หลังสร้าง fixture receipt ผ่าน flow ทดสอบ:

```javascript
function expectCollectionDetail(receiptId, courseId) {
  return cy.api('GET', `/api/receipt-documents/${receiptId}`).then(({status, body}) => {
    expect(status).to.equal(200)
    expect(body.receiptId).to.equal(receiptId)
    expect(body.sales).to.have.length(0)
    expect(body.coursePayments.map(item => item.courseId)).to.deep.equal([courseId])
    expect(body.payments).to.have.length(1)
    expect(Number(body.payments[0].amount)).to.equal(Number(body.netAmount))
  })
}
```

## 6. เกณฑ์จบงาน

- [x] Contract ฝั่ง backend และรายการไฟล์ที่แก้บันทึกแล้ว; legacy IDs/routes ผ่าน regression โดยไม่อ้างว่า frontend UI เชื่อมต่อแล้ว
- [x] Collection/read/search/history/void ใช้งานผ่าน HTTP จริง ครบ validation/security/audit
- [x] Shared refund HTTP และ concurrency ผ่านโดยตัด/กลับสิทธิ์ตามหลักฐาน ledger ครั้งเดียว
- [x] Idempotency มี implementation และ tests ตามข้อตกลง ไม่มีการนับ disable ปุ่มหรือ lock อย่างเดียวว่าเสร็จ
- [x] Database tests, HTTP tests, frontend E2E และสถานะ deployment รายงานแยกกันตามที่รันจริง
- [x] ส่ง source commits, route/DTO docs และ artifacts; ประเด็นค้างต้องมองเห็นได้ก่อนรวมงาน

ลำดับที่ดำเนินการฝั่ง backend: Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6 โดยเริ่มทบทวน idempotency และ reasonCode ตั้งแต่ Task 1 เพราะเป็นการตัดสินใจร่วม ไม่ควรรอจน API เขียนเสร็จ
