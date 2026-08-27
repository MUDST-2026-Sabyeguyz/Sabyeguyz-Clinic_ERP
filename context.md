# Physiotherapy Clinic ERP - Shared AI Context

<!-- Shared team context. Keep this file at the repository root. -->

> ไฟล์นี้เป็น Context กลางของทีมสำหรับใช้กับ AI ทุกตัว สมาชิกทุกคนควรแนบไฟล์นี้หรือคัดลอกเนื้อหาไปใช้ก่อนขอให้ AI วิเคราะห์ ออกแบบ หรือแก้ไขงาน เพื่อให้คำตอบสอดคล้องกัน

## 1. Project Overview

ทีมกำลังทำ Semester Project ชื่อ **Real-World DevOps in Action** คิดเป็น 30% ของคะแนนรายวิชา โดยเลือกหัวข้อ **Physiotherapy Clinic ERP**

เป้าหมายคือพัฒนาระบบบริหารคลินิกกายภาพบำบัดที่ใช้งานได้จริง ตั้งแต่การลงทะเบียนผู้ป่วย การนัดหมาย การรักษา การขายคอร์ส การชำระเงิน การคำนวณ Commission และการทำรายงาน โดยต้องแสดงกระบวนการพัฒนาซอฟต์แวร์แบบครบวงจร ตั้งแต่ UX/UI จนถึง CI/CD และ Kubernetes

ระบบต้องสามารถปรับเปลี่ยนตาม Requirements ที่อาจเพิ่มหรือเปลี่ยนแปลงระหว่างภาคการศึกษาได้ง่าย และต้องมี Test ที่ช่วยป้องกัน Regression

## 2. Mandatory Technology and Engineering Requirements

- Frontend: ใช้ Vue หรือ React
- Backend: ใช้ Spring Boot
- Backend ให้บริการผ่าน RESTful API
- มี Business Logic ในชั้น Service ไม่กระจายอยู่ใน Controller
- มี Unit Test, Integration Test และ End-to-End Test
- มี Dockerfile และ Docker Compose
- สามารถรันบน Minikube/Kubernetes ได้
- มี CI/CD Pipeline as Code บน GitHub Actions
- Pipeline ต้อง Build, Test, Build Docker Image และรองรับการ Deploy ตาม Phase
- ระบบต้องออกแบบให้ขยายได้ แต่ควรเริ่มจาก Modular Monolith ไม่ใช่ Microservices
- Database, Queue และ Storage เลือกใช้ได้เอง
- ระบบควรรันได้ภายในเครื่องโดยไม่พึ่งพา External API, Cloud Database, Cloud Storage, Firebase, External Auth หรือ SaaS
- Monitoring/Logging ถูกระบุใน Requirement ตอนต้น แต่กำหนดการภายหลังระบุว่า Observability ไม่จำเป็นแล้ว ให้ถือเป็น Optional จนกว่าจะยืนยันกับอาจารย์

## 3. Clinic ERP Scope

ระบบควรมี Module ต่อไปนี้:

1. Authentication and User Roles
2. Clinic and Branch Setup
3. Staff Management
4. Patient Registration
5. Appointment Management
6. Treatment Management
7. Treatment Course Management
8. Sales and Product Management
9. Payment and Receipt
10. Commission
11. Dashboard and Reports
12. Audit Log

รายละเอียด Workflow จากอาจารย์ยังอาจมีเอกสารเพิ่มเติม ดังนั้นห้ามสร้าง Business Rule ที่สำคัญโดยเดาเอง หากยังไม่มีข้อมูลยืนยัน ให้ระบุว่าเป็น Assumption และแยกออกจาก Requirement ที่ Confirmed แล้ว

## 4. Core Business Workflows

### Patient Registration

Staff ค้นหาผู้ป่วยจากชื่อ เบอร์โทร หรือรหัส หากไม่พบจึงสร้าง Patient Profile ใหม่ ระบบต้องตรวจสอบข้อมูลซ้ำและเก็บข้อมูลสำหรับเชื่อมโยงกับ Appointment, Treatment และ Payment

### Appointment

Staff เลือก Patient, Branch, Therapist, Service, วัน และเวลา ระบบต้องตรวจสอบเวลาซ้ำและเก็บสถานะ เช่น `SCHEDULED`, `CHECKED_IN`, `COMPLETED` และ `CANCELLED`

### Treatment

เมื่อผู้ป่วยเข้ารับบริการ Therapist บันทึกรายละเอียดการรักษาและบริการที่ได้รับ ระบบอาจเชื่อมโยงกับ Treatment Course และบันทึก Course Usage

### Course Purchase and Payment

เจ้าหน้าที่เลือก Patient และ Course ระบุจำนวนครั้ง ราคา และวันหมดอายุ จากนั้นสร้างรายการซื้อ รับชำระเงิน และออก Receipt พร้อมเพิ่มยอดคงเหลือของ Course

### Course Usage

ก่อนใช้ Course ระบบต้องตรวจสอบว่าคอร์สเป็นของผู้ป่วย จำนวนครั้งยังเหลือ และยังไม่หมดอายุ เมื่อใช้งานสำเร็จจึงบันทึก Usage และหักจำนวนครั้ง

### Reversal and Audit

Payment, Course Usage, Transfer, Cancellation และ Adjustment ที่มีผลต่อข้อมูลทางการเงินหรือยอดคงเหลือ ต้องไม่ถูกลบโดยตรง ควรสร้าง Reversal หรือ Corrective Transaction พร้อมเหตุผล ผู้ดำเนินการ และวันเวลา เพื่อให้ตรวจสอบย้อนหลังได้

## 5. Initial Roles

Role เบื้องต้นที่สามารถใช้เป็น Assumption ได้:

- `ADMIN`: จัดการ User, Branch, Service และดูข้อมูลรวม
- `RECEPTIONIST`: จัดการ Patient และ Appointment
- `THERAPIST`: บันทึก Treatment และ Course Usage
- `CASHIER`: จัดการ Sales, Payment และ Receipt

หากเอกสาร Workflow ของอาจารย์กำหนด Role อื่น ให้ยึดเอกสารนั้นเป็นหลัก

## 6. Suggested Initial Data Model

Entity เบื้องต้น:

- `User`
- `Role`
- `Branch`
- `StaffProfile`
- `Patient`
- `Service`
- `Appointment`
- `TreatmentRecord`
- `TreatmentCourse`
- `CoursePurchase`
- `CourseUsage`
- `Product`
- `Sale`
- `SaleItem`
- `Payment`
- `Receipt`
- `CommissionRule`
- `CommissionRecord`
- `AuditLog`

แนะนำใช้ PostgreSQL เพราะมีความเหมาะสมกับข้อมูลเชิงสัมพันธ์และ Transaction ระบบไม่จำเป็นต้องใช้ Redis, RabbitMQ หรือ Cloud Storage จนกว่าจะมี Requirement รองรับ

## 7. Suggested Frontend Pages

- Login
- Dashboard
- Appointment Calendar/List
- Patient List
- Patient Detail
- Register Patient
- Create/Edit Appointment
- Treatment Session
- Course Management
- Sales and Payment
- Receipt
- Reports
- Staff/User Management
- Audit Log

หลัก UX ที่ต้องรักษา:

- ค้นหา Patient ได้เร็ว
- แสดงสถานะด้วยสีและข้อความที่เข้าใจง่าย
- ตรวจสอบ Form ก่อนส่ง
- แสดง Loading, Error และ Empty State
- ป้องกันการกด Submit ซ้ำ
- ยืนยันก่อน Cancel หรือ Reverse Transaction
- แสดง Course Balance และ Expiry Date อย่างชัดเจน
- ลดจำนวน Click ในงานประจำวันของ Receptionist และ Cashier

## 8. Suggested API Areas

ตัวอย่าง API เบื้องต้น มีไว้เป็นแนวทาง ไม่ใช่ Contract ที่ตายตัว:

```text
POST   /api/auth/login
GET    /api/auth/me

GET    /api/patients
GET    /api/patients/{id}
POST   /api/patients
PATCH  /api/patients/{id}

GET    /api/appointments
POST   /api/appointments
PATCH  /api/appointments/{id}
POST   /api/appointments/{id}/check-in
POST   /api/appointments/{id}/complete
POST   /api/appointments/{id}/cancel

GET    /api/treatments
POST   /api/treatments
GET    /api/patients/{id}/treatments

GET    /api/courses
POST   /api/course-purchases
POST   /api/course-usages
POST   /api/course-transfers

POST   /api/payments
GET    /api/payments
POST   /api/payments/{id}/reverse
GET    /api/receipts/{id}

GET    /api/dashboard/summary
GET    /api/reports/appointments
GET    /api/reports/revenue
GET    /api/audit-logs
```

API ต้องใช้ DTO แยกจาก Entity, มี Validation, Global Exception Handler, Pagination/Search ตามความเหมาะสม และไม่ควรใส่ Business Logic ไว้ใน Controller

## 9. Suggested Repository Structure

แนะนำใช้ Monorepo เพื่อให้เริ่มต้นและทำ CI/CD ได้ง่าย:

```text
clinic-erp/
├── frontend/
├── backend/
├── deploy/
│   ├── docker-compose.yml
│   └── k8s/
├── docs/
│   ├── user-stories/
│   ├── api/
│   ├── architecture/
│   └── testing/
├── .github/
│   └── workflows/
├── README.md
└── .gitignore
```

## 10. Team Responsibilities

หากมีสมาชิก 7 คน ให้แบ่งเป็น:

1. Product Owner/Business Analyst: Requirement, User Story, Acceptance Criteria, Workflow และ Backlog
2. UX/UI Designer: Figma, User Flow, Design System และ Usability
3. Frontend Core: Login, Layout, Dashboard, Patient และ Staff
4. Frontend Transaction: Appointment, Treatment, Course, Payment และ Receipt
5. Backend Core: Authentication, User, Role, Branch, Patient และ Staff
6. Backend Transaction: Appointment, Treatment, Course, Payment และ Report
7. DevOps/QA/Integration: Test Strategy, CI/CD, Docker, Kubernetes, Integration และการรวมระบบ

หากมีสมาชิก 5 คน ให้รวม Product Owner กับ UX/UI, รวม Frontend เป็นทีมเดียว และรวม Backend Transaction กับ DevOps/QA โดยทุกคนยังต้องช่วย Code Review และ Test ใน Feature ของตนเอง

## 11. Testing Strategy

### Unit Test

ต้องครอบคลุมกฎสำคัญ เช่น:

- ห้ามจองเวลาซ้ำ
- ห้ามใช้ Course ที่หมดอายุ
- ห้ามใช้ Course ที่จำนวนครั้งเหลือศูนย์
- ห้ามชำระเงินติดลบ
- คำนวณยอดรวมถูกต้อง
- คำนวณ Commission ถูกต้อง
- Reverse ต้องเก็บรายการเดิมและสร้างรายการแก้ไข

### Integration Test

ทดสอบ Controller, Service, Repository, Security และ Database ร่วมกัน เช่น การสร้าง Patient, การจอง Appointment, การซื้อ Course, การใช้ Course และการ Reverse Payment

### E2E Test

อย่างน้อยต้องทดสอบ Flow นี้:

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

## 12. CI/CD and Deployment

### Initial Pipeline

- Checkout Code
- Install Dependencies
- Compile Backend
- Run Backend Unit Test
- Build Frontend
- Run Frontend Test

### Docker Phase

- Build Backend Image
- Build Frontend Image
- Start ด้วย Docker Compose
- Run Integration Test

### Kubernetes Phase

ควรมีอย่างน้อย:

- Namespace
- ConfigMap
- Secret
- PostgreSQL Deployment/Service
- Backend Deployment/Service
- Frontend Deployment/Service
- Ingress
- Persistent Volume หรือ Persistent Volume Claim ตามความจำเป็น
- Readiness/Liveness Health Check

## 13. Academic Milestones

- 15 Aug: Figma, UX/UI และ User Story เบื้องต้น
- 29 Aug: ปรับปรุง Figma, UX/UI, User Story และมี Code บางส่วน
- 12 Sep: Runnable Code, Unit Test และ GitHub Pipeline สำหรับ Build
- 26 Sep: Dockerfile, Docker Compose และ Pipeline สำหรับ Build Docker
- 17 Oct: Phase 1 เสร็จ, Presentation และ Deploy ไป Kubernetes ได้
- 31 Oct: ปรับปรุง Figma, User Story และ Code สำหรับ Phase 2
- 14 Nov: ระบบและเอกสารทั้งหมดเสร็จสมบูรณ์

## 14. Immediate Action Plan

### First Meeting

ทีมต้องตัดสินใจ:

- จำนวนสมาชิกจริง
- ชื่อกลุ่มและ Repository
- คนรับผิดชอบแต่ละฝ่าย
- MVP ที่จะส่งใน Phase 1
- Role เบื้องต้น
- Database และ Tech Stack
- วิธีใช้ Branch, Pull Request และ Code Review
- Workflow ที่ต้องถามอาจารย์เพิ่มเติม

### First Week

1. สร้าง Product Vision และ Scope
2. ทำ User Persona และ Actor
3. เขียน User Story และ Acceptance Criteria
4. วาด Main Workflow
5. ทำ Figma หน้า Login, Dashboard, Patient, Appointment, Treatment และ Payment
6. สร้าง Frontend และ Backend Hello World
7. ตั้ง PostgreSQL ด้วย Docker Compose
8. ทำ Health Check API
9. ทำ Patient Registration เป็น Feature แรก
10. ทำ Appointment เป็น Feature ถัดไป
11. เขียน Unit Test พร้อม Feature
12. เตรียมเอกสารสำหรับส่งวันที่ 15 Aug

## 15. Recommended MVP

MVP ควรเป็น Workflow ที่แสดงคุณค่าของ Clinic ERP ได้ชัดเจน:

```text
Login
→ Register Patient
→ Create Appointment
→ Check-in
→ Record Treatment
→ Purchase Course/Payment
→ View Patient History
→ Dashboard Summary
```

อย่าเริ่มจากทุก Module พร้อมกัน โดยเฉพาะ Commission, Course Transfer, Advanced Report และ Scheduled Reminder ควรทำหลังจาก Core Workflow เสถียรแล้ว

## 16. Definition of Done

Feature จะถือว่าเสร็จเมื่อ:

- มี User Story และ Acceptance Criteria
- มี Figma หรือ UI ที่เกี่ยวข้อง
- มี API และ Database ที่จำเป็น
- Frontend เรียก API ได้จริง
- มี Validation และ Error Handling
- มี Test ที่เหมาะสม
- ผ่าน Code Review
- มีเอกสารวิธีใช้งาน
- รันได้ตาม README
- ไม่ทำให้ Feature เดิมเสีย

## 17. Rules for AI Assistance

เมื่อใช้ AI ช่วยงาน Project นี้ ให้ AI ปฏิบัติตามกฎต่อไปนี้:

1. ตอบเป็นภาษาไทยเป็นหลัก และคงชื่อ Technology, API, Class และคำศัพท์ทางเทคนิคเป็นภาษาอังกฤษเมื่อเหมาะสม
2. อย่าสร้าง Requirement ใหม่โดยไม่มีเหตุผล ให้แยก `Confirmed Requirement`, `Assumption` และ `Open Question` ให้ชัดเจน
3. หาก Workflow จากอาจารย์ยังไม่ครบ ให้เสนอทางเลือกและระบุสิ่งที่ต้องยืนยันก่อน Implement
4. รักษา API Contract, ชื่อ Field และรูปแบบ Response ที่ทีมตกลงแล้ว
5. แนะนำวิธีที่ทำได้จริงภายในเวลาและทรัพยากรของทีม
6. หลีกเลี่ยง External Service และ Technology ที่ไม่จำเป็น
7. ให้ความสำคัญกับ Data Integrity, Security, Validation และ Auditability
8. ห้ามลบ Payment, Course Usage หรือ Transaction สำคัญโดยตรง ให้ใช้ Reversal/Corrective Transaction
9. เมื่อเสนอ Code ให้ระบุไฟล์ที่เกี่ยวข้อง วิธีรัน และ Test ที่ควรเพิ่ม
10. ห้ามสรุปว่าระบบ Deploy สำเร็จเพียงเพราะ Build ผ่าน ต้องแยก Local Test, Docker Test และ Kubernetes Runtime Test
11. เมื่อแก้ไขระบบ ให้ตรวจสอบผลกระทบต่อ Frontend, Backend, Database, Test, Docker และ Kubernetes
12. หากงานใหญ่ ให้แบ่งเป็น Phase และเริ่มจาก MVP ที่เล็กที่สุดแต่ทำงานครบ Flow

## 18. Open Questions

ต้องยืนยันกับอาจารย์หรือทีม:

- จำนวนสมาชิกจริงคือ 5 คน หรือ 6–8 คน
- Workflow ของ Clinic ฉบับเต็ม
- Role ที่ระบบต้องรองรับ
- ต้องมีหลาย Branch หรือไม่
- รูปแบบการคำนวณ Commission
- กฎการโอนและหมดอายุของ Course
- รูปแบบ Receipt และ Report
- Observability ถูกยกเลิกทั้งหมดหรือไม่
- Kubernetes ต้อง Deploy เฉพาะ Minikube หรือมี Environment อื่น
- Project และ Repository ต้องตั้งชื่อรูปแบบใด

## 19. Current Team Setup Status

### GitHub Organization

ทีมสร้าง Repository แยกไว้ 4 แห่งใน GitHub Organization แล้ว:

- `frontend`: Vue Frontend
- `backend`: Spring Boot Backend
- `deploy`: Docker Compose, Dockerfile และ Kubernetes Manifest
- `docs`: Figma Link, User Story, Architecture, API, Test Plan และเอกสารส่งงาน

โครงสร้างปัจจุบันเป็นแบบ **Multi-Repository** ไม่ใช่ Monorepo ดังนั้นเอกสารสำคัญและ Context ต้องประสานให้ตรงกันระหว่างทั้ง 4 Repository

### Frontend Setup

Frontend อยู่ที่:

```text
C:\Users\xanexer\Documents\.3-1\devops\project\frontend\sabyeguyz
```

สร้างด้วย Vue โดยเลือก Feature ดังนี้:

- JSX Support: ไม่เลือก
- Router: เลือก
- Pinia: เลือก
- Vitest: เลือก
- End-to-End Testing: เลือก
- E2E Framework: Playwright
- Oxfmt: ไม่เลือก
- Vue 3.6 Release Candidate: ไม่เลือก
- Example Code: เลือก No เพื่อให้มี Example Code สำหรับตรวจสอบการรันเบื้องต้น

### Current Installation Issue

ขณะรัน `npm install` ใน Frontend พบ Dependency Conflict:

```text
Found: oxlint@1.74.0
peer oxlint@"~1.73.0" from eslint-plugin-oxlint@1.73.0
```

สาเหตุคือ `oxlint` และ `eslint-plugin-oxlint` ใช้คนละ Minor Version ทำให้ npm ไม่สามารถ Resolve Dependency Tree ได้

แนวทางแก้ไขที่ควรใช้:

1. ตรวจสอบ `package.json` และจัดให้ `oxlint` กับ `eslint-plugin-oxlint` ใช้ Version Line เดียวกัน
2. ลบ `node_modules` และ `package-lock.json` เฉพาะเมื่อจำเป็น แล้วติดตั้งใหม่
3. ตรวจสอบว่า `npm install` ผ่านโดยไม่ใช้ `--force` หรือ `--legacy-peer-deps`
4. หลังติดตั้งสำเร็จ ให้รัน Frontend และตรวจสอบ Test Script

ไม่ควรใช้ `--force` หรือ `--legacy-peer-deps` เป็นวิธีแก้ถาวร เพราะอาจทำให้ Dependency Tree ไม่ถูกต้องและสร้างปัญหาใน CI/CD ภายหลัง

### Current Next Steps

- แก้ Dependency Version Conflict ของ Frontend
- รันหน้า Vue Example ให้สำเร็จ
- ตรวจสอบ Router, Pinia, Vitest และ Playwright
- เพิ่ม README ในแต่ละ Repository
- เพิ่ม Link ไปยัง `context.md` หรือ Context กลางในทุก Repository
- สร้าง Branch `develop` และกำหนดรูปแบบ `feature/<feature-name>`
- เริ่มทำหน้า Login และ Patient Registration หลังจาก Frontend รันได้


