# API v2 กับ frontend: เอกสารเทียบสัญญา (contract)

สร้างจากการเทียบ `frontend/src/api/client.ts` กับ controller ใน `backend/src/main/java/com/sabyeguyz/clinicv2`
ฐานเอกสารเดิมจาก branch `feat/v2-cutover` (PR #43); เพิ่มขอบเขตสิทธิ์นัดหมาย P1 วันที่ 14 ตุลาคม 2026 ตามรายละเอียดด้านล่าง

## 1. ความครอบคลุมของ endpoint

- frontend เรียก API 93 รายการ (method + path)
- API v2 มี route ตรงกัน 87 รายการจากการตรวจด้วยสคริปต์
- อีก 6 รายการเป็นผลบวกลวงของสคริปต์ (path ที่ต่อ query string เช่น `/api/course-transfers?patientId=`,
  `/api/lookups/therapists?branchCode=`, `closings/{เดือน}-01`) และ **ช่องว่างจริง 2 จุดซึ่งปิดแล้ว**:
  - `DELETE /api/staff/{id}` (ปิดพนักงาน, เฉพาะ MANAGER) และ `POST /api/staff/{id}/terminate`
  - `GET /api/course-usages` (รายการการใช้คอร์สพร้อมสถานะยกเลิก)

ทุกข้อข้างบนมีเทส Cypress ระดับ HTTP ใน `backend/cypress/e2e-v2/` (รันด้วย `npm run test:v2` ใน `backend/`)

## 2. endpoint ใหม่ที่ frontend ยังไม่ได้เรียก

| Endpoint | หมายเหตุ |
|---|---|
| `POST /api/payments/{paymentId}/refunds`, `GET` เดียวกัน | ขอคืนเงิน (Pending) เฉพาะ MANAGER; `paymentId` มาจาก `payments[].id` ของ `GET /api/receipts/{id}` |
| `POST /api/refunds/{id}/complete`, `/fail` | ปิดรายการคืนเงิน |
| `POST /api/payments` รับ `items` ได้หลายรายการ | เดิมจำกัด 1 รายการ; ส่วนลดท้ายบิลถูกแบ่งลงแต่ละรายการ |
| `POST /api/course-purchases` รับ `shares[]` และ `caseOwnerStaffId` | แบ่งเครดิตยอดขายให้ PT สูงสุด 2 คน รวม 100%; ไม่ส่งมา = ผู้ขายได้ 100% |

## 3. จุดที่ response ต่างจากของเดิม (frontend ต้องปรับ)

### ผู้ป่วย (`/api/patients`)
- `drugAllergy`, `foodAllergy`, `chronicDisease`, `emergencyContact*`, `privacyConsent`: schema ใหม่ไม่มีคอลัมน์
  จึงคืนค่า false/null เสมอ
- `customerType` (เดิมเป็นข้อความอิสระเช่น VIP) ตอนนี้คือ customer group; ค่า Thai/Foreigner ใหม่อยู่ใน `customerTypeCode`
- คนไทยต้องมี `phone` และ `identityNumber` 13 หลัก; ต่างชาติไม่บังคับ
- HN รูปแบบ `ปี(2) + สาขา(2) + เดือน(2) + ลำดับ(4)` ออกพร้อมการบันทึกผู้ป่วย

### นัดหมาย (`/api/appointments`)
- schema ใหม่ไม่มีเวลาสิ้นสุดและไม่มี note/cancelReason: `endAt` เท่ากับ `startAt`, `note` และ `cancelReason` เป็น null เสมอ
- มี `patientType` = `CURRENT` หรือ `NEW`; `NEW` จองด้วย `contactName`/`contactPhone` แล้ว `link-patient` เมื่อลงทะเบียน
- `status` เริ่มต้นได้ `Unconfirmed` หรือ `Confirmed`; จอง therapist เวลาเดียวกันซ้ำจะได้ 400
- `PATCH` เลื่อนนัดเปลี่ยนได้เฉพาะวัน/เวลา (`therapistId` ถูกเมินในตอนนี้)

### ผู้ใช้และ role
- `AuthResponse.email` เป็น null (ไม่มีคอลัมน์ email); `role` คือ role แรกของผู้ใช้ (v2 รองรับหลาย role)
- ผู้ใช้ที่ยังไม่มี role ล็อกอินได้ แต่ token ไม่มี claim `role`
- พนักงาน (`/api/staff`) ไม่มี `userId/username/role/phone`; ใช้ `/api/users` เมื่อต้องการบัญชีรวมกับพนักงาน

### การขาย/คอร์ส
- `receiptNo` เป็นเลขใบเสร็จจริง `RE-xxxxxxx` (ล็อกไม่ให้ซ้ำเมื่อขายพร้อมกัน)
- `course.code` ในผลซื้อคอร์สคือ **เลขที่คอร์สที่ขาย** (เลขใบเสร็จ) ไม่ใช่รหัสแม่แบบ; รหัสแม่แบบดูที่ `/api/catalog/courses`
- อัตราคอมมิชชัน (`lockedCommissionRate`, `totalCourseCommissionPool`) เป็น 0 และ `commissionStatus = Provisional` จนกว่าจะปิดยอดรายเดือน
- ใช้คอร์สเกินสิทธิ์ได้ 400/409 และยิงพร้อมกันแล้วไม่เกิน (ล็อกแถวคอร์ส)

## 4. สิทธิ์การเข้าถึง

| กลุ่ม | สิทธิ์ |
|---|---|
| MANAGER เท่านั้น | `/api/users` (ทุก method), `/api/auth/register`, จัดการบริการ/แม่แบบคอร์ส, คอมมิชชัน, คืนเงิน, ปิดพนักงาน |
| ผู้ใช้ที่ล็อกอินแล้ว | อ่านข้อมูลทั่วไป, ขาย, ใช้คอร์ส; นัดหมายจำกัดสาขาตาม P1 ด้านล่าง |
| ไม่ต้องล็อกอิน | `/api/auth/login`, `/actuator/health` |

ไม่มี token หรือ token เสีย = 401; ไม่มีสิทธิ์ = 403

### P1 ส่วนสิทธิ์นัดหมาย (R1–R3, 14 ตุลาคม 2026)

ใช้เฉพาะ `/api/appointments` และทุก action ใต้ path นี้; ยังไม่เปลี่ยน login/session contract หรือสิทธิ์ report/sale/course/catalog ตามที่นายท่านเลือกพัก Q9

- Server อ่าน active AppUser, roles และ `Staff.branch` จาก DB; Manager เท่านั้นที่เข้าถึงนัดได้ทุกสาขา บัญชีอื่นที่มี staff ใช้สาขาประจำของตน ไม่ใช้ branch grant เดิมเป็นสิทธิ์ข้ามสาขาใน P1 นี้
- `GET /api/appointments` ไม่ส่งหรือส่ง branchCode ว่าง: non-Manager ได้เฉพาะสาขาประจำ รวมถึง `totalItems/totalPages`; Manager ได้ทุกสาขา ส่ง branchCode ต่างสาขาสำหรับ non-Manager ได้ 403 และ therapist/date/status/page ไม่ข้ามขอบเขตนี้
- `POST /api/appointments` ต้องระบุ branchCode; non-Manager ต้องตรงสาขาประจำ ฝืนส่งต่างสาขาได้ 403; therapist ต้องอยู่สาขาเดียวกับนัด ไม่ตรงได้ 400 ทั้ง CURRENT/NEW และ Manager
- `GET /{id}`, `PATCH /{id}` และ POST confirm/link-patient/check-in/start-service/complete/no-show/cancel ตรวจสาขาของนัดที่บันทึกไว้ก่อนอ่านข้อมูลหรือประมวลผล action/payload; ต่างสาขาได้ 403, ID ไม่มีได้ 404
- ไม่มี staff/สาขาประจำและไม่มีบทบาท Manager ได้ 403; AppUser ปิดใช้งานได้ 401 สำหรับ appointment endpoints แม้ JWT ยังไม่หมดอายุ
- ผู้ป่วยยังค้นและอ่าน profile รวมข้ามสาขาได้; `patientId` ไม่ใช่สิทธิ์ในการเข้าถึงนัด จองให้ผู้ป่วยจากสาขาอื่นในสาขาประจำของผู้ใช้งานได้
- รูป JSON, query names, statuses และ JWT เดิมไม่เปลี่ยน; reschedule ยังไม่ย้าย therapist ตามข้อจำกัดเดิม/Q8

Open Question Q9 ยังไม่ปิด: ระบบปัจจุบันมี Manager/Therapist; ไม่เพิ่ม STAFF/ADMIN หรือเดา Figma permissions/login selector ในรอบนี้ สิทธิ์ข้ามสาขาของ reports/sale/course และ session branch selection ยังต้องทำแยกตามคำตอบที่ยืนยัน

ข้อจำกัดของการตรวจ DB roles ในรอบนี้: ใช้กับ appointment endpoints เท่านั้น ส่วน management endpoints เดิม เช่น `/api/users` ยังใช้ JWT role claims; จึงยังไม่ใช่การเพิกถอนสิทธิ์ Manager/token แบบครอบคลุมทั้งระบบ

## 5. Audit log

`GET /api/audit-logs` บันทึกเมื่อ: ขาย, ยกเลิกบิล, ขอ/จบ/ล้มเหลวการคืนเงิน, ซื้อ/ยกเลิกคอร์ส, โอน/ย้อนการโอนคอร์ส,
สร้าง/แก้/ปิด/เปิดผู้ใช้ (ตาราง, id, ผู้ทำ, เวลา, ค่าเดิม/ใหม่)

## 6. สิ่งที่ยังไม่ได้ตัดสินใจ (รอ manager)

ข้อมูลเก่าเก็บหรือทิ้ง, คอมมิชชันโบนัส, คอร์สหมดอายุ, กฎสัดส่วน PT 2 คน, ภาษี, ขอบเขต Google Calendar, สถานะนัดเริ่มต้น
