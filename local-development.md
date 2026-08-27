# Local Development — Full System

คู่มือสำหรับรัน **Frontend + Backend + PostgreSQL** ร่วมกัน

> ใช้เมื่อเริ่ม Integration ระหว่าง Frontend และ Backend

## โครงสร้าง

```text
project/
├── backend/
├── frontend/
│   └── sabyeguyz/
├── deploy/
└── docs/
```

## Clone

```bash
mkdir project
cd project

git clone https://github.com/MUDST-2026-Sabyeguyz/backend.git
git clone https://github.com/MUDST-2026-Sabyeguyz/frontend.git
git clone https://github.com/MUDST-2026-Sabyeguyz/deploy.git
```

## 1. PostgreSQL

เปิด Docker Desktop

จาก `deploy`:

```bash
docker compose up postgres
```

## 2. Backend

เปิด Terminal ใหม่:

```bash
cd backend
mvn spring-boot:run
```

ตรวจสอบ:

```text
http://localhost:8080/actuator/health
```

## 3. Frontend

เปิด Terminal ใหม่:

```bash
cd frontend/sabyeguyz
npm install
npm run dev
```

เปิด:

```text
http://localhost:5173
```

## Architecture

```text
Browser
   ↓
Frontend :5173
   ↓ REST API
Backend :8080
   ↓
PostgreSQL :5432
```

## การพัฒนาแยกกัน

Frontend Development:

```text
Frontend + Mock Data
```

Backend Development:

```text
Backend + PostgreSQL
```

Integration:

```text
Frontend + Backend + PostgreSQL
```

Frontend  **ไม่ต้อง Clone Backend** จนกว่าจะต้อง Integration

## หยุดระบบ

Frontend / Backend:

```text
Ctrl + C
```

PostgreSQL:

```bash
docker compose down
```

> อย่าใช้ `docker compose down -v` ถ้าจะเก็บข้อมูล Local Database
