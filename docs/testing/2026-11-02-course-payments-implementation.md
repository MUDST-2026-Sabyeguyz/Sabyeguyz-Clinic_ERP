# Course payments backend verification

Implementation base: `9071c89e` บน `feat/database`; เริ่มงาน 2 พฤศจิกายนและตรวจรวมเสร็จต่อเนื่องวันที่ 3 พฤศจิกายน 2026 (Asia/Bangkok) ผลด้านล่างเป็น local working-tree verification ก่อน commit ของ implementation ไม่ใช่ผล CI/deployment

Source commits ที่ตรงกับผลตรวจ: `2b3e6201` implementation/API/HTTP tests และ `0429d3a8` regression fixtures/specs ไม่มีการเปลี่ยน source หลังผล green ก่อน commit ทั้งสองชุด

## สิ่งที่เสร็จใน backend

- Collection API สำหรับคอร์สเดิม พร้อม nested validation, manual label/discount, JWT actor และ branch checks
- Durable idempotency โดยใช้ audit_log เดิมตามที่ผู้ใช้อนุญาต ไม่เพิ่ม schema และไม่แก้ migration
- Receipt-ID detail/search/course history รวม collection-only receipts, buyer/HN และ branch header สำหรับพิมพ์ พร้อม payments/channel/status
- Collection-only void ผ่าน service เดิม พร้อม reasonCode/SALE_CANCEL/branch/refund guards
- Refund คง Manager-only ตามที่ผู้ใช้เลือก เพิ่ม branch checks ทุก route และ optional reasonCode โดย legacy default Other
- Contract ใน [course-payments.md](../api/course-payments.md) ระบุ ID semantics, payloads, error behavior, idempotency retention และ frontend handoff
- Backend CI เพิ่ม JUnit run ด้วย Asia/Bangkok และเก็บ Surefire XML แยก default/Bangkok เพิ่มจาก Cypress artifacts เดิม ไม่อ้างว่า live CI ผ่านจากการแก้ workflow

## Tests ที่รันจริง

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Maven/JUnit ทั้งหมด, JVM Asia/Bangkok | 64 | 0 | 0 | 0 |
| Maven/JUnit ทั้งหมด, JVM UTC | 64 | 0 | 0 | 0 |
| Cypress v2, 36 specs | 339 | 0 | 0 | 0 |
| Legacy Cypress/H2, 17 specs | 450 | 0 | 0 | 0 |

ทั้งสอง run exit 0 ตรวจ XML ทั้งห้าคลาสและ user.timezone จริงแยกกัน ไม่ใช้ผล UTC ของ test เดียวแทน full suite

| Class | Tests | หน้าที่ |
|---|---:|---|
| CoursePaymentDatabaseTest | 20 | service/DB collections, totals, rollback, invariants และ payment date [from,to) |
| SharedCourseRefundTest | 10 | migration/legacy preservation, shared group cancellation/reversal/holders และ locking |
| CoursePaymentCollectionHttpTest | 12 | real JWT/HTTP collection, validation, actor/branch, durable replay และ receipt numbers |
| ReceiptDocumentHttpTest | 13 | receipt detail/search/history/print payload, legacy ID distinction และ void guards/race |
| RefundHttpContractTest | 9 | Manager-only, closed branch, reasonCode, shared refund order/retry/concurrency/ceilings |

Backend package: `mvn --batch-mode --no-transfer-progress -DskipTests package` exit0/BUILD SUCCESS หลัง JUnit ผ่านแล้ว ไม่ได้นับ tests ซ้ำจาก build นี้ Legacy full suite รอบสุดท้ายผ่าน450tests/17specs exit0แล้ว ตรวจ XML ทั้ง v2/legacy และทั้งสอง timezone ครบ ไม่มี failures/errors/skips

ตรวจ workflow YAML ด้วย js-yaml parser ผ่าน และ git diff --check ผ่าน PostgreSQLชั่วคราวที่สร้างสำหรับ Cypress ถูกปิด/ลบด้วย --rm แล้ว runnersปิดเซิร์ฟเวอร์ทดสอบตามปกติ ไม่หยุดบริการเดิมของผู้ใช้

### Stale Cypress regression ที่ซ่อมเฉพาะ tests

รอบแรกของ Cypress v2 มี339tests/12failures เทียบ checkout เดิม `9071c89e` ที่ extract ด้วย git archive ในโฟลเดอร์ชั่วคราว แล้วรัน6specsที่ล้มเหลวได้56tests/12failures ชื่อ testcase ที่ล้มเหลวทั้ง12ตรงกันทุกชื่อจากการเทียบ XML จึงพิสูจน์ว่า failuresมีอยู่ก่อนเพิ่มAPIใหม่

แก้เฉพาะ6specs ไม่แก้ production business rules:

- admin-misc: สร้าง Visit ผ่าน paid-service flow จริงแทน standalone check-in ที่ระบบไม่รองรับ คง negative validation เดิม
- catalog/course-transfers: sessionsรวมโปรโมชันแล้ว ไม่มีbonusแยก; purchaseต้องมีpaymentLabelCodeและassert201ก่อนtransfer; five-visits transfer2เหลือ3และreverseกลับ5
- management: ส่งrequired email และใช้emailใหม่ในduplicate-username caseเพื่อไม่ให้failด้วยเหตุผลอื่น
- payment-policy: ตรวจshared-refund IDs เพิ่มโดยยังตรวจยอด/สิทธิ์; insufficient-rightsเป็น400พร้อมข้อความและตรวจไม่มีside effectsตามerror contractปัจจุบัน
- receipt-history: subtotalก่อนส่วนลดรายรายการเป็น2000 ส่วนnet/paidยัง1900ตามDD

หลังแก้ รัน Cypress v2 ทั้ง36specsใหม่ผ่าน339testsทั้งหมด และ independent review ยืนยันว่าไม่มีการลด validation หรือเปลี่ยนสูตรเพื่อให้ tests ผ่าน

Legacy H2 runner รอบแรกมี448tests/16failuresในv2-financeและv2-workflows เทียบสองspecsเดิมบน9071ได้165tests/16failures และ failure testcase namesตรงกันทุกชื่อเช่นกัน จึงไม่ใช่ regression จากAPIใหม่

ซ่อมเฉพาะ test helpers/specs:

- Tier doubles ใช้ชื่อ repository method ปัจจุบัน, fee rule double คืน version ที่จำเป็น, SalesTransaction fixtures มี id/type/quantity/unitPrice ครบก่อนคำนวณ subtotal
- วันที่เริ่มกฎค่ามือใช้เดือนถัดไปตาม clinic calendar แทนวันที่กรกฎาคม2026ที่ผ่านไปแล้ว คงค่าคาดหวังเชิงตัวเลขของ fee/commission/allocation เดิม
- เปลี่ยน expectation แบบเลือก staff-specific rule ท่ามกลางสอง active rules เป็นกรณีมี central ruleเดียวที่ถูกต้อง และเพิ่มการปฏิเสธช่วงทับกันตามDD#11
- Booking defaultเป็นUnconfirmed; manual confirm/Arrived/check-in ที่เลิกใช้ตรวจ exception+ข้อความเจาะจง เพิ่ม positive confirmAfterService พร้อมhistory1แถวและคงalready-confirmed guard

เพิ่ม2testsจากcoverageที่รองรับจริง ไม่ skip/delete failuresเพื่อทำให้ผ่าน Targetedหลังแก้ผ่าน118finance+49workflow=167tests และ independent review ยืนยันว่าไม่มีการเปลี่ยน production formulas หรือทำ assertions อ่อนลง

## หลักฐาน idempotency

1. Reproduce red ก่อน implement: HTTP tests ใหม่รวม 26 tests มี20 failures/0errors; collection routes ยังไม่มีและ refund ยังขาด branch/reasonCode guards
2. Same key/payload ได้201/body/IDsเดิม มี receipt/payment/replay audit เพิ่มเพียงชุดเดียว คนละ key รับจำนวนเท่ากันใหม่ได้
3. Restart application หลัง commit แล้ว retry อ่านผลเดิมจาก PostgreSQL โดยไม่สร้างเงินใหม่
4. Hold computed advisory transaction lock ด้วยอีก JDBC connection แล้วเริ่ม HTTP สองคำขอ ตรวจ pg_stat_activity ว่าทั้งสองรอ advisory lock จริงก่อนปล่อย จากนั้นได้ผลเดียวกันและ receipt เพียงใบเดียว
5. Test-only PostgreSQL trigger จำลองการเขียน replay audit ล้มเหลวหลัง service รับเงินทำงานแล้ว ยืนยัน receipt/items/payment/replay audit rollback ไม่มี partial writes เมื่อเอา trigger ออก keyเดิม retry ได้
6. Same key/payloadต่างคืน409; auth/branchถูกตรวจอีกครั้งสำหรับ replay; client actorIdปลอมไม่มีผล; scopeแยกตาม actor

ข้อ5เป็น simulated pre-commit persistence failure ไม่ใช่การ kill OS process ทุกจังหวะ ส่วนหลังcommit/responseสูญหายพิสูจน์ด้วย replay และ restart ผล audit เก็บถาวรตาม contract ไม่มี TTL/purge อัตโนมัติ

## Review และ regression เพิ่มเติม

- Independent review ของ receipt และ refund ไม่พบ actionable correctness/security defect
- Collection review พบ precision ของ billDiscountValue ต้องตรง decimal(12,4); เพิ่ม failing HTTP test (expected400แต่ได้201) แล้วเพิ่ม @Digits/@DecimalMin ผล green รวมอยู่ใน64tests
- เพิ่ม round-trip/timezone regression จาก fix ก่อนหน้า โดยคง query [from,to) เดิม
- Receipt number tests ตรวจแยกสาขา/เดือน, ไม่ใช้เลข void ซ้ำ และไม่วนหลัง9999
- ไม่เพิ่ม Cypress specs ที่ mirror JUnit HTTP ชุดใหม่ ใช้ Cypress suite เดิมเป็น independent legacy regression

## Environment และคำสั่ง

Windows 11, Corretto Java21.0.12, Maven3.9.16; pom compiler target Java17; JUnit ใช้ Testcontainers PostgreSQL16-alpine และ random HTTP ports ทุกฐานเป็น disposable test DB ไม่ใช่ฐานข้อมูลหลัก

```powershell
# backend/
mvn --batch-mode --no-transfer-progress '-DargLine=-Duser.timezone=Asia/Bangkok' test
mvn --batch-mode --no-transfer-progress '-DargLine=-Duser.timezone=UTC' test

# Cypress v2: DATABASE_URL/USERNAME/PASSWORD ชี้เฉพาะ PostgreSQL disposable ที่ตั้งขึ้นสำหรับงานนี้
npm run test:v2
# Legacy Cypress: isolated H2 application ตาม runner เดิม
npm run test:api
```

Local artifacts (ไม่ใช่ CI artifacts และ Maven clean อาจลบ):

- `backend/target/course-payments-full-bangkok.log`, `backend/target/course-payments-full-utc.log`
- `backend/target/course-payments-verification/bangkok/TEST-*.xml`, `backend/target/course-payments-verification/utc/TEST-*.xml`
- `backend/target/course-payment-http-red.log`, `backend/target/course-payments-discount-red.log`
- `backend/target/course-payments-cypress-v2.log` และ Cypress XML ใน `backend/cypress/results/`
- `backend/target/course-payments-cypress-v2-final.log` และ `backend/target/course-payments-package.log`
- Baselineก่อนแก้tests: `backend/target/course-payments-baseline/backend/baseline-cypress.log` กับ XML ใน baseline directory; ผลcurrentก่อนแก้เก็บที่ `backend/target/course-payments-verification/cypress-before/`
- Legacyก่อนแก้: `backend/target/course-payments-cypress-legacy.log`, `backend/target/course-payments-baseline/backend/baseline-legacy.log` และ XML snapshot ใน `backend/target/course-payments-verification/legacy-before/`
- Legacy targetedหลังแก้: `backend/target/course-payments-legacy-targeted.log`; fullรอบสุดท้าย: `backend/target/course-payments-cypress-legacy-final.log`

## ขอบเขตส่งต่อ frontend และ release

Backend flow ผ่าน real HTTP และมี print-ready JSON แต่ไม่มีการเพิ่มหน้ารับเงิน/แก้หน้า history/print ใน frontend จากงานนี้ จึงไม่อ้าง browser E2E ของ UI ใหม่หรือ deployment

Frontend ใช้ namespaceใหม่กับ Receipt ID จริง แสดง coursePayments และสร้าง Idempotency-Keyหนึ่งครั้งต่อรายการรับเงิน/ใช้ keyเดิมเมื่อretry; legacy salesApi และ SalesTransaction ID ยังคงเดิม Checklist UI หลังทีม frontend เชื่อม: ซื้อครั้งแรก → รับเงินเพิ่มสองใบ → เปิด history/detail/print → คืนเงินหลาย payment ในกลุ่มเดียว → ตรวจตัดสิทธิ์ครั้งเดียว และแยก scenario void collection-only

ยังไม่ได้ส่งข้อความไปหาทีมภายนอก, push, merge, deploy หรือรัน migration กับฐานหลัก ทุกการเปลี่ยนอยู่ใน checkout นี้
