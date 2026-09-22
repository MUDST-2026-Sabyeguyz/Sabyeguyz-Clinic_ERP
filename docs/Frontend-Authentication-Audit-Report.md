# รายงานตรวจสอบการยืนยันตัวตนของ Frontend

วันที่ตรวจสอบ: 11 กันยายน 2026  
ขอบเขตเดิม: `frontend/src` เทียบกับ security configuration ของ backend

## สรุปผล

พบปัญหาหลัก 1 จุดที่ทำให้เกิด `401 Unauthorized` และพบประเด็นเชิงระบบเพิ่มเติมว่า frontend หลายหน้ายังไม่ได้เชื่อมต่อ backend จริง

| ระดับ | จุดตรวจสอบ | ผลตรวจสอบ |
|---|---|---|
| P1 | ลงทะเบียนผู้ป่วย | เรียก `POST /api/patients` แต่ไม่ส่ง JWT จึงได้ `401` |
| P1 | Login | เป็น mock login ไม่เรียก `POST /api/auth/login` และไม่เก็บ token |
| P1 | หน้า staff อื่น ๆ | ใช้ mock data/localStorage จึงยังไม่พบ request ที่ขาด JWT แต่ยังไม่ได้เชื่อม backend |

## หลักฐานจาก request ที่แจ้งมา

Request ไปที่ `POST http://localhost/api/patients` ได้ response `401` พร้อม header `WWW-Authenticate: Bearer` และไม่มี header `Authorization` ใน request

ดังนั้น payload ผู้ป่วยยังไม่ใช่สาเหตุของ error นี้ เพราะ request ถูกปฏิเสธก่อนเข้าสู่ validation และการบันทึกฐานข้อมูล

## รายละเอียดตามไฟล์

### 1. ระบบ login ยังเป็น mock

ไฟล์เดิม: `frontend/src/features/auth/views/StaffLoginView.vue`

ฟังก์ชัน `signIn()` ทำเพียง:

- ตรวจว่ามี username/password
- บันทึก session ที่ได้จาก backend ลง localStorage
- redirect ไปหน้าถัดไป

ไม่มีการเรียก `/api/auth/login` และไม่มีการบันทึก `response.token`

### 2. API ลงทะเบียนผู้ป่วยไม่ส่ง token

ไฟล์เดิม: `frontend/src/api/patients.ts`

ฟังก์ชัน `registerPatient()` ส่งเฉพาะ header:

```ts
{ 'Content-Type': 'application/json' }
```

ต้องส่งเพิ่ม:

```ts
Authorization: `Bearer ${token}`
```

โดย `token` ต้องมาจากผลลัพธ์ของ `/api/auth/login`

### 3. หน้าอื่นยังเป็น mock/localStorage

จากการค้นหา `fetch`, `/api/`, `Authorization` และ `axios` ใน `frontend/src` รายงานเดิมพบ API call จริงเพียง `registerPatient()` เท่านั้น

หน้าที่พบว่าใช้ mock data หรือ localStorage ได้แก่:

- `features/patients/views/PatientSearchView.vue`
- `features/patients/views/PatientProfileView.vue`
- `features/appointments/views/AppointmentView.vue`
- `features/services/views/servicesView.vue`
- `features/courses/views/CourseActionView.vue`
- `features/transactions/views/TransactionView.vue`
- `features/commission/views/CommissionView.vue`
- `features/reports/views/ReportsView.vue`
- `features/dashboard/views/DashboardView.vue`
- `features/settings/views/BackOfficeView.vue`

หน้ากลุ่มนี้ยังไม่สามารถสรุปว่า “ส่ง JWT ไม่ครบ” ได้ เพราะยังไม่ได้ยิง backend จริง แต่เมื่อเชื่อม API แล้วต้องใช้รูปแบบ authenticated request เดียวกัน

## การบังคับใช้ของ Backend

ไฟล์: `backend/src/main/java/com/sabyeguyz/clinic/config/SecurityConfig.java`

Backend อนุญาตโดยไม่ต้อง login เฉพาะ:

- `/api/auth/login`
- `/api/auth/register`
- health check และเอกสาร API

ทุก endpoint อื่นใช้ `.authenticated()` ดังนั้น endpoint ผู้ป่วย นัดหมาย การรักษา การขาย คอร์ส รายงาน และข้อมูลตั้งค่าต้องมี Bearer token

## ข้อเสนอแนะในการแก้ไข

1. เปลี่ยน `StaffLoginView.vue` ให้เรียก `/api/auth/login`
2. เก็บ JWT เช่น `localStorage.setItem('authToken', response.token)`
3. สร้าง shared API client หรือ helper สำหรับเติม `Authorization` ทุก request
4. ให้ logout ลบ token และ session ที่เก็บไว้
5. เมื่อเริ่มเชื่อมหน้า mock เข้ากับ backend ให้ตรวจทุก request ว่ามี token และจัดการกรณี `401` โดยส่งกลับหน้า login
6. ไม่ควรใช้ role จาก localStorage เป็นหลักในการอนุญาตสิทธิ์ เพราะสิทธิ์จริงควรอ้างอิงจาก JWT/backend

## ข้อสรุป

Error ที่หน้า `/staff/patients/new` แก้ที่ frontend เป็นหลัก โดยต้องทำ login จริงและส่ง JWT ไปกับ API ส่วนหน้าอื่นยังไม่เกิด error แบบเดียวกันจาก request จริง เพราะยังใช้ mock/localStorage และยังไม่เชื่อม backend อย่างสมบูรณ์
