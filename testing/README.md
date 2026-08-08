# Testing Strategy

## ระยะเริ่มต้น

- Frontend: Type check, Unit Test และ Build
- Backend: Spring Context Test และ Controller Test
- Deploy: ตรวจ Docker Compose configuration และ Kubernetes file set

## ระยะถัดไป

- Unit Test สำหรับ Business Rule สำคัญ
- Integration Test กับ Database
- E2E Test ตาม Workflow ที่ BA ยืนยัน

การ Build ผ่านไม่ใช่หลักฐานว่า Docker หรือ Kubernetes Runtime ทำงานแล้ว ต้องตรวจ Runtime แยกต่างหาก
