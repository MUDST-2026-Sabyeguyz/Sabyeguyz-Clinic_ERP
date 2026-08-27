# Backend Repository

Repository สำหรับส่วน Backend ของ Physiotherapy Clinic ERP

## ขอบเขตของ Repo

- เก็บ Source Code, Test และเอกสารที่เกี่ยวข้องกับ Backend
- รายละเอียด Technology, API และ Implementation ให้ฝ่าย Backend เป็นผู้ยืนยันและดูแล
- ห้ามเพิ่ม Business Rule จากการคาดเดา ให้ยึด Context กลางและ User Story ที่ BA ยืนยัน

## การทำงานร่วมกัน

ใช้ Branch `main`, `develop` และ `feature/<feature-name>` ตาม [Branching Guide](https://github.com/MUDST-2026-Sabyeguyz/docs/blob/main/branching.md)

## Context กลาง

อ่าน [Context กลาง](https://github.com/MUDST-2026-Sabyeguyz/docs/blob/main/context.md) ก่อนแก้ไขงาน

## Local Development

ต้องใช้ Java 17 และ Maven:

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

## Prototype: Patient Registration

Branch `xan-prototype` เพิ่ม Vertical Slice แรกตาม Figma:

- PostgreSQL schema versioned ด้วย Flyway
- Branch เริ่มต้น `RP` (สาขาราชพฤกษ์)
- `POST /api/patients` สำหรับลงทะเบียนผู้ป่วยและสร้าง HN รูปแบบ `RP-000001`
- Validation, duplicate check และ BCrypt password hashing

ค่าเชื่อมต่อ Local เริ่มต้นตรงกับ Compose ใน Deploy repository และ override ได้ด้วย
`DATABASE_URL`, `DATABASE_USERNAME` และ `DATABASE_PASSWORD`
