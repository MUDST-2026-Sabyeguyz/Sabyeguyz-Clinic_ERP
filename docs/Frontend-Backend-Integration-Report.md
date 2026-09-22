# Frontend–Backend Integration Report

วันที่: 11 กันยายน 2026  
Branch: `feat/connect-to-backend`

## ผลการ implement

- เพิ่ม `frontend/src/api/client.ts` เป็น API client กลาง แนบ `Authorization: Bearer <JWT>` กับ protected endpoint ทุก request, แปลง API error และส่งกลับหน้า login เมื่อได้ `401`
- เปลี่ยน login ให้เรียก `POST /api/auth/login` และเก็บ token/user จาก backend; logout ล้าง session
- เชื่อมหน้าผู้ป่วยกับ `GET /api/patients`, `GET /api/patients/{id}`, `POST /api/patients`
- เชื่อมหน้าจัดการนัดหมายกับ search/create/reschedule และ lifecycle actions ของ `/api/appointments`
- เชื่อมบริการ/คอร์ส/โอนสิทธิ์/ธุรกรรมกับ catalog, course ledger, course transfer, payment และ receipt endpoints
- เชื่อม dashboard และรายงานรายได้, course balance, transfer history และ staff sales กับ backend reports
- เชื่อมการอ่าน/จัดการสาขาและช่องทางชำระเงินใน Back Office เท่าที่ backend รองรับ
- เพิ่มและปรับ unit test ของ patient API ให้ตรวจการส่ง JWT

## ปัญหาและข้อจำกัดที่พบ

| ระดับ | ปัญหา | ผลกระทบ |
|---|---|---|
| P1 | `POST /api/patients` ต้องการ `username`, `password`, `email` และข้อมูลผู้ติดต่อฉุกเฉินที่ UI เดิมไม่มี | เพิ่ม field เหล่านี้ในฟอร์มแล้ว; ผู้ใช้ต้องกรอกข้อมูลให้ครบก่อนบันทึก |
| แก้แล้ว | เพิ่ม `PUT /api/patients/{id}` | หน้า profile แก้ไขและบันทึกข้อมูลจริงได้ |
| แก้แล้ว | เพิ่ม commission scheme update/deactivate/reactivate และ report | หน้า tier persist ได้; รายงานคำนวณจากยอดขายจริง |
| แก้แล้ว | เพิ่ม CRUD ห้อง/ทรัพยากรและ master data พร้อม migration V17 | Back Office อ่าน/เพิ่ม/แก้ไขข้อมูลผ่าน backend |
| P2 | `GET /api/patients` ส่งเฉพาะ id, HN, ชื่อ, โทรศัพท์, active | หน้าค้นหาผู้ป่วยไม่สามารถกรอง/แสดงสาขาและวันเกิดจาก response นี้ได้ |
| P2 | `GET /api/patients` ไม่ส่ง branch ของผู้ป่วย และ sale request ต้องใช้ `branchCode` | หน้าบริการใช้ `RP` เป็นค่าเริ่มต้นจน backend เพิ่ม branch ใน search response หรือมี patient profile lookup ก่อนขาย |
| P2 | Backend มี lifecycle action นัดหมายแบบเจาะจง ไม่มี generic status update | dropdown จะเรียกเฉพาะ action ที่ backend รองรับ; การเลือก `RESCHEDULED` จาก dropdown ต้องใช้ฟอร์ม reschedule |
| แก้แล้ว | เพิ่ม endpoint เปิดใช้งาน payment method กลับมา | Back Office ปิดและเปิดกลับได้ |
| แก้แล้ว | เพิ่มสูตรค่ามือ Treatment แบบ FIXED/PERCENTAGE, CAP_AT_COMMISSION และ Allocation Ledger ต่อ Visit | เก็บ Gross, Treatment Fee และ Owner Net แยกกันตามสูตรใน requirement |
| แก้แล้ว | เพิ่ม Commission Closing snapshot, freeze rate ต่อ Course และ Course Ledger Detail | ปิดเดือนแล้ว Course ใช้ locked rate เดิม |
| แก้แล้ว | ปิด `open-in-view` ทั้ง main/test profile และเปลี่ยน auth endpoint เป็น `permitAll` | ลด warning จาก Spring Security/JPA |
| P3 | `npm ci` พบ dependency audit 2 รายการระดับ moderate และ engine warning กับ Node `v24.13.0` | ควรอัปเดต dependency/Node ตามนโยบายก่อน release |

## Verification

- `npm run type-check` ผ่าน
- `npm run lint` ผ่าน
- `npm run test:unit -- --run` ผ่าน: 1 test file, 3 tests
- `npm run build` ผ่าน
- `git diff --check` ผ่าน
- Maven 3.9.11 portable + Java 17: `mvn test` ผ่าน 219 tests, 0 failures, 0 errors, 0 skipped
- PostgreSQL 16 CRUD จริงผ่าน HTTP/JWT: branch, resource, master data, payment method, patient และ commission scheme
- ทดสอบ create/read/update/deactivate/reactivate ตาม endpoint ที่รองรับ และเรียก commission report สำเร็จ
- ใช้ Compose project แยก `clinic-erp-crud-test`; ลบ container/network/volume ทดสอบแล้ว ไม่แตะฐานข้อมูลเดิม
