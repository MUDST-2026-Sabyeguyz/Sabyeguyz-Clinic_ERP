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

รัน Cypress tests ทั้งหมดและ package (ติดตั้ง dependencies ตามหัวข้อ Test ก่อน):

```bash
npm test
```

CI รัน `npm test` (package + Cypress tests ทั้งหมด) เมื่อ Push ไปยัง `develop`, `feat/**`, `feature/**` หรือเปิด Pull Request เข้า `develop`

## Test

ติดตั้งชุดทดสอบและรัน package พร้อม Cypress tests ทั้งหมดจากโฟลเดอร์ `backend`:

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
```

บน Windows ใช้ `./run-tests.ps1` เพื่อ build และรันทดสอบทั้งหมด หรือ `-Suite api` เพื่อรัน Cypress โดยไม่ package ก่อน

Cypress เป็น test runner เพียงตัวเดียวของ backend และตรวจ HTTP, business rules, repository และ concurrency ทั้งหมด ใช้ backend ที่เปิด/ปิดอัตโนมัติบนพอร์ต 18080 ฐานข้อมูล H2 แยกและ JWT จริง โดยไม่ต้องเปิด frontend ไม่มี JUnit/Mockito เหลืออยู่ ส่วน `src/test/java` เก็บเฉพาะ helpers ที่เตรียมข้อมูลและเปิดทางเข้าถึง logic ภายในให้ Cypress ตรวจผล

`mvn package` หรือ `mvn verify` ใช้ compile/package เท่านั้น หากต้องการรันทดสอบทั้งหมดให้ใช้ `npm test`

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
