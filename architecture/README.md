# Architecture

## Baseline

ระบบเริ่มต้นเป็น Multi-Repository และ Modular Monolith:

```text
Vue Frontend → Spring Boot REST API → PostgreSQL
        ↘ Docker Compose / Minikube ↙
```

Repository:

- `frontend`: Vue 3 + Vite
- `backend`: Spring Boot REST API
- `deploy`: Docker Compose และ Kubernetes manifests
- `docs`: เอกสารและ Context กลาง

## หลักการ

- แยก Controller, Service และ Data Access ตามความเหมาะสม
- รักษา API Contract ที่ทีมยืนยันแล้ว
- ไม่เริ่มเป็น Microservices จนกว่าจะมี Requirement รองรับ
- ระบบ local ไม่ควรพึ่ง External API หรือ Cloud Service โดยไม่จำเป็น
