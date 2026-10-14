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
- ยังไม่มีเวลาสิ้นสุด: `endAt` เท่ากับ `startAt` ระหว่างรอ Q8; P3 map `note` ไป `appointment.notes` เดิม และอ่าน `cancelReason` จากประวัติ Cancelled ล่าสุด
- มี `patientType` = `CURRENT` หรือ `NEW`; `NEW` จองด้วย `contactName`/`contactPhone` แล้ว `link-patient` เมื่อลงทะเบียน
- `status` เริ่มต้นได้ `Unconfirmed` หรือ `Confirmed`; จอง therapist เวลาเดียวกันซ้ำจะได้ 400
- `PATCH` เลื่อนนัดเปลี่ยนวัน/เวลาและ `note` ได้ (`therapistId` ยังถูกเมินระหว่างรอ Q8); ไม่ส่ง note คงเดิม, ส่ง null ล้างค่า

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

## 5. P2 patient contract (14 ตุลาคม 2026)

- Register/update รับ `firstName`, `lastName`, `middleName: string|null`; profile เพิ่มทั้งสาม field เพื่อให้แก้ชื่อหลายคำโดยไม่ split จาก display name; `fullName` ใน patient summary/profile รวมชื่อกลางที่มีอยู่โดยไม่รวมไว้ใน firstName
- `middleName` ใช้คอลัมน์ `patient_middle_name` เดิม, สูงสุด 100 ตัวอักษร, trim และ blank→null; update ไม่ส่ง field นี้คงค่าเดิม ส่ง null/blank เพื่อล้าง; ชนิดอื่นที่ไม่ใช่ string/null ได้ 400
- Client เก่าที่ส่งเฉพาะ fullName ยังใช้ได้เมื่อไม่มีชื่อกลาง; การบันทึก fullName เดิมที่มีชื่อกลางไม่ split ซ้ำ เมื่อแก้ชื่อที่กำกวมหรือเพิ่มชื่อกลางให้ส่ง firstName/lastName แยก มิฉะนั้นได้ 400 เพื่อไม่เดาองค์ประกอบชื่อ
- คำนำหน้า 5 canonical values: นาย, นางสาว, นาง, เด็กชาย, เด็กหญิง; รับ English aliases Mr./Miss/Mrs./Boy/Girl และบันทึก canonical ไทยสำหรับ create/update ใหม่ ไม่ backfill ข้อมูลเก่า
- UI แสดง labels ไทย/อังกฤษและเพศ ชาย/หญิง/ไม่ระบุ; ส่ง Male/Female/NotSpecified และยังรับคำไทย; title กับ gender เลือกแยกกันโดยไม่เขียนทับกันอัตโนมัติ; Other เดิมยังอ่าน/รับได้เพื่อ compatibility
- Thai/Foreigner ส่งใน customerType โดยแยกจาก customer group; address ไม่บังคับทั้ง API/UI; foreigner ไม่บังคับ phone/birthDate/passport ตามเดิม ส่วนกฎ phone คนไทยยังคงไว้ระหว่างรอ Q1
- `GET /api/patients/identity-lookup?nationalId=<13 ASCII digits>` ต้อง login (และ active user); equality กับ national_id เท่านั้น ข้ามสาขาได้ตาม global patient directory ไม่ค้น passport/phone/ชื่อ ไม่ใช้ partial/fuzzy match
- พบ: `{exists:true, patient:{id,hn,fullName}}`; ไม่พบ: `{exists:false,patient:null}`; malformed/partial ได้ 400, unauthenticated ได้ 401; ไม่ส่ง nationalId/address/phone/birthDate กลับจาก endpoint นี้
- `patientApi.identityLookup` เป็น browser adapter ที่ UI ตรวจเลขบัตรใช้ร่วมได้กับ reader integration ภายนอก; ยังไม่ได้เชื่อมเครื่องอ่านบัตรจริงหรือสร้าง hardware daemon
- คง logic name warning เดิมขณะ Q2 ยังเปิด แต่เปลี่ยน identity check จาก broad patient search เป็น exact endpoint; ไม่อ้างว่าการเตือนชื่อซ้ำครอบคลุมเพราะ repository เดิมค้น first/last แยก ไม่ได้ค้น display name ทั้งก้อน
- Allergy/consent/emergency (T27/Q10) ยังไม่ implement/backfill; placeholder fields เดิมใน profile ไม่ใช่หลักฐานว่าข้อมูลเหล่านี้ถูกบันทึกแล้ว

## 6. P3 appointment workflow (14 ตุลาคม 2026)

- NEW ต้องมี contactName/contactPhone/branchCode/therapistId/startAt; room ไม่บังคับ และไม่สร้าง Patient ก่อนลงทะเบียน; requestedServiceType รับ SingleVisit/Course ตาม enum เดิม
- `GET /api/appointments?query=...` ค้นชื่อจริง/กลาง/นามสกุล/ชื่อเต็ม/ชื่อเล่น/HN/โทรของ CURRENT และชื่อ/โทรของ NEW ใน repository ก่อนแบ่งหน้า ภายใต้ขอบเขตสาขา P1; page ต้องไม่ติดลบและ size 1–100
- response เพิ่ม patient.phone, patientType และ contact fields ให้ client แยก NEW/CURRENT จริง; NEW patient.id/hn เป็น null ไม่ใช่ทะเบียนผู้ป่วยที่สร้างล่วงหน้า
- POST link-patient ล็อก appointment ก่อนโหลดครั้งแรกและตรวจประเภทใน transaction เดียว; คำขอพร้อมกันสำเร็จได้หนึ่งคำขอ อีกคำขอได้ 400 ไม่เปลี่ยนไปเป็นคนอื่นโดยเงียบ
- หน้า NEW ยืนยันแล้วไปลงทะเบียนพร้อม appointmentId จากนั้น link และไปบริการพร้อม patientId/appointmentId; retry หลัง link ล้มเหลวใช้ patient ที่สร้างแล้ว ไม่ลงทะเบียนซ้ำภายในหน้าปัจจุบัน; response ของ link สูญหายตรวจยืนยันด้วย patient ID เดิมได้
- CURRENT ยืนยันแล้วเปิดบริการของผู้ป่วยคนนั้นโดยไม่ค้นซ้ำ; หน้าบริการตรวจความสัมพันธ์ patient/appointment และล้าง query context เมื่อเปลี่ยนผู้ป่วย ไม่เปลี่ยน sale/payment/commission rules
- UI ถอดห้องและตัวกรองบริการ แสดงเบอร์บนรายการ/ปฏิทิน จำกัด branch selector ให้ Manager; นัดรอยืนยันยังแก้ได้ นัด Confirmed แสดงบริการ/ลงทะเบียนแทน
- คง enum/transition เดิม: Unconfirmed/Rescheduled แสดงกลุ่มรอยืนยัน, Confirmed/Arrived/InService กลุ่มยืนยัน, Completed ใช้บริการแล้ว, Cancelled/NoShow กลุ่มยกเลิก; action selector ยังแสดงสถานะจริง ไม่ลบประวัติ enum
- Q8 ยังเปิดเรื่อง duration/endAt, ความหมายใช้บริการแล้วกับ receipt และการย้าย therapist; Q9 ยังพักตาม P1; ไม่ปิด Google Calendar integration หรือ Assessment sales scope ของ P5 ด้วยงานนี้

## 7. Audit log

`GET /api/audit-logs` บันทึกเมื่อ: ขาย, ยกเลิกบิล, ขอ/จบ/ล้มเหลวการคืนเงิน, ซื้อ/ยกเลิกคอร์ส, โอน/ย้อนการโอนคอร์ส,
สร้าง/แก้/ปิด/เปิดผู้ใช้ (ตาราง, id, ผู้ทำ, เวลา, ค่าเดิม/ใหม่)

## 8. สิ่งที่ยังไม่ได้ตัดสินใจ (รอ manager)

ข้อมูลเก่าเก็บหรือทิ้ง, คอมมิชชันโบนัส, คอร์สหมดอายุ, กฎสัดส่วน PT 2 คน, ขอบเขต Google Calendar, สถานะนัดเริ่มต้น (ภาษี 0 ยืนยันใน R32 แล้ว)

## P4 payment policy (14 ตุลาคม 2026)

- POST /api/payments คง payments[] แต่ canonical methods ต่างกันในบิลเดียวได้400ก่อนสร้างreceipt/line/payment; QR aliases รวมเป็นPromptPay วิธีหลัก Cash/BankTransfer/CreditCard/PromptPay
- PaymentTransactionService ถือ receipt row lock ก่อนตรวจ active payment methods ป้องกันคำขอ concurrent คนละวิธีทั้งสองcommit; วิธีต่างจาก active เดิมได้400 หลายrecordsวิธีเดียวคง behavior เดิมระหว่างรอQ6 ไม่ถือว่าอนุมัติ policy งวดแล้ว
- Sale summary/detail เพิ่ม paymentLabel เป็น FullyPaid/Installment/Deposit จาก Receipt ที่persistแล้ว ไม่ deriveจากstatusPAID/VOIDED; UIแสดงชำระเต็ม/ผ่อน/มัดจำแยก lifecycle พร้อมเพย์เป็นdisplayของQR/PromptPay
- POST /api/payments/{paymentId}/refunds และ POST /api/refunds/{id}/complete ได้409ถ้าcourseในreceiptเคยมีusage (รวมreversedusage); lockcoursesเรียงIDโดยใช้ lockเดียวกับcourseusageAPI แล้วอ่านusageก่อนเขียนfinancialrows
- Completeที่ถูกปฏิเสธคงrefundPending และอ่านhistoricalrefundsได้ตามเดิม ไม่เปลี่ยนvoid/commission reversal rules
- Q5/Q6นายท่านให้พัก: ไม่มีdiscountprecision/ceiling changes, ไม่มีpaymentlabelselector/deriveใหม่, ไม่มีcredit/loanengine; ตัดแต้ม/วงเงินสมมติจากUI

## P5 receipt history / print contract (14 ตุลาคม 2026)

- GET /api/payments?patientId=...&receiptsOnly=true&page=0&size=20 ใช้serverpatient/branchfilterและเลือกlegacylineIDแรกต่อreceiptก่อนpagination จึงไม่ซ้ำmulti-line bill; เรียงtransactionDateและidDESCเพื่อให้pageเสถียร
- receiptsOnlyไม่ส่งหรือfalseคงtransaction-line modeเดิมรวมline totalเดิม; receiptmode totalเป็นReceipt.netAmount ทั้งบิล; size1–100/page>=0
- Summary/detailเพิ่ม receiptId(nativeReceipt.id), subtotalBeforeDiscount(หลังส่วนลดlineก่อนbill), billDiscountAmount,taxableAmount,taxAmount,netAmount จากreceiptที่persistแล้ว; paidยังเป็นยอดactivepayments แยกจากnet ไม่ใช้paid deriveส่วนลด
- สำคัญ: idเดิมยังเป็นSalesTransaction.id และ GET /api/receipts/{id} รับlegacytransactionID ไม่ใช่nativeReceipt.id; หลายlineทำให้เลขสองชุดต่างกันได้ Frontendroute /staff/receipts/:id ใช้sale.idเดิมเช่นกัน
- หลังPOSTpaymentสำเร็จ frontendเปิดหน้าreceiptที่GETข้อมูลจริง; refresh/retryreceiptเป็นGET ไม่สร้างsaleซ้ำ หากPOSTล้มเหลวไม่เปิดหน้าพิมพ์; หากPOSTcommitแต่navigation/GETผิดพลาดเก็บsaleIDแล้วเปิดอ่านซ้ำ ไม่POSTซ้ำอัตโนมัติ
- หน้าประวัติขอreceiptmodeเฉพาะpatientก่อนแบ่งหน้า20records มีnext/prev; ตัวกรองlocalอื่นระบุว่าครอบคลุมหน้าปัจจุบัน ไม่อ้างเป็นการค้นทั้งประวัติ
- หน้าพิมพ์แสดงreceiptNo/date/items/ส่วนลด/tax/net/paid/paymentLabel/methodจากserver และดึงสาขาของreceipt ไม่เดาจากpatienthomebranch; ไม่ใช้วันที่/BLอ้างอิงสมมติ ไม่รับรองสถานะพิมพ์สำเร็จจากwindow.print
- `items[].description` ของ receipt detail อ่าน `item_name_snapshot` ณ วันขาย จึงไม่เปลี่ยนตามการแก้ชื่อ service ใน catalog; ชื่อผู้ป่วยและรายละเอียดสาขายังอ่านจากระเบียนปัจจุบัน เพราะยังไม่มี snapshot ของสองส่วนนี้
- V1001 เพิ่ม `Assessment` ใน PostgreSQL transaction enum เพื่อเก็บประวัติเก่า; การขายใหม่ยัง reject. Legacy migration คงชนิด Assessment และเลขใบเสร็จเดิมถูก map เป็นเลขใหม่ตามกติกา migration เดิม
- Tax0เป็นนโยบายที่ยืนยันในR32 ไม่มีการเปลี่ยนสูตร/ข้อมูลย้อนหลัง; ซ่อนAssessmentในUIการขายใหม่ คงAPIenum/ประวัติเดิมให้อ่านและพิมพ์ได้
- Q4formatเลข/หน่วยcourseและQ9permissionmatrixยังไม่ปิด; ไม่เปลี่ยนreceiptNoหรือquantity/price semantics และคงglobalpatient/salesaccessเดิม เช่นเดียวกับAPIก่อนP5
