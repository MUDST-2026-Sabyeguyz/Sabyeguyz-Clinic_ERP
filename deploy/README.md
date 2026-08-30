# Deploy Repository

Repository สำหรับเอกสารและไฟล์ Deployment ของ Physiotherapy Clinic ERP

## ขอบเขตของ Repo

- เก็บแนวทาง Docker, Kubernetes และ Deployment ที่ฝ่าย DevOps รับผิดชอบ
- ตอนนี้ยังเป็นพื้นที่เตรียมงานและเอกสาร ยังไม่กำหนด Implementation เพิ่ม
- ห้ามใส่ Secret, Password หรือข้อมูลจริงลงใน Repository

## การทำงานร่วมกัน

ใช้ Branch `main`, `develop` และ `feature/<feature-name>` ตาม [Branching Guide](https://github.com/MUDST-2026-Sabyeguyz/docs/blob/main/branching.md)

## Context กลาง

อ่าน [Context กลาง](https://github.com/MUDST-2026-Sabyeguyz/docs/blob/main/context.md) ก่อนออกแบบหรือแก้ไข Deployment

## Local Compose Baseline

จากโฟลเดอร์ `deploy` ให้รัน:

```bash
docker compose up --build
```

บริการเริ่มต้น:

- PostgreSQL ที่ `localhost:5432`
- Backend ที่ `localhost:8080`
- Health check: `http://localhost:8080/actuator/health`

Password ในไฟล์นี้เป็นค่า local สำหรับ Development เท่านั้น ห้ามนำไปใช้ใน Environment จริง และห้ามเก็บ Secret จริงใน Repository
