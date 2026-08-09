# QA and Testing Strategy

เอกสารนี้เป็น baseline สำหรับ QA, CI และ Integration ของทีม โดยยึด Workflow และ Acceptance Criteria ที่ BA/PM ยืนยันแล้วเป็นหลัก หาก Requirement ยังไม่ยืนยัน ให้ระบุเป็น `Assumption` หรือ `Open Question` ก่อนเขียน Test

## Quality Gate ก่อน Merge

ทุก Pull Request ต้องผ่านเงื่อนไขต่อไปนี้ตามส่วนของระบบที่ถูกแก้:

| ส่วน | Quality Gate ระยะเริ่มต้น |
|---|---|
| Frontend | `npm ci`, type-check, Unit Test และ production build |
| Backend | Compile, Unit Test และ Spring Context Test เมื่อมีโค้ดแล้ว |
| Docs/API | ตรวจว่า User Story, Acceptance Criteria และ API Contract สอดคล้องกัน |
| Deploy | ตรวจ Docker Compose และ Kubernetes manifest เมื่อมีไฟล์แล้ว |

ห้ามใช้การข้าม Test หรือ `--force` เป็นวิธีแก้ปัญหาใน CI โดยไม่บันทึกเหตุผลและการอนุมัติของทีม

## Test Levels

### Unit Test

ทดสอบกฎของแต่ละ Component/Service แยกจากระบบอื่น เช่น:

- ค้นหาและสร้าง Patient ตามเงื่อนไขที่กำหนด
- ป้องกัน Appointment เวลาซ้ำ
- ป้องกันการใช้ Course ที่หมดอายุหรือไม่มีจำนวนครั้งเหลือ
- ป้องกัน Payment ติดลบและตรวจยอดรวม
- คำนวณ Commission ตาม Rule ที่ยืนยันแล้ว
- Reverse ต้องสร้าง Corrective Transaction และเก็บเหตุผล ไม่ลบรายการเดิม

### Integration Test

ทดสอบการทำงานร่วมกันของ Controller, Service, Repository, Security และ Database โดยเริ่มจาก:

1. สร้าง Patient
2. สร้าง Appointment และตรวจเวลาซ้ำ
3. ซื้อ Course และบันทึก Payment
4. ใช้ Course และหักยอดคงเหลือ
5. Reverse Payment/Transaction

### End-to-End Test

หลัง API และ UI ของ MVP พร้อม ให้ทดสอบ Workflow หลัก:

```text
Login
→ Register Patient
→ Create Appointment
→ Check-in Patient
→ Record Treatment
→ Purchase Course/Payment
→ View Receipt
→ View Patient History
```

E2E ต้องตรวจอย่างน้อย Success Case, Validation Error, Duplicate/Conflict Case และ Permission Error ตาม Role ที่ยืนยันแล้ว

## Integration Test Matrix

| Scenario | Expected Result |
|---|---|
| Patient ใหม่ | สร้างข้อมูลสำเร็จและได้ ID |
| Patient ซ้ำ | แจ้งเตือน/ปฏิเสธตาม Rule ที่ยืนยันแล้ว |
| Appointment เวลาว่าง | สร้างสถานะ `SCHEDULED` |
| Appointment เวลาซ้ำ | ปฏิเสธและไม่สร้างข้อมูลซ้อน |
| Course หมดอายุ | ปฏิเสธการใช้งาน |
| Course เหลือศูนย์ | ปฏิเสธการใช้งาน |
| Payment ติดลบ | ปฏิเสธด้วย Validation Error |
| Reverse Transaction | รายการเดิมยังอยู่และมีรายการแก้ไขพร้อม Audit |

## Environment Verification

ผลการตรวจต้องแยกตามระดับ ห้ามสรุปว่า Deploy สำเร็จเพียงเพราะ Build ผ่าน:

- **Local:** รัน Test และตรวจการทำงานบนเครื่องนักพัฒนา
- **CI:** ตรวจว่า Pipeline ทำซ้ำได้ใน clean environment
- **Docker:** Build image, start Compose และทดสอบ health/API จริง
- **Kubernetes:** Apply manifest, ตรวจ Pod/Service/Ingress และเรียกใช้งานผ่าน Runtime จริง

## Current Baseline

- Frontend มี type-check, Unit Test, Build และ Playwright configuration แล้ว
- Backend มี Spring Boot baseline, `/api/hello`, `/actuator/health` และ Controller Test แล้ว
- Deploy มี Backend Dockerfile และ Docker Compose สำหรับ PostgreSQL/Backend แล้ว แต่ยังไม่มี Kubernetes manifest
- Database ยังไม่ได้เชื่อมกับ Business Entity จึงยังไม่ถือว่าเป็น Database Integration Test
- เมื่อมี API Contract ที่ BA/Backend ยืนยันแล้ว ให้เพิ่ม Contract Test ก่อนเชื่อม Frontend กับ Backend
