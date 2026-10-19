# Patients E2E: Base Choice Coverage (BCC)

ขอบเขต: `/staff/patients` ใน checkout นี้ ทดสอบ Chrome → Vue/Vite → backend v2 → PostgreSQL จริง
ไม่ถือว่า BCC ครอบคลุมทุก combination หรือทุกหน้าในระบบ

## Input space partitioning

| Characteristic | Base choice | Alternative blocks | Test |
|---|---|---|---|
| Query | ว่าง | ชื่อ, นามสกุล, HN, โทรศัพท์, ไม่ตรงข้อมูล, ช่องว่างล้วน, ชื่อมีช่องว่างหัวท้าย, HN มี prefix | BCC-01–08 |
| Branch | ทุกสาขา | 01, 02 | BCC-9–10 |
| Sort | ชื่อ ก-ฮ (`nameAsc`) | ชื่อ ฮ-ก (`nameDesc`), เพิ่มล่าสุด (`newest`) | BCC-11–12 |
| Page | หน้าแรก | หน้ากลาง, หน้าสุดท้าย | BCC-13–14 |
| Role | therapist | manager | BCC-15 |

BCC-00 เป็น base test: query ว่าง / ทุกสาขา / nameAsc / หน้าแรก / therapist

จำนวน BCC = `1 + Σ(blocks_i - 1) = 1 + 8 + 2 + 2 + 2 + 1 = 16`
แต่ละกรณีเปลี่ยนจาก base ทีละ characteristic และคงอย่างอื่นเดิม

**Constraint:** Manager ไม่มีตัวเลือกทุกสาขา จึงเปลี่ยน role เพียงอย่างเดียวแบบ strict BCC ไม่ได้
BCC-15 ใช้สาขา 01 ซึ่งเป็นค่าเริ่มต้นที่ใช้ได้ แล้วตรวจสลับเป็น 02 ผ่าน header
รายงานเป็น constrained BCC ไม่อ้างว่ากรณี manager/all-branches ถูกทดสอบ

ข้อมูล: สร้าง 45 ราย (สาขา 01 จำนวน 23, สาขา 02 จำนวน 22) จาก API จริงก่อนทุก test
จึงมี 3 หน้ารวมและ 2 หน้าต่อสาขา ที่ page size 20 ชื่อจงใจมีลำดับต่างจาก ID
Expected results คำนวณจาก fixture ที่สร้างไว้ ไม่คัดลอกผล search API มาเป็น oracle
ตรวจทั้ง ID/order/count จาก backend และแถวบนหน้าเว็บ รวม HN/ชื่อ/โทรศัพท์/สาขา
การเรียงล่าสุดต้องเรียง ID ลดลงทั้งชุดก่อนแบ่งหน้า เพื่อจับกรณี frontend เรียงเฉพาะหน้าปัจจุบัน

## Supplemental tests

- SUP-01 ล้างคำค้นแล้วได้รายการทั้งหมดกลับมา
- SUP-02 คำค้น/สาขา/การเรียงแต่ละอย่าง reset กลับหน้าแรก (interaction เพิ่มเติมจาก BCC)
- SUP-03 เปิดข้อมูลผู้ป่วยที่เลือก พร้อมตรวจ HN และโทรศัพท์
- SUP-04 เปิดฟอร์มลงทะเบียนจากลิงก์บนหน้า Patients
- SUP-05 ไม่มี session ต้องกลับหน้าล็อกอิน
- SUP-06 ข้อความลงทะเบียนสำเร็จแสดงครั้งเดียวและ consume localStorage
- SUP-07 สถานะกำลังโหลด โดยหน่วง response จาก backend จริง
- SUP-08 แสดง error 503 และค้นหาใหม่สำเร็จ (จำลองเฉพาะ response 503)
- SUP-09 token ใช้ไม่ได้: backend จริงปฏิเสธ แล้วกลับหน้า login พร้อมล้าง session
- SUP-10 ลงทะเบียนต่างชาติจากฟอร์มจริง ตรวจ POST 201, HN และข้อมูลที่บันทึก แล้วเปิด profile

ไม่รวม validation ทุก field ของ registration หรือแก้ไขประวัติ/การรักษาในชุด BCC นี้
รวม 26 กรณี: constrained BCC 16 + supplemental 10; error injection แยกจาก BCC
สเปกอยู่ที่ `frontend/cypress/e2e-patients/patients-bcc.cy.js` แยกจาก default suite ที่ใช้ backend ปกติ
ผล JUnit: `frontend/test-results/patients-bcc.xml`; screenshots เมื่อ fail: `frontend/cypress/screenshots/`
วันเกิดไทยตรวจจาก fixture คงที่ `1990-01-01` → `1 มกราคม 2533`

## Run (PowerShell)

ใช้ฐานข้อมูล disposable เท่านั้น: `/__cypress/reset` ล้างข้อมูลธุรกรรมก่อนทุก test
ไม่รันร่วมกับชุด backend E2E อื่นที่ใช้ backend/database เดียวกัน

```powershell
docker run -d --name clinic-patients-bcc-pg --label codex.task=patients-bcc -e POSTGRES_DB=patients_bcc -e POSTGRES_USER=clinic -e POSTGRES_PASSWORD=clinic -p 127.0.0.1:15434:5432 postgres:16
cd backend
$env:DATABASE_URL='jdbc:postgresql://127.0.0.1:15434/patients_bcc'
$env:DATABASE_USERNAME='clinic'
$env:DATABASE_PASSWORD='clinic'
mvn --batch-mode --no-transfer-progress -Pcypress-v2 test-compile spring-boot:start
```

อีก terminal จากโฟลเดอร์ frontend (ต้องไม่มี server อื่นใช้ 5173):

```powershell
npm ci
$env:VITE_API_PROXY_TARGET='http://127.0.0.1:18080'
npm run dev -- --host localhost --port 5173 --strictPort
```

อีก terminal จากโฟลเดอร์ frontend:

```powershell
$env:PATIENTS_BCC_RESET='1'
npx cypress run --config-file cypress.patients.config.ts --browser chrome
# หรือเปิด GUI ด้วย config เดียวกัน
npx cypress open --config-file cypress.patients.config.ts
```

Config ล็อก base URL และใช้ API port 18080; ต้องยืนยัน opt-in `PATIENTS_BCC_RESET=1`
ก่อน reset ผู้รันต้องตรวจว่า backend นั้นเชื่อมต่อ disposable database จริง
เทสต์ตรวจ identity ของ fixture ผ่าน Vite proxy ก่อนเปิดหน้า เพื่อตรวจ proxy ที่ชี้ผิด
ห้ามใช้ config ปกติรัน spec นี้: suite มี guard ป้องกัน reset หากไม่ได้เลือก config เฉพาะ

หลังใช้งาน ให้หยุด Vite ด้วย Ctrl+C, หยุด backend จาก backend directory:

```powershell
mvn -Pcypress-v2 spring-boot:stop
docker stop clinic-patients-bcc-pg
```

Container ที่หยุดยังเก็บข้อมูลทดสอบไว้ รันซ้ำด้วย `docker start clinic-patients-bcc-pg`
แล้วเปิด backend/Vite และรัน Cypress ตามเดิม

## ผลรัน 17 ตุลาคม 2026 (เวลาไทย)

Chrome 155 headless, Cypress 16.1.0, backend profile `v2,cypress-v2`, PostgreSQL 16
ฐานข้อมูล `patients_bcc` ใน container `clinic-patients-bcc-pg` ที่พอร์ต 15434

**24 ผ่าน / 2 ไม่ผ่าน / 0 skipped จาก 26 tests** (52 วินาทีเฉพาะ spec)
BCC ผ่าน 15/16; supplemental ผ่าน 9/10 ไม่ใช่ผลผ่านทั้งหมดของแอป

| กรณีไม่ผ่าน | หลักฐานและสาเหตุ |
|---|---|
| BCC-12 newest | API `sort=newest` คืน ID คนละลำดับกับ ID ลดลงของ fixture ทั้ง 45 ราย; `PatientController.search` รองรับ nameDesc และ fallback เป็น nameAsc สำหรับค่าอื่น รวม newest ส่วน frontend เรียง ID ได้เฉพาะข้อมูลในหน้าที่ backend ส่งมา |
| SUP-09 invalid token | Cypress ตรวจพบ unhandled promise rejection `ApiError: Request failed (401)` จาก `PatientSearchView` ขณะโหลด branch list ใน mounted hook ที่ไม่มี catch; ไม่ได้ปิดการตรวจ uncaught exception เพื่อทำให้เทสต์ผ่าน |

ทั้งสองกรณีคง assertion ไว้ ไม่มีการแก้ production code ในงานเพิ่มชุดทดสอบนี้
SUP-10 ลงทะเบียนผ่าน UI ได้ HTTP 201, ตรวจ profile จาก API และเปิดหน้า profile สำเร็จ
ESLint และ Oxlint สำหรับสองไฟล์ใหม่ผ่าน; ตรวจ config โดยไม่ตั้ง opt-in แล้วปฏิเสธก่อนรันตามที่ออกแบบไว้
รายงาน XML และภาพ failure ถูก ignore ตามการตั้งค่า test output ของ repository
