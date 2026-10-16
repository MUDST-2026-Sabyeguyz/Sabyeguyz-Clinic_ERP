# Backend CI: appointment repository fixture fix

วันที่: 14 ตุลาคม 2026 (Asia/Bangkok)

ฐานโค้ด: P3 `c4f1caa`; CI run `38538816084` ล้มในขั้น `npm test` (398/400)

## สาเหตุและการแก้ไข

สอง failures คือ `registeringANewPatientMakesTheAppointmentCurrent` และ `rejectsLinkingAPatientToAnAppointmentThatAlreadyHasOne` ใน `backend/cypress/e2e/v2-workflows.cy.js`

P3 เปลี่ยน `AppointmentService.linkRegisteredPatient` ให้อ่านผ่าน `findByIdForUpdate` เพื่อ serialize การเชื่อมผู้ป่วยพร้อมกัน แต่ test-only `V2WorkflowScenariosController` ตั้ง repository stub เฉพาะ `findById` ทำให้ locked lookup คืน Optional.empty และได้ AppointmentNotFoundException แทนผลที่ต้องการ

เพิ่มคำตอบให้ `findByIdForUpdate` ใน fixture เดิม และเพิ่ม assertion ว่าการผูกซ้ำถูกปฏิเสธเพราะลงทะเบียนแล้ว ไม่ผ่านด้วย IllegalArgumentException ใดก็ได้ ไม่ลด assertions หรือยกเลิก production lock; production/commission source ไม่มี diff

## หลักฐาน

- ก่อนแก้ ทำซ้ำ `npm run test:api -- --spec cypress/e2e/v2-workflows.cy.js`: 46/48, failed 2 ตรงกับ CI, exit 1
- หลังแก้ `npm test` ครั้งแรก: clean package และ 400/400 ผ่าน exit 0 เวลา 16:14:01; จากนั้น checkout หลักเปลี่ยนโดยงานอื่น จึงนำสองไฟล์ไป worktree แยกจากฐาน c4f1caa และรันซ้ำเพื่อยืนยัน final tree
- ผล final worktree `npm test`: clean package และ **400/400 จาก17 specs**, failed/pending/skipped0, exit0; v2-workflows48/48; runnerหยุด17:59:46 +07:00
- PostgreSQL16 ใหม่แยก `alice-backend-ci-fix-20261014`, owner `codex.task=backend-ci-fix-20261014`, bind127.0.0.1:55435; RuntimeCheck ผ่าน RUNTIME_CHECK_OK และ SchemaCheck ผ่าน SCHEMA_CHECK_OK, 491 mapped columns/0 problems, validate45 entities/wire21 controllers/30 services, exit0 เวลา17:58:30
- RuntimeCheck ทดสอบ production entry point/FlywayV1–V3/HTTP ตามขั้น CI; duplicate PromptPay log เป็น negative test409 ที่คาดไว้
- ผลนี้เป็นการรันคำสั่งเดียวกับ workflow ในเครื่อง ไม่ใช่การอ้างว่า GitHub Actions run ใหม่ผ่านแล้ว

## ข้อจำกัดและการตรวจในรอบต่อไป

การตรวจ P3 รอบเดิมรัน test:v2 183 cases แต่ไม่ได้รัน npm test 400 cases ที่ Backend CI ใช้ จึงพลาด fixture นี้ ต่อไปแต่ละ P ที่เปลี่ยน service/repository ต้องตรวจทั้ง npm test และ test:v2 ตามผลกระทบ รวมถึง RuntimeCheck/SchemaCheck ตาม workflow

รอบแก้นี้เปลี่ยนเพียง test fixture/assertion; PostgreSQL schema และ production code คงเดิม จึงไม่รัน browser/commission suites ซ้ำโดยไม่มี code change ที่เกี่ยวข้อง

ตรวจ node --check/git diff --check ผ่าน และลบเฉพาะ PostgreSQL container/volume ของงานหลังตรวจ owner label แล้ว
