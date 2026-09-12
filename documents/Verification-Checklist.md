# Sabyeguyz Clinic ERP - Verification Checklist

เอกสารนี้ใช้สำหรับให้อาจารย์หรือผู้ตรวจเช็ก branch `v2-rebuild` ของ repository:

`https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP.git`

## 1. โครงสร้างโปรเจกต์

- [x] มี `frontend/`, `backend/`, `documents/` ที่ root
- [x] ไม่มี `frontend/sabyeguyz/` ซ้อน
- [x] มี `compose.yaml` และ `Makefile` ที่ root
- [x] ไม่มี `deploy/`
- [ ] ตรวจไฟล์ที่ไม่ได้ใช้งานซ้ำโดยผู้ตรวจ

## 2. Java และ Backend

- [x] ใช้ Java/JDK 21
- [x] `pom.xml` กำหนด Java 21
- [x] มี `.java-version` เป็น `21`
- [x] Docker และ GitHub Actions ใช้ Java 21
- [x] เปิด Spring Virtual Threads
- [x] แยก controller/service/repository
- [x] ลบ Hello/sample code ที่ไม่ใช้งาน
- [ ] ตรวจหลักฐาน runtime ว่าใช้ Virtual Threads จริง
- [ ] ตรวจ authorization, validation, exception และ transaction boundary

## 3. Configuration และ Secret

- [x] Database URL/user/password รับผ่าน environment variable
- [x] JWT secret รับผ่าน environment variable
- [x] Frontend ใช้ `VITE_API_BASE_URL`
- [x] มี `.env.example` เป็น placeholder
- [x] ไม่มี `.env` และ secret ใน Git
- [x] ไม่มี default secret ใน Java configuration
- [ ] ตรวจ logs ว่าไม่แสดง password, JWT หรือข้อมูลผู้ป่วยเกินจำเป็น

## 4. PostgreSQL, Migration และ Seed

- [x] ลบ H2 dependency และ H2 configuration
- [x] Integration tests ใช้ PostgreSQL ผ่าน Testcontainers
- [x] ใช้ Flyway migrations ชุดเดียวกับ production
- [x] ใช้ `ddl-auto=validate`
- [x] Migration ผ่านทั้งหมด 26 รายการ
- [x] มีเอกสารอธิบาย migration/seed boundary
- [ ] ตัดสินใจร่วมกับทีมว่าจะย้าย demo seed V20-V25 ออกจาก migration เดิมหรือไม่
- [ ] ตรวจ business policy ของ demo data และ production seed

## 5. Docker Compose

- [x] Build context เป็น `./frontend` และ `./backend`
- [x] PostgreSQL, backend และ frontend มี health check
- [x] Port หลักคือ `5173`, `8080`, `5432`
- [x] `docker compose config` ผ่าน
- [ ] Docker images build ผ่านบนเครื่องผู้ตรวจ
- [ ] Containers รันจริงและ healthy
- [ ] ทดสอบ frontend เรียก backend จริง

## 6. Makefile และคำสั่งหลัก

- [x] มี `dev-start`, `dev-rebuild`, `dev-stop`, `dev-logs`
- [x] มีคำสั่ง backend build/run/test
- [x] มีคำสั่ง frontend install/build/run/test
- [x] มีคำสั่ง docker build/run แยก frontend/backend
- [x] มี `test` และ `verify`

## 7. IntelliJ IDEA

- [ ] Project SDK เป็น JDK 21
- [ ] Maven importer ใช้ JDK 21
- [ ] เปิด `backend/pom.xml` เป็น Maven project ได้
- [ ] Spring Boot class มีปุ่ม Run
- [ ] Test class และ test method มีปุ่ม Run/Test
- [x] ไม่ต้องพึ่ง custom script อย่างเดียว
- [x] ไม่ commit `.idea` ที่ผูกกับเครื่อง

## 8. Backend Tests

- [x] `mvn test` ผ่าน: 220 tests, failures 0, errors 0
- [x] Integration tests ใช้ PostgreSQL/Testcontainers
- [x] Flyway migration ใน test ผ่าน
- [x] มี positive, negative และ concurrency tests ในจุดสำคัญ
- [x] แก้ test isolation ที่ชนกับ demo seed data
- [ ] รัน `mvn verify` ซ้ำก่อนส่งงาน
- [ ] ทำ mutation/negative-test verification และบันทึกผล

## 9. Frontend และ E2E

- [x] `npm ci` ผ่าน
- [x] Unit tests ผ่าน: 3 tests
- [x] Type-check และ production build ผ่าน
- [x] ลบ Playwright placeholder/config/steps ที่ไม่ได้ใช้
- [ ] ตรวจ mock data ทีละหน้าและยืนยัน API replacement
- [ ] ตรวจ handling ของ 401, 403, 404, 409 และ 500
- [ ] ตัดสินใจว่าทีมจะใช้ Cypress หรือไม่ หากต้องการ E2E จริง

## 10. GitHub Actions

- [x] Backend workflow ใช้ Java 21
- [x] Frontend workflow ใช้ working directory ถูกต้อง
- [x] Docker workflow ใช้ `compose.yaml` ที่ root
- [x] ไม่มี path เก่า `frontend/sabyeguyz` หรือ `deploy/docker-compose.yml`
- [ ] ตรวจ Actions หลัง push ว่าผ่านจริง

## 11. Documents และ Business Decisions

- [x] เก็บ requirement PDF และ project documents ไว้ใน `documents/`
- [x] README อัปเดตตามโครงสร้างใหม่
- [ ] ยืนยัน workflow การใช้งานจริงกับคลินิก
- [ ] ยืนยัน role/permission
- [ ] ยืนยัน payment/refund/void policy
- [ ] ยืนยัน course transfer/shared-course policy
- [ ] ยืนยัน commission calculation/closing policy
- [ ] บันทึก open decisions และห้ามเดานโยบายธุรกิจเอง

## 12. คำสั่งตรวจหลัก

รันจาก root ของโปรเจกต์:

```powershell
docker compose --env-file .env.example config
```

รัน backend test จาก `backend/`:

```powershell
mvn -B test
mvn -B verify
```

รัน frontend จาก `frontend/`:

```powershell
npm ci
npm run test:unit -- --run
npm run build
```

## หมายเหตุสำหรับผู้ตรวจ

- Testcontainers ต้องเชื่อม Docker Desktop ได้
- บน Windows อาจต้องตั้ง `DOCKER_HOST` เป็น `tcp://localhost:2375`
- ห้ามใช้ `develop` เป็น branch ทำงานโดยตรง
- branch ที่ทำจริงในรอบนี้คือ `v2-rebuild` ไม่ใช่ branch ชื่อยาวเดิม
- รายการที่เป็น `[ ]` ยังต้องตรวจหรือยืนยันกับทีมก่อนสรุปว่าเสร็จ
