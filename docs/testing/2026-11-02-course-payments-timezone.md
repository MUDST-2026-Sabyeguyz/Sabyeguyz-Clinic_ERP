# Course payment boundary test: Asia/Bangkok fixture fix

วันที่ 2 พฤศจิกายน 2026 (Asia/Bangkok), branch `feat/database`, base HEAD `9d80dca2c0b8d66d459ea7926b96246864ef53e1` บวก working-tree diff ที่ยังไม่ commit ผู้ใช้อนุญาตให้แก้ปัญหา timezone และส่ง diff/ผล tests ในรอบนี้

## สาเหตุที่ตรวจพบ

`CoursePaymentDatabaseTest.paymentQueryUsesInclusiveStartExclusiveEndAndBranchAndStatusFilters` เตรียม boundary records ผ่าน JdbcTemplate ด้วย `java.sql.Timestamp.from(Instant)` แต่ production mapping ของ PaymentTransaction ใช้ JPA `Instant` กับคอลัมน์ PostgreSQL `TIMESTAMP` ซึ่งไม่มี timezone

เมื่อ JVM และ DB session เป็น Asia/Bangkok, fixture ตั้งใจใส่ `2026-01-01T17:00:00Z` แต่ JDBC บันทึก SQL local timestamp เป็น `2026-01-02 00:00:00` จากนั้น JPA อ่านกลับเป็น `2026-01-02T00:00:00Z` คลาดจาก Instant ที่ตั้งใจ 7 ชั่วโมง จึงเลือก payment 4 ที่ควรอยู่ก่อน from และทำ payment 3 ที่ควรอยู่ก่อน to หลุดออก

Diagnostic อ่านทั้ง `show timezone`, `payment_date::text` และ JPA-loaded `paymentDate` ใน run เดียว ยืนยัน JVM=Asia/Bangkok, DB=Asia/Bangkok และค่าคลาดข้างต้น ไม่ได้สรุปจาก UTC diagnostic pass เพียงอย่างเดียว

## Diff ที่แก้

เปลี่ยนเฉพาะ `backend/src/test/java/com/sabyeguyz/schemacheck/CoursePaymentDatabaseTest.java`:

1. เก็บ boundary IDs/Instants ใน `boundaryDates`
2. เขียนเวลาของ fixture ผ่าน JPA ใน transaction โดยใช้ `PaymentTransaction.setPaymentDate(...)` แบบเดียวกับ production mapping แล้ว flush/clear
3. อ่านกลับจาก repository และ assert ว่าทุก boundary record มี Instant ตรงค่าที่ตั้งใจ ก่อนตรวจ query results

Expected IDs และ assertions ของ from-inclusive/to-exclusive, branch filter, all-branch และ Completed-only ไม่เปลี่ยน ไม่มีการบังคับ UTC ใน application หรือแก้ production query/schema/migration Temporary diagnostic output ถูกเอาออกจาก source แล้ว

ข้อจำกัด: การแก้นี้พิสูจน์ fixture/query path ที่ทดสอบ ไม่ใช่การรับรองทุก TIMESTAMP mapping ในระบบหรือทุก database session timezone

## Red / green evidence

Environment: Windows 11, Java Corretto 21.0.12, Maven 3.9.16, Testcontainers PostgreSQL 16-alpine แยกจากฐานข้อมูลหลัก

| Run | Tests | Failures | Errors | Skipped | Exit |
|---|---:|---:|---:|---:|---:|
| Before fix: original boundary test, Asia/Bangkok | 1 | 1 | 0 | 0 | 1 |
| Red: เพิ่ม Instant round-trip assertion แต่ยังใช้ JDBC fixture เดิม, Asia/Bangkok | 1 | 1 | 0 | 0 | 1 |
| After fix: CoursePaymentDatabaseTest + SharedCourseRefundTest, Asia/Bangkok | 30 | 0 | 0 | 0 | 0 |
| After fix: CoursePaymentDatabaseTest + SharedCourseRefundTest, UTC | 30 | 0 | 0 | 0 | 0 |

Red assertion ยืนยัน `expected 2026-01-01T17:00:00Z but was 2026-01-02T00:00:00Z` จึงจับปัญหา fixture ได้ตรงสาเหตุ ไม่ได้เปลี่ยน expected ให้ยอมรับเวลาที่ผิด

แต่ละ green run: CoursePaymentDatabaseTest 20 tests + SharedCourseRefundTest 10 tests นี่คือสองคลาส JUnit `*Test.java` ที่พบใน checkout ปัจจุบัน ไม่ใช่ผล Cypress/HTTP/E2E/CI/deployment

คำสั่ง green จาก `backend/` (รันแยกครั้งและเก็บ reports ก่อนเริ่มครั้งต่อไป):

```powershell
mvn --batch-mode --no-transfer-progress '-Dtest=CoursePaymentDatabaseTest,SharedCourseRefundTest' '-DargLine=-Duser.timezone=Asia/Bangkok' test
mvn --batch-mode --no-transfer-progress '-Dtest=CoursePaymentDatabaseTest,SharedCourseRefundTest' '-DargLine=-Duser.timezone=UTC' test
```

## Local artifacts สำหรับส่ง diff/ผล

ไฟล์ใน target เป็น local artifacts ไม่ได้ commit และอาจถูกล้างโดย Maven clean:

- Diff เฉพาะ test fix: `backend/target/course-payment-timezone-fix/fix.patch`
- Original failure: `backend/target/course-payments-bangkok-before.log`
- Binding diagnostic: `backend/target/course-payments-bangkok-diagnostic.log`
- Red assertion: `backend/target/course-payments-bangkok-red.log`, `backend/target/course-payment-timezone-fix/red-bangkok.xml`
- Green Bangkok: `backend/target/course-payment-timezone-fix/bangkok.log` และ XML ทั้งสองคลาสใน `backend/target/course-payment-timezone-fix/bangkok/`
- Green UTC: `backend/target/course-payment-timezone-fix/utc.log` และ XML ทั้งสองคลาสใน `backend/target/course-payment-timezone-fix/utc/`

งานเดิมใน `backend/README.md` และ `docs/api/course-payments.md` ไม่ถูกเขียนทับจาก fix นี้ ข้อความ failure ใน contract draft เป็นประวัติผลก่อนแก้; ใช้รายงานนี้และผล green ใหม่สำหรับสถานะ timezone ล่าสุด
