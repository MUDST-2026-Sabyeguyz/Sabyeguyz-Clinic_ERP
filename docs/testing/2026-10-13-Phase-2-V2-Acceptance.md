# แผนงานข้อ 2: เปิด v2 สำหรับทดสอบ

วันที่: 13 ตุลาคม 2026 (Asia/Bangkok)

Confirmed Requirement: ทดสอบ Login → ลงทะเบียน → นัดหมาย → ขาย → ใช้คอร์ส ผ่าน API จริงบน PostgreSQL แยก และ commit ผลงาน

## ฐานงานและขอบเขต

เริ่มจาก `feat/backend-v2-integration` ที่ `58ff9eb` โดย working tree ว่าง Runtime/profile v2, migrations ล่าสุด และชุด API regression มีอยู่แล้วจากการรวม branch ล่าสุด จึงใช้ implementation ที่มีอยู่และเพิ่ม acceptance test ให้ครบเส้นทางของผู้ป่วยคนเดียว

- Production entry point เป็น `ClinicV2Application` และ scan เฉพาะ `com.sabyeguyz.clinicv2`
- `cypress-v2` ใช้ test classpath, profile `v2,cypress-v2`, HTTP `127.0.0.1:18080` และ JMX port `19001`; runner ตรวจว่า ports ว่างก่อนเริ่มและหยุดเฉพาะ server ของชุดทดสอบ
- Production ใช้ `db/migration` V1–V3; demo seeds และ reset endpoint ใช้เฉพาะ test profile
- PostgreSQL 16.15 อยู่ใน container ชั่วคราว `alice-phase2-01a133b6` ผูก `127.0.0.1:55432` และ label เจ้าของ chat นี้ แยก `phase2_v2` สำหรับ Cypress กับ `phase2_runtime` สำหรับ production acceptance
- ไม่เปลี่ยน SQL, production code, ข้อมูลเดิม หรือ business rules ของแผนข้อ 3

## Acceptance test ที่เพิ่ม

`backend/cypress/e2e-v2/phase2-workflow.cy.js` ใช้ผู้ป่วยคนเดียวและไม่ reset ระหว่างขั้นตอน:

1. ตรวจ unauthenticated request ได้ 401, รหัสผ่านผิดได้ 400 และ login Manager ได้ JWT/role ตาม contract
2. ลงทะเบียนผู้ป่วยไทย ตรวจ HN และอ่าน profile กลับ
3. จองนัดกับ therapist/service ในสาขา 01 ตรวจ patient และสถานะ Confirmed
4. ขายบริการ 800 บาท ชำระ Cash เต็มจำนวน ตรวจ receipt number, paid total และอ่าน receipt กลับ
5. ซื้อคอร์ส 5+1 สิทธิ์ให้ผู้ป่วยเดิม โดยกำหนด case owner เป็น therapist ของนัด
6. Check-in → start-service → ใช้คอร์สโดยส่ง appointmentId → complete
7. อ่านนัดและคอร์สกลับ ตรวจ Completed, ใช้ 1/เหลือ 5, ledger USAGE หนึ่งรายการ และประวัติการใช้คอร์สตรงกับ HN เดิม

ข้อจำกัด: การขายบริการที่รับเงินกับการซื้อคอร์สเป็นคนละรายการใน test นี้ ไม่ได้ยืนยันว่าคอร์สมีการรับเงินแล้ว และไม่ได้อ้างว่าเป็นการขายคอร์สพร้อมชำระเงินใน receipt เดียวกัน

## การตรวจซ้ำ

สร้าง PostgreSQL ทดสอบว่าง แล้วตั้ง `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` ให้ชี้เฉพาะฐานข้อมูลทดสอบก่อนรันจาก `backend`:

```powershell
npm run test:v2 -- --spec cypress/e2e-v2/phase2-workflow.cy.js
npm run test:v2
```

ตรวจ production profile บนฐานข้อมูลว่างอีกชุด โดยตั้ง `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` สำหรับ test เท่านั้น:

```powershell
mvn --batch-mode --no-transfer-progress test-compile dependency:build-classpath '-Dmdep.outputFile=target/test-classpath.txt' '-Dmdep.includeScope=test'
$acceptanceClasspath = 'target/classes;target/test-classes;' + (Get-Content target/test-classpath.txt -Raw).Trim()
java -cp $acceptanceClasspath com.sabyeguyz.schemacheck.RuntimeCheck
java -cp $acceptanceClasspath com.sabyeguyz.schemacheck.SchemaCheck
```

RuntimeCheck ต้องใช้ฐานข้อมูลว่าง เนื่องจากใช้ชื่อ fixtures คงที่; SchemaCheck ใช้ฐานข้อมูลเดียวกันหลัง RuntimeCheck ได้

## ผลตรวจรอบนี้

ทั้งหมดเป็นผลรันในเครื่องรอบนี้บน baseline `58ff9eb` พร้อม acceptance test ใหม่

- Acceptance test ใหม่: 1/1 ผ่าน, exit 0
- Regression API v2 ทั้งชุด: 143/143 ผ่านจาก 18 specs, failed/pending/skipped 0, exit 0; runner หยุด test server สำเร็จ
- Production `RuntimeCheck`: `RUNTIME_CHECK_OK`, exit 0; Flyway V1/V2/V3 success ทั้งสามรายการ และไม่ scan legacy appointment controller
- `SchemaCheck`: 489 mapped columns, 0 problems; Hibernate validate ผ่าน 45 entities, wiring ผ่าน 21 controllers / 30 services; `SCHEMA_CHECK_OK`, exit 0
- `node --check` ของ test ใหม่ และ `git diff --check` ผ่าน
- ลบ container/anonymous volume ของงานนี้หลังยืนยัน label เจ้าของแล้ว

เกณฑ์ข้อ 2 ที่ตรวจแล้ว: runtime/profile v2 แยกจาก legacy, migration ถูกชุด, ฐานข้อมูลทดสอบแยก, Login → ลงทะเบียน → นัดหมาย → ขาย → ใช้คอร์สผ่าน HTTP จริง พร้อมอ่านข้อมูลกลับยืนยัน persistence

Open Question/งานนอกขอบเขต: กฎสิทธิ์สาขา ผู้รักษาจริง การคืนเงิน และ concurrency ของ HN ต้องตรวจในแผนช่วงถัดไป ผลครั้งนี้ไม่ได้ยืนยันทุก business flow, frontend browser หรือ GitHub Actions
