# Phase Implementation Report

วันที่: 11 กันยายน 2026  
Repository: `Sabyeguyz-Clinic_ERP`  
Branch: `feat/connect-to-backend`

## ขอบเขตของ Phase

- Frontend Coding
- Compose Docker - Build
- Backend Coding & Connect Database
- Unit Test
- Pipeline as Code - Build

## สรุปผล

ระบบสามารถรันด้วย Docker Compose และเชื่อมต่อ Frontend, Backend และ PostgreSQL ได้แล้ว โดย Frontend เรียก Backend API จริงในฟังก์ชันหลัก และ Backend ใช้ PostgreSQL พร้อม Flyway migration

## งานที่ทำ

### Frontend Coding

- เพิ่ม API client กลางที่ `frontend/sabyeguyz/src/api/client.ts`
- แนบ JWT Bearer Token กับ protected request
- จัดการ `401 Unauthorized` และล้าง session อัตโนมัติ
- เชื่อม Login/Logout กับ Backend
- เชื่อม Patient search, register, profile และ update
- เชื่อม Appointment และ lifecycle actions
- เชื่อม Service, Course, Course Ledger และ Course Transfer
- เชื่อม Sale, Payment และ Receipt
- เชื่อม Dashboard และ Reports
- เชื่อม Back Office ได้แก่ Branch, Payment Method, Resource และ Master Data
- เชื่อม Commission Scheme, Tier, Closing และ Course Commission Detail เท่าที่ Backend รองรับ
- ปรับหน้า Login ให้รับ `Username หรือ Email`

### Backend Coding & Database

- เพิ่ม Patient update endpoint: `PUT /api/patients/{id}`
- เพิ่ม CRUD Resource และ Master Data พร้อม migration V17
- เพิ่ม Payment Method activate/deactivate
- เพิ่ม Commission Scheme update, activate/deactivate และ report
- เพิ่ม Treatment Fee Rule แบบ `FIXED` และ `PERCENTAGE`
- เพิ่มสูตร `CAP_AT_COMMISSION`
- เพิ่ม Commission Allocation Ledger ต่อ Visit
- เพิ่ม Commission Closing และ locked commission rate ต่อ Course
- เพิ่ม fields สำหรับ seller, case owner, sale month และ commission status ใน Course Purchase
- เพิ่ม migration V18 สำหรับ commission closing/allocation
- เพิ่ม email ใน `app_users` ด้วย migration V19
- ปรับ Authentication ให้ค้นหาตามลำดับ:
  1. ตรวจ `username`
  2. ถ้าไม่พบ จึงตรวจ `email`
  3. ตรวจสอบ `password`
- ปิด `spring.jpa.open-in-view`
- ปรับ Security ให้ใช้ `permitAll` สำหรับ auth และ health endpoints
- ลบ dependency OAuth2 resource server ที่ซ้ำกัน

### สูตร Commission ที่รองรับ

- Total Course Commission Pool = Course Price × Locked Commission Rate
- Commission Allocation / Visit = Commission Pool ÷ Total Visits
- Gross Commission Allocated = Used Visits × Allocation / Visit
- Outstanding = Commission Pool - Gross Commission Allocated
- Treatment Fee แบบ Fixed หรือ Percentage
- Owner Net = Gross Commission Allocated - Treatment Fee
- Treatment Fee ไม่สร้าง commission tier ใหม่ และหักจาก pool เดิม

### Docker Compose

- Build image ของ Backend สำเร็จ
- Build image ของ Frontend สำเร็จ
- PostgreSQL 16 ทำงานได้
- Flyway migration ทำงานถึง version 19
- Runtime services ที่ตรวจสอบแล้ว:

| Service | สถานะ |
|---|---|
| Backend | healthy |
| Frontend | healthy |
| PostgreSQL | healthy |

Endpoints สำหรับทดสอบในเครื่อง:

- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8080/actuator/health`

### Test และ Verification

Backend:

```text
Tests run: 221
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

Frontend:

- `npm run type-check` ผ่าน
- `npm run lint` ผ่าน
- `npm run test:unit -- --run` ผ่าน: 3 tests
- `npm run build` ผ่าน

เพิ่มเติม:

- `git diff --check` ผ่าน
- Docker Compose config ผ่าน
- Docker image build ผ่าน
- Backend health ตอบ `UP`
- Frontend ตอบ HTTP `200`
- ทดสอบ Login ด้วย username และ email ผ่านจริงทั้ง Manager และ Therapist

### Dev/Test Accounts

| Username | Email | Password | Role |
|---|---|---|---|
| `manager` | `manager@clinic.local` | `manager123` | `MANAGER` |
| `therapist` | `therapist@clinic.local` | `therapist123` | `THERAPIST` |

Therapist ถูกผูกกับ Staff:

- Employee Code: `THP-001`
- Name: `Therapist Demo`

บัญชีเหล่านี้ใช้สำหรับ Development/Test เท่านั้น ต้องเปลี่ยนก่อนใช้งานจริง

บัญชีและ Staff ของ Therapist ถูกเก็บเป็น Flyway seed migration ใน `V20__seed_dev_test_accounts.sql` จึงถูกสร้างอัตโนมัติเมื่อเริ่มฐานข้อมูลใหม่จาก code

### Pipeline as Code

มี GitHub Actions workflow ดังนี้:

- `.github/workflows/backend-ci.yml`
  - Java 17
  - Maven `verify`
- `.github/workflows/frontend-ci.yml`
  - Node 24
  - `npm ci`
  - Unit test
  - Production build
  - Playwright E2E
- `.github/workflows/docker-ci.yml`
  - Validate Docker Compose
  - Build application images

Workflow รองรับ branch `feat/**`, `feature/**` และ `develop`

## Git และการส่งงาน

Commits หลัก:

- `4d0caa5 feat: connect frontend to backend`
- `d1fb242 fix: support username and email login`

Branch ถูก push ขึ้น remote แล้ว และ working tree ล่าสุดไม่มีไฟล์ค้าง

## ปัญหาและข้อจำกัดที่ยังเหลือ

- GitHub Actions ถูกกำหนดและ push แล้ว แต่ยังไม่ได้ยืนยันผล run จาก `gh run list` ใน environment นี้ เนื่องจาก GitHub CLI แจ้งว่ายังไม่ได้ authenticate
- Real PostgreSQL CRUD หลัง migration V18/V19 ยังควรทดสอบซ้ำให้ครบทุก endpoint ก่อน release
- Commission Report ยังต้องตรวจให้ผูกกับ frozen rate ของ Course ครบทุก sales flow
- Sale flow ยังไม่ได้ส่ง seller/case owner เข้า Course Purchase ครบทุกกรณี
- Shared Course/member allocation ยังไม่ครบ
- Refund/Cancel commission adjustment และ audit trail ยังไม่ครบ
- Employee termination และ outstanding commission policy ยังไม่มี
- Admin override สำหรับแก้ rate หลังปิดรอบยังไม่มี
- Course Commission Detail ยังมี fallback สำหรับ route ที่ไม่ใช่ numeric
- Back Office บางหมวดยังมี localStorage fallback เนื่องจาก Backend ยังไม่มี endpoint ครบ
- `POST /api/staff` พบปัญหา JPA detached entity และควรแก้ในรอบถัดไป
- มี warning ที่ไม่ทำให้ build fail เรื่อง H2/Flyway version และ Vite config import extension

## คำสั่งเริ่มระบบ

```powershell
docker compose -p sabyeguyz-clinic_erp -f deploy/docker-compose.yml up -d
docker compose -p sabyeguyz-clinic_erp -f deploy/docker-compose.yml ps
```

ไม่ควรใช้ `docker compose down -v` หากต้องการเก็บข้อมูล PostgreSQL เดิมไว้
