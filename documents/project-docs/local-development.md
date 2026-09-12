# Local Development — Monorepo

คู่มือสำหรับรัน Frontend, Backend และ PostgreSQL จาก repository เดียว

## Clone และโครงสร้าง

```bash
git clone https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP.git
cd Sabyeguyz-Clinic_ERP
```

```text
Sabyeguyz-Clinic_ERP/
├── backend/
├── frontend/sabyeguyz/
├── deploy/docker-compose.yml
└── docs/
```

## วิธีที่แนะนำ: Docker Compose

ต้องเปิด Docker Desktop และใช้ Linux containers:

```bash
docker compose -f deploy/docker-compose.yml up --build -d
docker compose -f deploy/docker-compose.yml ps
```

รอให้ `postgres` healthy และ Backend health เป็น `UP` ก่อนใช้งาน:

- Frontend: http://localhost:5173
- Patients: http://localhost:5173/patients
- Appointments: http://localhost:5173/appointments
- Backend health: http://localhost:8080/actuator/health

หยุดระบบโดยเก็บข้อมูลไว้:

```bash
docker compose -f deploy/docker-compose.yml down
```

อย่าใช้ `down -v` หากต้องการเก็บฐานข้อมูลใน local volume

## วิธีพัฒนาแยกบริการ

```bash
docker compose -f deploy/docker-compose.yml up -d postgres
cd backend
mvn spring-boot:run
```

เปิด Terminal ใหม่เพื่อรัน Frontend:

```bash
cd frontend/sabyeguyz
npm ci
npm run dev
```

Frontend ใช้ `http://localhost:5173` และเรียก Backend ที่ `http://localhost:8080` ผ่าน `VITE_API_BASE_URL`

## ตรวจสอบคุณภาพ

```bash
cd backend
mvn test
cd ../frontend/sabyeguyz
npm run test:unit -- --run
npm run build
npm run test:e2e -- --project=chromium
```

## Port อื่น

```powershell
$env:FRONTEND_PORT = '15173'
$env:BACKEND_PORT = '18080'
$env:POSTGRES_PORT = '15432'
docker compose -p clinic-check -f deploy/docker-compose.yml up --build -d
```

## Architecture

```text
Browser :5173 → Frontend nginx :80 → backend:8080 → postgres:5432
```

nginx รองรับ Vue history fallback และส่ง `/api/` ไป Backend ใน Compose network
