# QA and Testing Strategy

เอกสารนี้เป็น baseline สำหรับ QA, CI และ Integration ของทีม โดยยึด Workflow และ Acceptance Criteria ที่ BA/PM ยืนยันแล้วเป็นหลัก หาก Requirement ยังไม่ยืนยัน ให้ระบุเป็น `Assumption` หรือ `Open Question` ก่อนเขียน Test

## Quality Gate ก่อน Merge

ทุก Pull Request ต้องผ่านเงื่อนไขต่อไปนี้ตามส่วนของระบบที่ถูกแก้:

| ส่วน | Quality Gate ระยะเริ่มต้น |
|---|---|
| Frontend | `npm ci`, type-check, Unit Test และ production build |
| Backend | JUnit/JaCoCo และ Cypress HTTP บน PostgreSQL ของ clinicv2; ใช้ entry point ด้านล่าง |
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
| Appointment เวลาว่าง | สร้างสถานะ `CONFIRMED` |
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

## Current baseline — Phase 1 review 16 Oct 2026

ชุดตรวจ current `clinicv2` ใช้คำสั่งจาก repo root:

```bash
node scripts/verify-phase1.mjs
node scripts/verify-compose.mjs
```

ต้องมี Node 24.12+/npm และ Docker Linux engine; รายละเอียด clone, ports, demo login, database isolation และ troubleshooting อยู่ใน [คู่มือผู้ตรวจ](../phase1/REVIEW_GUIDE_TH.md) ผลจริงและขอบเขตที่ยังเปิดอยู่ใน [validation](../phase1/VALIDATION_TH.md)

- JUnit regressions ตรวจ Bangkok date boundaries, HN sequence/overflow และ confirmed baht discount แยกจาก HTTP/browser suites
- Backend HTTP Cypress v2 ใช้ production Flyway/PostgreSQL 16 กับ test-only fixtures และตรวจ security/permissions ผ่าน HTTP จริง
- Frontend ใช้ Vitest กับ V8 coverage, type-check/build, ESLint/Oxlint และ Cypress browser v2 กับ API/PostgreSQL จริง ไม่ใช้ mocked API แทน integration ทั้งชุด
- JaCoCo รวม production clinicv2 ทุก class รวมที่ยังไม่ cover และเก็บ unit + instrumented app execution; frontend V8 เป็น unit coverage รวม TS/Vue ที่ไม่ได้ import ด้วย ไม่ใช่ browser coverage
- ไม่มี numeric coverage threshold ที่อนุมัติแล้ว ต้องรายงาน denominator และ gaps ตามจริง
- Maven verify อย่างเดียวไม่รัน Cypress ทั้งชุด; npm test เดิมใน backend เป็น legacy entry point จึงไม่ใช้แทน current-v2 gate
- Pipeline `.github/workflows/phase1-review.yml` เรียกคำสั่งเดียวกันและเก็บ logs/JUnit/coverage แม้ fail; ต้องผูก remote run กับ SHA จริง
- Compose gate build images ใหม่ใน project/volume สังเคราะห์เฉพาะ run แล้วตรวจ health, app/JS, 401, normal login และ authenticated patient read
- การมี Kubernetes manifests ไม่ใช่หลักฐานว่า current app revision deploy แล้ว ต้องเก็บ image digest/deployment/Ingress evidence แยก

เอกสาร legacy เช่น [Backend Cypress API tests](../../backend/cypress/README.md) และ [V2 Runtime CI](2026-10-13-V2-Runtime-CI.md) ใช้อ้างประวัติ/รายละเอียด ไม่ใช้ counts ของ revision เก่าแทน source ที่ตรวจรอบนี้

## Latest integration trial

หลักฐาน merge dev-backed ล่าสุดก่อน feat/database และการตรวจ v2 runtime/legacy import ดู [Latest Integration Trial Merge](2026-10-13-Latest-Integration-Trial-Merge.md)

## Manual validation ของทีม

[Validation checklist](https://docs.google.com/spreadsheets/d/1tJqQv1AxWR1F_JMyo8AcWGv3zZDmyAlNlDNmukJJO2I/edit?usp=sharing) เป็นรายการให้ทีมตรวจรับเพิ่มเติม ลิงก์นี้รักษามาจาก main; รอบนี้ไม่ได้อ่านหรือยืนยันผลในชีต และไม่ใช้แทน automated evidence
