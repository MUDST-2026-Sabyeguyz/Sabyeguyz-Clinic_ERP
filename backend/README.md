# Backend Repository

Backend สำหรับ **Physiotherapy Clinic ERP — Sabyeguyz**

## Requirements

- Git
- Java 17
- Maven
- Node.js 24 (สำหรับ Cypress API tests)
- Docker Desktop

ตรวจสอบเครื่องมือที่ติดตั้งแล้ว:

```bash
java --version
mvn --version
docker --version
```

## Setup

```bash
git clone https://github.com/MUDST-2026-Sabyeguyz/backend.git
cd backend
```

เลือก Branch ที่ต้องการพัฒนา:

```bash
git switch <branch-name>
```

## ขอบเขตของ Repo

- เก็บ Source Code, Test และเอกสารที่เกี่ยวข้องกับ Backend
- รายละเอียด Technology, API และ Implementation ให้ฝ่าย Backend เป็นผู้ยืนยันและดูแล
- ห้ามเพิ่ม Business Rule จากการคาดเดา ให้ยึด Context กลางและ User Story ที่ BA ยืนยัน

## Database

Backend ใช้ PostgreSQL เป็นฐานข้อมูลหลัก สำหรับ Local Development สามารถรัน PostgreSQL ผ่าน Docker ตาม configuration ของโปรเจกต์ได้

ค่าเชื่อมต่อ Local เริ่มต้นตรงกับ Compose ใน Deploy repository และ override ได้ด้วย
`DATABASE_URL`, `DATABASE_USERNAME` และ `DATABASE_PASSWORD`

## Run Backend

```bash
mvn spring-boot:run
```

Backend เปิดให้ใช้งานที่ `http://localhost:8080`

Health Check: `http://localhost:8080/actuator/health`

## การทำงานร่วมกัน

ใช้ Branch `main`, `develop` และ `feature/<feature-name>` ตาม [Branching Guide](https://github.com/MUDST-2026-Sabyeguyz/docs/blob/main/branching.md)

## Context กลาง

อ่าน [Context กลาง](https://github.com/MUDST-2026-Sabyeguyz/docs/blob/main/context.md) ก่อนแก้ไขงาน

## การพัฒนา

Backend รับผิดชอบ:

- REST API
- Business Logic
- Validation
- Database
- Migration
- Authentication / Authorization
- Error Handling
- Backend Tests

**ไม่จำเป็นต้องมี Frontend เพื่อพัฒนาและทดสอบ Backend** สามารถทดสอบ API โดยตรงผ่าน `curl` หรือ API Testing Tool ได้

## API Contract

Frontend และ Backend ต้องพัฒนาตาม API Contract ที่ตกลงร่วมกัน

ห้ามเปลี่ยน Request, Response หรือ Business Rule โดยไม่แจ้งทีมที่เกี่ยวข้อง

API รับเงินคอร์สเดิมและใบเสร็จตาม Receipt ID: ดู [Course payments contract](../docs/api/course-payments.md)
`POST /api/course-payment-collections` ต้องส่ง `Idempotency-Key` และเก็บ key เดิมเมื่อ retry รายการเดิม
ผล replay เก็บใน `audit_log` ร่วมกับธุรกรรมรับเงิน จึงต้องรักษา audit ประเภท `course_payment_collection_request` ไว้พร้อมประวัติใบเสร็จ
Legacy `GET /api/receipts/{id}` ยังคงใช้ SalesTransaction ID; API ใหม่อ่าน Receipt ID ผ่าน `/api/receipt-documents/{receiptId}`

## Local Development

ตรวจสอบ API baseline:

```bash
mvn spring-boot:run
```

ตรวจสอบ API baseline:

```text
GET http://localhost:8080/api/hello
GET http://localhost:8080/actuator/health
```

รัน Maven package (รวม JUnit) แล้วรัน legacy Cypress API tests (ติดตั้ง dependencies ตามหัวข้อ Test ก่อน):

```bash
npm test
```

CI มีขั้นตอน `npm test` และ v2 PostgreSQL/API checks แยกตาม `.github/workflows/backend-ci.yml`; การมี workflow ไม่ใช่หลักฐานว่า run ล่าสุดผ่าน

## Test

ติดตั้งชุดทดสอบและรัน Maven package พร้อม legacy Cypress API tests จากโฟลเดอร์ `backend`:

```bash
npm ci
npx cypress install
npm test
```

เลือกชุดทดสอบ:

```bash
npm run build
npm run test:api
npm run test:api:open
npm run test:v2
npm run test:v2:open
```

บน Windows ใช้ `./run-tests.ps1` เพื่อ build และรันทดสอบทั้งหมด หรือ `-Suite api` เพื่อรัน Cypress โดยไม่ package ก่อน

Backend มีทั้ง JUnit/Testcontainers และ Cypress HTTP tests ส่วน `src/test/java` มี JUnit tests จริง เช่น `CoursePaymentDatabaseTest` และ `SharedCourseRefundTest` รวมถึง helpers สำหรับ Cypress โดยไม่ต้องเปิด frontend

รัน database baseline บน PostgreSQL 16 แยกผ่าน Testcontainers (Docker ต้องพร้อม):

```powershell
mvn --batch-mode --no-transfer-progress '-Dtest=CoursePaymentDatabaseTest,SharedCourseRefundTest' test
```

`mvn test` รัน JUnit suite; `mvn package` และ `mvn verify` รัน tests ตาม Maven lifecycle ด้วย หากไม่ได้ skip tests ตรวจจำนวน tests/failures/errors/skips ใน `target/surefire-reports/*.xml` ไม่สรุปจาก exit code อย่างเดียว

`npm test` เรียก `npm run build` (`mvn clean package` รวม JUnit) ตามด้วย `test:api` สำหรับ legacy Cypress/H2 จึงไม่ครอบคลุม Cypress v2 และไม่ควรนับผล Maven ซ้ำเป็นสองชุดอิสระ

`npm run test:v2` ใช้ PostgreSQL ตาม `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` ต้องเป็นฐานทดสอบแยกที่ยอมให้ reset fixtures ได้เท่านั้น runner เปิด/ปิด backend v2 อัตโนมัติบนพอร์ต 18080 และ JMX 19001 และปฏิเสธเมื่อพอร์ตถูกใช้อยู่ ตัวอย่างเลือก spec ที่มีอยู่:

```powershell
npm run test:v2 -- --spec cypress/e2e-v2/refunds.cy.js
```

JUnit/Testcontainers สร้างฐานของตัวเอง ส่วน Cypress v2 ต้องเตรียมฐานแยกให้ก่อน ห้ามตั้งค่าไปยังฐานข้อมูลหลัก ผลแต่ละชุดเป็นคนละหลักฐาน ไม่ใช่หลักฐาน deployment

ดูข้อจำกัด H2, การเตรียมข้อมูล และตารางเทสต์ที่ย้ายใน [คู่มือ Cypress](cypress/README.md)

## Prototype: Patient Registration

Branch `xan-prototype` เพิ่ม Vertical Slice แรกตาม Figma:

- PostgreSQL schema versioned ด้วย Flyway
- Branch เริ่มต้น `RP` (สาขาราชพฤกษ์)
- `POST /api/patients` สำหรับลงทะเบียนผู้ป่วยและสร้าง HN รูปแบบ `RP-000001`
- Validation, duplicate check และ BCrypt password hashing

## กติกา

- ห้ามสร้าง Business Rule เอง
- ยึด Context และ User Story/Acceptance Criteria
- Requirement จาก BA

## Git

ใช้ `main`, `develop` และ `feature/<feature-name>`

[Branching Guide](https://github.com/MUDST-2026-Sabyeguyz/docs/blob/main/branching.md)

[Context กลาง](https://github.com/MUDST-2026-Sabyeguyz/docs/blob/main/context.md)
