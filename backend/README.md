# Backend Repository

Backend สำหรับ **Physiotherapy Clinic ERP — Sabyeguyz**

## Requirements

- Git
- Java 17
- Maven
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

รัน Test และ package:

```bash
mvn verify
```

CI จะรัน `mvn verify` ใน GitHub Actions ทุกครั้งที่มีการ Push ไปยัง `main`/`develop` หรือเปิด Pull Request

## Test

รัน Test และ package:

```bash
mvn verify
```

หรือรันเฉพาะ Test:

```bash
mvn test
```

CI จะรัน `mvn verify` ใน GitHub Actions ทุกครั้งที่มีการ Push ไปยัง `main`/`develop` หรือเปิด Pull Request

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
