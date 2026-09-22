# Test Execution Report (historical)

วันที่ตรวจสอบ: 12 กันยายน 2026
Branch เดิม: `feat/connect-endpoint`
Commit: `77bcbf0 Create clinic_erp_demo.sql`

เอกสารนี้เก็บไว้เป็นหลักฐานของ branch เดิม ไม่ใช่ผลทดสอบล่าสุดของ `xan` หรือ `xan-database-connect`.

## สรุปผล

| รายการ | ผลลัพธ์ |
|---|---:|
| Backend test files | 24 |
| Backend tests | 221 |
| Failures | 0 |
| Errors | 0 |
| Frontend unit tests | 3 ผ่าน |
| Frontend type-check | ผ่าน |
| Frontend production build | ผ่าน |
| Backend runtime health | `UP` |

## คำสั่งที่รัน

```powershell
# backend
mvn -q test '-Dstyle.color=never'

# frontend
npm run test:unit -- --run
npm run type-check
npm run build
```

## ขอบเขต Backend ที่ทดสอบ

### API พื้นฐานและ Authentication

- `HelloControllerTest`: ตรวจ endpoint พื้นฐาน
- `AuthServiceTest`: ตรวจการ authenticate และการจัดการ password/role

### Patient และ HN

- `HnGeneratorTest`: ตรวจการสร้าง HN และ running number
- `PatientRegistrationServiceTest`: ตรวจการลงทะเบียนผู้ป่วยและ validation
- `PatientControllerTest`: ตรวจ create patient และ error response
- `PatientQueryApiTest`: ตรวจค้นหาผู้ป่วย, pagination, query validation และกรณีไม่พบข้อมูล
- `PatientOpenApiTest`: ตรวจ Swagger/OpenAPI contract ของ patient endpoints รวม branch filter

### Appointment

- `AppointmentBookingServiceTest`: ตรวจการสร้างนัดหมายและ validation
- `AppointmentApiTest`: ตรวจ API การสร้าง/อ่านนัดหมาย
- `AppointmentLifecycleApiTest`: ตรวจการเปลี่ยนสถานะนัดหมาย
- `AppointmentStateTest`: ตรวจ state transition
- `AppointmentQueryServiceTest`: ตรวจค้นหาและแบ่งหน้านัดหมาย
- `AppointmentRepositoryTest`: ตรวจ repository query
- `AppointmentRescheduleServiceTest`: ตรวจการเลื่อนนัด
- `AppointmentConcurrencyTest`: ตรวจการป้องกันรายการเวลาซ้ำภายใต้ concurrent request

### Course Ledger

- `CourseLedgerServiceTest`: ตรวจซื้อ/ใช้สิทธิ์คอร์สและยอดคงเหลือ
- `CourseLedgerApiTest`: ตรวจ course purchase, movement และ transfer API
- `CourseLedgerConcurrencyTest`: ตรวจการใช้สิทธิ์พร้อมกันไม่ให้ยอดติดลบ
- `CourseRepositoryTest`: ตรวจการค้นหา course

### Sale และ Payment

- `SaleServiceTest`: ตรวจสร้างรายการขาย, payment และการ reverse/void
- `SaleApiTest`: ตรวจ sale/receipt API และ validation
- `SaleConcurrencyTest`: ตรวจการทำรายการพร้อมกันและ course entitlement

### Reports

- `ReportServiceTest`: ตรวจการคำนวณรายงานรายได้, ยอดขาย และ commission
- `ReportApiTest`: ตรวจ report endpoints และกรณี parameter ไม่ถูกต้อง

## สิ่งที่ตรวจนอก Backend

- Frontend unit test ของ API patient: 3 tests ผ่าน
- Vue type-check ผ่าน
- Vite production build ผ่าน
- Docker Compose build สำเร็จ
- Flyway migration ใน PostgreSQL local ผ่านถึง V26
- `GET /actuator/health` ได้ HTTP 200 และ `{"status":"UP"}`
- `GET http://127.0.0.1:5173/staff/courses` ได้ HTTP 200

## ข้อจำกัดการทดสอบ

- Backend Maven tests ใช้ test profile และฐานข้อมูล H2 จึงไม่ใช่ integration test กับ PostgreSQL Docker โดยตรง
- Playwright E2E ถูกเตรียมและติดตั้ง Chromium แล้ว แต่เปิด browser process ไม่ได้ด้วย environment error `spawn UNKNOWN`
- ยังไม่ได้ทดสอบ workflow หลัง login ใน browser เพราะไม่มี credential ที่ยืนยันได้ใน session
- การตรวจ CI/GitHub Actions ไม่ได้สรุปจาก local test run นี้
