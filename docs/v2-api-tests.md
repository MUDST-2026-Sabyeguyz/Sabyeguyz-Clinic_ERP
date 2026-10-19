# ชุดเทส HTTP ของแอป v2 (Cypress + PostgreSQL)

ชุดเทสนี้ยิง HTTP จริงไปยังแอป v2 (`ClinicV2Application`) ที่รันบน PostgreSQL ด้วย schema ใหม่ของทีม
(`backend/src/main/resources/db/migration_new`) พร้อมข้อมูลตัวอย่าง (`db/seed_v2`)

## รันบน CI (วิธีหลัก)

Workflow **V2 API tests** (`.github/workflows/v2-api-ci.yml`) รันอัตโนมัติเมื่อ push เข้า `feat/**`, `feature/**`,
`develop`, `dev-backed` ที่แตะโค้ด v2, schema, seed หรือเทส และเมื่อเปิด PR เข้า `develop` / `dev-backed`

ดูผลที่แท็บ Actions: เปิด step "Run the v2 API tests" แล้วค้นคำว่า `FAILED` ตัวรายงานจะพิมพ์ชื่อเทสที่ล้ม
และข้อความ error ไว้ในล็อกโดยตรง (ไฟล์ JUnit XML แนบเป็น artifact ชื่อ `v2-cypress-results`)

## รันในเครื่อง

ต้องมี: JDK 17+, Maven, Node 22, PostgreSQL ที่ว่างอยู่ และพอร์ต `18080` กับ `19001` ว่าง

```bash
cd backend
npm ci
# ตัวอย่าง: ฐานข้อมูลเปล่าชื่อ clinic
export DATABASE_URL=jdbc:postgresql://localhost:5432/clinic
export DATABASE_USERNAME=clinic
export DATABASE_PASSWORD=clinic
npm run test:v2                                   # ทุก spec
npm run test:v2 -- --spec cypress/e2e-v2/auth.cy.js   # spec เดียว
npm run test:v2:open                              # โหมด interactive
```

สคริปต์ `scripts/run-v2-tests.cjs` จะสตาร์ตแอปด้วย `mvn -Pcypress-v2 spring-boot:start`, รัน Cypress, แล้วปิดแอปให้เอง
ใช้ฐานข้อมูลที่ว่างหรือที่ใช้ทดสอบเท่านั้น: ก่อนทุกเทส `/__cypress/reset` จะล้างตารางธุรกรรมทั้งหมด

## โครงสร้าง

| ที่อยู่ | หน้าที่ |
|---|---|
| `backend/cypress/e2e-v2/*.cy.js` | สเปกเทส (auth, patients, appointments, catalog, sales, courses, course-transfers, refunds, reports, commission, commission-override, management, audit, frontend-contract) |
| `backend/cypress/support-v2/` | คำสั่งช่วย (`cy.api`, `cy.apiAs`, `cy.login`, `cy.registerPatient`, `cy.parallelApi`) และ reset ก่อนทุกเทส |
| `backend/cypress.v2.config.cjs` | ตั้งค่า (ล็อกให้ยิงได้เฉพาะ `127.0.0.1:18080`) |
| `backend/src/test/java/com/sabyeguyz/clinicv2/cypress/CypressV2FixturesController.java` | endpoint `/__cypress/reset` (มีเฉพาะ profile `cypress-v2` ใน test classpath ไม่อยู่ใน jar จริง) |
| `backend/src/test/resources/application-cypress-v2.properties` | ตั้งค่าตอนเทส |

## ข้อมูลตั้งต้นหลัง reset

- ผู้ใช้: `manager / manager123`, `therapist / therapist123`, `therapist2 / therapist123` (ข้อมูลสำหรับ dev/demo เท่านั้น)
- สาขา `01`, `02`; พนักงาน `MGR-001`, `THP-001`, `THP-002`
- บริการ `PHYSIO_30` (800 บาท), `PHYSIO_60` (1,200 บาท); คอร์ส `COURSE_10`, `COURSE_5` และคอร์สทดสอบ `CYPRESS`
  (9,000 บาท, 5 ครั้ง + โบนัส 1, อายุ 60 วัน)
- โครงสร้างอัตราคอมมิชชัน 5 / 6 / 7 / 8 / 10% ตามยอดขายรายเดือน และค่ารักษา PT แทน 45 บาท

## เพิ่มเทสใหม่

1. สร้างไฟล์ใน `backend/cypress/e2e-v2/`; ค่า fixture (`this.fixtures`) ใช้ `function () {}` ไม่ใช่ arrow function
2. ใช้ `cy.api(method, url, body)` (ส่ง token ของ manager ให้) และ `cy.apiAs(token, ...)` สำหรับผู้ใช้อื่น
3. ฟังก์ชันช่วยที่สั่ง `cy.*` ต้องคืนค่าเป็น Cypress chain (เช่น `cy.wrap(x)`) ไม่ใช่ค่าธรรมดา
4. เทสยิงขนานใช้ `cy.parallelApi([req1, req2])` (รองรับ 2 คำขอ เฉพาะ POST/PATCH ที่ `/api/...`)
5. push แล้วดูผลที่ Actions

## บั๊กจริงที่ชุดเทสนี้จับได้ (ดูเป็นตัวอย่าง)

- ลงทะเบียนผู้ป่วยไม่ได้ เพราะ `patient_hn` เป็น NOT NULL แต่ออก HN หลังบันทึก
- ค้นหานัดหมายตอบ 500 เมื่อมี NULL ที่ไม่มีชนิดใน `(? is null or ...)` บน PostgreSQL
- คำสั่ง JPQL ที่ใส่ค่า enum ตายตัวถูกแปลงเป็นชนิดที่ไม่มีใน PostgreSQL
- เลขใบเสร็จชนกัน และใช้คอร์สเกินสิทธิ์ได้ เมื่อยิงพร้อมกัน
