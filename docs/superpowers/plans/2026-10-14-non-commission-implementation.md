# Non-Commission Backend Continuation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking. ใช้ subagent-driven-development ได้เมื่อนายท่านเลือกให้ใช้ subagents

**Goal:** ทำ requirement และ backlog ที่ไม่เกี่ยวกับ commission ต่อจาก `feat/carlendar` โดยตรวจสิ่งที่มีแล้วและแยกงานที่รอคำตอบชัดเจน

**Architecture:** ใช้ v2 Spring Boot/JPA/PostgreSQL/Flyway ปัจจุบัน; backend เป็นผู้ตรวจสิทธิ์และความถูกต้อง ส่วน Vue รับ contract เดียวกัน แยกงานเป็นราย subsystem ที่ทดสอบและ commit ได้เอง ไม่เปลี่ยน commission rule หรือฐานคำนวณ

**Tech Stack:** Java 17 ตาม pom, Spring Boot, PostgreSQL, Flyway, Cypress API, Vue 3/TypeScript/Vitest/Cypress browser

**Spec:** `docs/superpowers/specs/2026-10-14-non-commission-scope.md` พร้อม source R/T/F และ Open Questions Q1–Q13

## Global Constraints

- ฐานที่ตรวจวันที่ 14: branch `feat/carlendar`, commit `7462949`; ใช้ branch นี้เป็น base ไม่ใช่ branch จาก memory
- ตอนจัดทำแผนยังไม่เปลี่ยน application code, DB, branch หรือ commit; นายท่านอนุญาต execution วันที่ 14 ตุลาคม 2026 ให้แยก branch ทำทีละ P และตรวจ requirements ก่อน commit
- ข้อกำหนด source: บิลเดียวจ่ายได้วิธีเดียว; ทศนิยม 2 ตำแหน่ง; Manager เท่านั้นที่นัดข้ามสาขา; ผู้ป่วยค้นรวมผ่านตัวกรองได้
- ไม่รวม commission/Tier/Pool/ค่ามือ/แชร์เครดิตขาย/ปิดยอดคอมมิชชัน; module ที่ใช้ร่วมกันแก้ได้เฉพาะส่วนที่แยกแล้ว พร้อม regression ของพฤติกรรมเดิม
- ไม่แก้ V1–V3 ที่ applied แล้ว; migration ใหม่เลือกเลขต่อจากล่าสุด ณ เวลาลงมือ
- PostgreSQL/API tests ใช้ฐานทดสอบแยก; ห้าม reset ฐานจริง; test fixture ต้องครอบ role และสาขาอย่างน้อย 01/02
- Open Question เป็น gate เฉพาะ task ที่ขึ้นกับคำตอบ; ไม่สร้างกติกาธุรกิจแทนคำตอบ
- ทุก task ปรับ `docs/api-v2-frontend-contract.md`, เขียน RED→GREEN ที่พิสูจน์พฤติกรรมจริง แล้ว commit เฉพาะไฟล์งานนั้นเมื่อเริ่ม execution ที่ได้รับอนุญาต

## ลำดับงานและตัวเลือก

แนะนำ: P0 baseline → P1 branch security → P2 patient fields → P3 appointments → P4 payments → P5 receipt/history → P7 expiry → P6 identifiers เมื่อ Q3/Q4 พร้อม → P8 reports/integrations → P9 migration/release

ทางเลือกอีกสองแบบ: เริ่ม P2/P3 ที่ชัดก่อนขณะรอ permission matrix (ได้ UI เร็วแต่ยังไม่ควร release cross-branch); หรือทำ identifiers/migration ก่อนทุกอย่าง (ลดการเปลี่ยนรูปแบบรอบหลังแต่จะรอ DB/Manager มาก) แผนนี้แนะนำ security ก่อน แล้วให้ task ที่ไม่ติด gate เดินต่อ

## P0: Baseline และพื้นที่ execution

**Files:** อ่าน pom/config, V1–V3, `backend/scripts/run-v2-tests.cjs`, `.github/workflows/*`, `docs/testing/2026-10-13-Phase-2-V2-Acceptance.md`; สร้าง `docs/testing/2026-10-14-Non-Commission-Baseline.md` เมื่อรันจริง

- [x] ตรวจ `git status --short`, `git branch --show-current`, `git rev-parse HEAD`; ยืนยัน HEAD `74629492972f4cfb04bac82cadd47b04668fdd2e`; เก็บเอกสาร plan/spec ที่ untracked เดิมไว้
- [x] สร้าง branch `codex/non-commission-backend` จาก local `feat/carlendar` ที่ hash ข้างต้นตามคำขอแยก branch; ใช้ checkout เดิม ยังไม่ได้สร้าง linked worktree และไม่อ้างว่าแยกพื้นที่ไฟล์แล้ว
- [x] ตั้ง DATABASE_URL/USERNAME/PASSWORD ให้เป็น disposable PostgreSQL ของงานนี้; เก็บ secrets นอกเอกสาร; backend v2 ผ่าน 143/143 รวม commission และ package ผ่าน; คำสั่ง:

```powershell
npm run test:v2
mvn --batch-mode --no-transfer-progress clean package
```

- [x] frontend unit ผ่าน 28/28 และ build ผ่าน; คำสั่ง:

```powershell
npm run test:unit -- --run
npm run build
```

- [x] บันทึกผลใหม่วันที่ 14 และข้อจำกัดใน `docs/testing/2026-10-14-Non-Commission-Baseline.md`; แยก Docker startup/sandbox EPERM จาก product และรันใหม่ผ่านแล้ว; ยังไม่เริ่ม P1 ใน P0 commit

## P1: Branch authorization และ session branch (R1–4, T4/24)

**Modify:** `backend/src/main/java/com/sabyeguyz/clinicv2/auth/{AuthController,JwtTokenService,AppUserService,UserBranchAccessRepository}.java`, `appointment/{AppointmentController,AppointmentService,AppointmentRepository}.java`, `report/ReportController.java`, `catalog/CatalogController.java`, controllers ของ sale/course ที่อ่านหรือเขียนข้อมูลรายสาขา, `frontend/src/features/auth/views/StaffLoginView.vue`, `frontend/src/api/client.ts`

**Create:** `backend/src/main/java/com/sabyeguyz/clinicv2/auth/BranchAccessPolicy.java`, `backend/cypress/e2e-v2/branch-access.cy.js`

**Interfaces proposed (Assumption รอ Q9):** login เพิ่ม selected `branchCode` โดยตรวจ home branch/role จาก server; policy คืน allowed branch ID สำหรับ query และตรวจ resource.branch ก่อน get/mutate; ไม่ trust branchCode จาก payload; patient directory คง global search ได้ตาม R3

- [ ] ใช้ fixtures ใน management/auth specs สร้าง STAFF สาขา01, STAFF สาขา02, MANAGER และ ADMIN; login แต่ละรายและสร้างนัดทั้งสองสาขา
- [ ] เพิ่ม RED cases: STAFF01 list ไม่ปน02; ส่ง branch02 ใน create ได้403; get/reschedule/cancel/confirm/link-patient/check-in/start/complete นัด02 ได้403; เปลี่ยน patientId/therapistId ไม่ bypass; unauthenticated401; Managerทำได้ทั้งสอง; Adminไม่ inherit Manager; global patient search ยังเห็นผู้ป่วย02
- [ ] รัน `npm run test:v2 -- --spec cypress/e2e-v2/branch-access.cy.js` และยืนยัน fail ตรงข้อ branch enforcement
- [ ] รวม enforcement ใน policy เดียว: resolve actor จาก authenticated user, ตรวจ role/branch จาก DB, filter query เมื่อไม่ส่งสาขา, ตรวจ resource branch ก่อน mutation; therapist ต้องอยู่สาขาของนัด; ใช้ policy กับ report/sale/course เมื่อ endpoint มีข้อมูลรายสาขา
- [ ] หาก Q9 ยังไม่ตอบ ทำส่วนบังคับสิทธิ์นัดตาม R1–3 ได้ แต่ไม่เดา granular permission จาก Figma; login selector/multi-branch contract รอ Q9
- [ ] ยืนยัน GREEN scoped suite + auth/appointments/reports/frontend-contract; browser loginเลือกสาขาและ Managerสลับสาขา; commit `feat: enforce branch access outside commission`

### P1 execution scope ที่ยืนยันวันที่ 14 ตุลาคม 2026

นายท่านเลือก “ทำสิทธิ์นัดหมายก่อน พักส่วน Q9 ตามแผน” จึงแบ่ง release นี้เป็น **P1 ส่วน appointment authorization**; ไม่ถือว่าปิด P1 ทั้งหมด

- ใช้ Manager/Therapist ซึ่งมีอยู่จริงเป็น fixtures ทั้งสาขา 01/02; ไม่เพิ่ม STAFF/ADMIN roles หรือแก้ permission matrix ก่อน Q9
- BranchAccessPolicy อ่าน active user/role/home branch จาก DB และบังคับเฉพาะ appointment endpoints; legacy branch grants ไม่เปิดการนัดข้ามสาขาให้ non-Manager
- Login/session selector, report/sale/course/catalog enforcement และ browser selection ที่ขึ้นกับ Q9 ยังพักไว้; global patient search คงเดิม
- RED รอบแรก `branch-access.cy.js`: 18 tests, ผ่าน 2 / ล้ม 16 เพราะขาด branch enforcement และ therapist-branch validation; scoped GREEN หลังแก้ผ่าน 18/18
- [x] ยืนยัน release เฉพาะ appointment subset: full API 161/161 รวม commission 17/17; browser นัดหมาย 16/16; frontend unit 28/28 และ backend/frontend build ผ่าน; read-only code review ไม่พบ actionable defect ใน scope
- Checklist P1 ด้านบนคงเปิดสำหรับส่วนที่ยังไม่ครบ; ผลตรวจและรายการที่รับเข้า commit ดู `docs/testing/2026-10-14-P1-Appointment-Branch-Authorization.md`

## P2: Patient contract และ exact identity lookup (R5–11, T15/16/20/27)

**Modify:** `backend/src/main/java/com/sabyeguyz/clinicv2/patient/{Patient,PatientRegistration,PatientController,PatientService,PatientRepository}.java`, `frontend/src/api/{client,patients}.ts`, `frontend/src/features/patients/views/{PatientRegistrationView,PatientProfileView}.vue`

**Test:** `backend/cypress/e2e-v2/{patients,patient-update,frontend-contract}.cy.js`, สร้าง `backend/cypress/e2e-v2/patient-identity-lookup.cy.js`, `frontend/src/api/__tests__/patients.spec.ts`

**Interfaces proposed:** register/update/profile เพิ่ม `middleName: string|null`; แยก Thai/Foreigner ออกจาก customer group ตาม API เดิม; `GET /api/patients/identity-lookup?nationalId=<13 digits>` คืน `{exists:boolean, patient:PatientSummary|null}`; query exact equality เท่านั้น ใช้ auth และจำกัดข้อมูลตอบให้เท่าที่จำเป็น

- [x] เพิ่ม test ชื่อกลางที่ต้อง persist/read back จริง:

```javascript
it('persists middleName across registration and profile reads', () => {
  cy.registerPatient({ middleName: 'กลาง' }).then((patient) => {
    cy.api('GET', `/api/patients/${patient.id}`)
      .its('body.middleName').should('equal', 'กลาง')
    cy.api('PUT', `/api/patients/${patient.id}`, {
      firstName: 'สมชาย', lastName: 'ใจดี', middleName: 'ใหม่',
    }).its('status').should('equal', 200)
    cy.api('GET', `/api/patients/${patient.id}`)
      .its('body.middleName').should('equal', 'ใหม่')
  })
})
```

- [x] เพิ่ม P2 scoped RED และ full patients/update/frontend-contract regression; clearกลางเป็นnull, ไทย/อังกฤษ title ทั้ง5, gender3, foreignerไม่มีโทร/วันเกิด/พาสปอร์ต, addressว่าง; ผลจริงในรายงาน P2
- [x] Map `@Column(name="patient_middle_name")` ใน Patient และเดินค่า DTO→service→entity→response; ไม่เพิ่มคอลัมน์ซ้ำ; title canonical value + UI label ไทย/อังกฤษ; ไม่รวมชื่อกลางใน firstName
- [x] lookup cases พบตรง, ไม่พบ, เลขบางส่วน, non-digit/lengthผิด, unauthenticated; repository equality nationalId + minimal response; browser adapter ใช้ endpoint นี้ ยังไม่เชื่อม hardware จริง
- [ ] Q1/Q2 ยังไม่ตอบให้คงกฎโทรไทยและไม่เพิ่ม hard-block ชื่อซ้ำ; F3 ระบุโทร/วันเกิดไม่บังคับในการลงทะเบียน แต่ R10 ขัดกันเรื่องคนไทย จึงอย่าเปลี่ยน validation ทั้งระบบก่อนคำตอบ; เมื่อคำตอบพร้อมเพิ่มตาราง tests แยก Thai/Foreignerและ create/updateให้เหมือนกัน; การเตือนชื่อซ้ำต้องไม่ mergeผู้ป่วยอัตโนมัติ
- [ ] T27 เป็นแพ็กเกจเพิ่มต่างหากหลัง Q10: ออก field contract, forward migration, round-trip test และ backfillรายfield; ห้ามส่ง false/null แล้วแสดงเหมือนข้อมูลเดิมถูกเก็บครบ
- [x] GREEN ของส่วนไม่ติด gate: API 176/176 (P2 15/15, P1 18/18, commission 17/17), frontend unit 30/30, browser 26/26, builds และ SchemaCheck 490 columns/0 problems ผ่าน; commit `feat: complete patient fields and identity lookup`

P2 release นี้ทำเฉพาะส่วนที่ไม่ติด Q1/Q2/Q10; mapping พิสูจน์ด้วย API/DB readback + SchemaCheck, frontend adapter unit และ browser แทนการถือว่า compile อย่างเดียวคือ persist สำเร็จ รายงาน `docs/testing/2026-10-14-P2-Patient-Fields-Identity-Lookup.md` ระบุผล/ข้อจำกัด; checklist gated ข้างต้นยังไม่ปิด

## P3: Appointment New/Current, search และข้อมูลที่ไม่ persist (R12/15–22/24–25, T17–19)

**Modify:** `backend/src/main/java/com/sabyeguyz/clinicv2/appointment/{Appointment,AppointmentController,AppointmentService,AppointmentRepository,AppointmentStatusHistoryRepository}.java`, `frontend/src/features/appointments/views/AppointmentView.vue`, `frontend/src/features/patients/views/PatientRegistrationView.vue`, `frontend/src/features/services/views/servicesView.vue`, `frontend/src/api/client.ts`

**Test:** `backend/cypress/e2e-v2/{appointment-workflow,appointments,frontend-contract}.cy.js`; create `frontend/cypress/e2e-v2/appointment-registration.cy.js`; schema migration สำหรับ note/timing เฉพาะคอลัมน์ที่ยังขาดจริง

**Interfaces proposed:** list เพิ่ม `query` ค้น registered name และ contactName/contactPhone; response noteจาก persisted field, cancelReasonจากล่าสุดใน status history; branchCodeเป็นสาขาที่ server ตรวจ; NEW ไม่สร้าง Patientล่วงหน้า; link-patient ต้อง atomicและไม่ relinkไปคนอื่นโดยเงียบ

- [x] RED matrix: API ใหม่ 7 ข้อ fail 6 ก่อนแก้ (รวม concurrent link), browser 3 ข้อเดิม fail ทั้งหมดก่อน implement; GREEN พิสูจน์ validation/no room/search/notes/cancel reason/link/navigation ตามรายงาน P3
- [x] scoped appointment-workflow 7/7 และ full API รวม appointments/frontend-contract 183/183; schema มี appointment.notes TEXT อยู่แล้ว จึง map โดยไม่เพิ่ม migration
- [x] ย้าย query filteringไป repositoryก่อน pagination; validate contact/name/phone สำหรับNEW; ใช้ P1 policy; persist noteในคอลัมน์เดิม; read cancel reasonจากstatus historyล่าสุด
- [ ] timingรอQ8: ถ้าเลือกdurationบนappointment บันทึกstart/endที่ตรวจend>startและใช้Asia/Bangkok; ถ้าเลือกcatalog ให้snapshotdurationตอนจอง; ไม่คืนendAt=startAtพร้อมอ้างว่าช่วงนัดครบ
- [x] browser: ถอด room และ service filter; branch selector เฉพาะ Manager, fieldบังคับมี*, phoneในรายการ/ปฏิทิน, queryผ่านAPI, navigationส่งpatientId/appointmentId; status display mappingระบุในcontract ไม่ลบ InService/NoShow ในDB
- [ ] นิยามใช้บริการแล้วและreschedule therapistต้องได้คำตอบQ8ก่อนเปลี่ยน; ไม่ผูกออกreceiptกับcompletedโดยอนุมาน
- [x] GREEN: API183/183, unit30/30, browser57/57 และ final appointment21/21 หลังแก้ R22/calendar dispatch; builds/SchemaCheck491 columnsผ่าน; commit `feat: complete non-commission appointment workflow`

P3 รอบนี้เฉพาะส่วนไม่ติด Q8; NEW retry link ใช้ patient เดิมภายในหน้าลงทะเบียน ไม่รับรอง retry ข้าม reload/response register สูญหายว่า idempotent; Q9 ยังพักตามคำสั่งนายท่าน และ Google Calendar สองทาง/Assessment sale scope อยู่ขั้นอื่น รายงาน `docs/testing/2026-10-14-P3-Appointment-Workflow.md`

## P4: Payment method, label, discount validation และ refund guard (R26บางส่วน/30–33, T6–10)

**Modify:** `backend/src/main/java/com/sabyeguyz/clinicv2/sale/{SaleController,PaymentTransactionService,PaymentTransactionRepository,Receipt,Discount,PaymentMethod,RefundController,RefundService}.java`, `frontend/src/features/services/views/servicesView.vue`, `frontend/src/features/transactions/views/TransactionView.vue`, `frontend/src/api/client.ts`

**Test:** `backend/cypress/e2e-v2/{sales,refunds,frontend-contract}.cy.js`, `frontend/src/features/transactions/__tests__/TransactionView.spec.ts`

**Interfaces:** คงpayments[]เพื่อcompatible แต่ rejectหลายวิธี; payment label mapping FullyPaid/Installment/Deposit→ชำระเต็ม/ผ่อน/มัดจำ; qr aliasยังacceptแต่UIแสดงพร้อมเพย์; ไม่เปิดinstallment credit engine

- [x] RED: requestบิล Cash+PromptPayได้400และไม่ทิ้งreceipt/transaction/payment; หนึ่งวิธีทุก4แบบสำเร็จ; QR aliasตอบcanonical; เปลี่ยนmethodภายหลังขัดวิธีเดิมไม่ได้; concurrency requestคนละmethodบนreceiptเดียวเหลือวิธีเดียวที่commit
- [x] Validateก่อนสร้างsale; receipt-level lockก่อนrecordpaymentและตรวจexistingactive methodในtransactionเดียว; ไม่ใช้แค่frontendvalidation; one method≠one record ต้องรอQ6เรื่องงวด
- [ ] Q6พร้อมแล้วเพิ่ม label tests: fullpaid, deposit, installment, zero, void/repaymentตามpolicyที่ตอบ; อ่านlabelในdetail/list/historyไม่ deriveจากbooleanแทนenumที่มีแล้ว; F1 ระบุ Payment เป็นการบันทึกสิ่งที่ลูกค้าชำระ จึงไม่สร้าง credit/loan flow จากคำว่า “ผ่อน”
- [ ] ส่วนลด RED cases: percentage/fixedคนละคำขอ, payloadกำกวม, 0, negative, 2digits, >2digitsและoversizeตามQ5; ใช้ BigDecimalและscale policyเดียว line/bill; ห้ามเปลี่ยนcommissionable amount/formula; หากทำไม่แยกได้ให้พักส่วนนั้น
- [x] Refund R33: หลังมีusageของcourseในreceipt request/complete refundต้องปฏิเสธและไม่มีrefund/cancellation/commission rowใหม่; ป้องกันrequestก่อนใช้แต่completeหลังใช้; transaction guardต้อง serializeกับcourse usage หรือใช้ lockเดียวกับusage path; หากต้องเปลี่ยนค่ามือ/reversal algorithmให้พักและแยกงานcommission
- [x] เก็บhistorical refundsอ่านได้; ไม่ปิดvoidบิลที่ยังไม่ใช้โดยอนุมาน; ใช้HTTP conflict409เป็นข้อเสนอcontractสำหรับrefundหลังใช้ และระบุในdoc
- [x] รัน sales/refunds/frontend-contract RED→GREEN พร้อม existing commission suites เพื่อพิสูจน์ไม่ได้เปลี่ยนพฤติกรรมเดิม; commit `feat: align payment rules without commission changes`

P4 Q5/Q6: นายท่านยืนยันให้พักใน chat; คง discount/label derivation/งวดเดิม เพิ่มเฉพาะการอ่าน persisted paymentLabel และ display ไม่ปิด checklist gated ข้างต้น

## P5: Patient receipt history, print และ tax policy (R13/24–25/28–29บางส่วน/32, T11)

**Modify:** `backend/src/main/java/com/sabyeguyz/clinicv2/sale/{SaleController,ReceiptService,SalesTransactionService}.java`, `frontend/src/features/patients/views/{PatientHistoryView,PatientProfileView}.vue`, `frontend/src/features/services/views/servicesView.vue`, `frontend/src/features/transactions/views/TransactionView.vue`, `frontend/src/api/client.ts`

**Create test:** `frontend/cypress/e2e/receipt-history-print.cy.js`; extend `backend/cypress/e2e-v2/{sales,frontend-contract}.cy.js`

**Interfaces proposed:** patient historyกรอง `patientId` จาก serverไม่กรองแค่current pageในbrowser; responseคงreceiptId/receiptNo; printนำreceipt detailที่persistแล้วมาใช้

- [x] RED: ผู้ป่วยA/Bซื้อคนละบิลและมากกว่าหนึ่งpage; historyAไม่ปนBและมีpagination; paidresponseนำไปreceiptถูกใบ; reload receiptแสดงยอด/วิธีชำระถูก; tax0และnetหลังลดถูก
- [x] ใช้patientId filterฝั่งserverที่มีอยู่ร่วมกับreceiptsOnly/pagination; ผูกการเปิดreceiptหลังPOSTสำเร็จ ไม่สร้างreceiptซ้ำเมื่อrefresh; errorไม่เปิดหน้าพิมพ์
- [ ] Q9: branch policy ของ history ตาม permission matrix ที่ยังไม่ยืนยัน; P5 คง global patient/sales access เดิม
- [x] แก้คอมเมนต์ ReceiptService จากtaxยังรอยืนยันเป็นtax0ตามR32; ซ่อนAssessmentจากการขายใหม่แต่ไม่ลบประวัติและenumย้อนหลัง โดยเพิ่ม V1001 และ legacy mapping
- [x] ไม่เปลี่ยนpersistedreceiptNoก่อนP6; displayเลขเอกสารและหน่วยครั้งต้องอิงQ4 ไม่แก้quantitycourseที่ราคาเป็นต่อคอร์สให้กลายเป็นต่อครั้ง
- [x] scoped API + browser history/print + frontend build; final backend v2 204/204 และ `npm test` 400/400 ผ่าน; commit `feat: complete patient receipt history and printing`

## P6: HN / Receipt numbering และความเข้ากันได้ของข้อมูล (R14/29, T13–14)

**Gate:** Q3/Q4ต้องตอบก่อนลงสูตร; schemacommentไม่ใช่businessapproval

**Modify:** `backend/src/main/java/com/sabyeguyz/clinicv2/patient/{HnGeneratorService,HnSequenceRepository,HnSequence}.java`, `backend/src/main/java/com/sabyeguyz/clinicv2/sale/{ReceiptService,ReceiptRepository}.java`, `backend/scripts/legacy-migration/migrate-legacy-to-v2.sql`

**Create:** migrationเลขถัดไปที่ว่าง, `backend/cypress/e2e-v2/identity-sequences.cy.js`; extend patients/sales specsและSchemaCheck

- [ ] ใส่approvedตัวอย่างyear/branch/month/runในtest fixtureแบบliteral; cases9หลักHN/8หลักreceipt, resetเดือน, overflow, branch01/02, oldIDsอ่านได้, voidแล้วเลขถัดไปไม่reuse
- [ ] ทดสอบ concurrencyด้วย `backend/cypress/tasks/parallel-requests.cjs` patternที่มี: simultaneous first-row insertและexisting-rowallocation; IDไม่ซ้ำและจำนวนsuccessตรงsavedrows
- [ ] รันให้REDตรงformat/locking; ใช้PostgreSQLatomicsequence/upsert+rowlock ไม่ใช้readthenwriteแบบHNปัจจุบัน; preserveold IDs ห้ามrenumberข้อมูลจริงอัตโนมัติ
- [ ] ปรับforwardconstraintให้รับapprovednewformatและlegacypolicy; ไม่เพิ่มchk_patient_hnที่ไม่มีในbaseline; updateentitycomments/APIvalidation/testsพร้อมกัน
- [ ] ทดสอบทั้งemptyDBและDBที่มีexistingHN/receipt; migrationต้องไม่ลบหรือเปลี่ยนFK; P9dryrunต้องผ่านก่อนrelease; commit `feat: align identifiers with approved numbering rules`

## P7: Course expiry/extension, transfer inheritance และประวัติ (R35–36/38–40บางส่วน, T5)

**Modify:** `backend/src/main/java/com/sabyeguyz/clinicv2/catalog/{Service,ServiceCatalogService,ServiceManagementController,CourseTemplateController,Course}.java`, `backend/src/main/java/com/sabyeguyz/clinicv2/course/{CoursePurchaseController,CourseTransferController,CourseUsagePrintService}.java`, `frontend/src/features/courses/views/CourseActionView.vue`, `frontend/src/features/settings/views/BackOfficeView.vue`, `frontend/src/api/client.ts`

**Create:** `backend/src/main/java/com/sabyeguyz/clinicv2/course/CourseExpiryService.java`, `backend/cypress/e2e-v2/course-expiry.cy.js`; extend course-transfers.cy.js

**Interfaces proposed:** catalogตั้งอายุเป็นdaysใช้validityValue/unitที่มี; purchase responseเพิ่ม `daysRemaining`; `PATCH /api/course-purchases/{id}/expiry` request `{expiryDate:"YYYY-MM-DD"}`; extensionrecord/auditเก็บold/new/actor/timeตามpatternในrepo; roleและexpiryinclusionตามQ7

- [ ] เริ่ม regressionที่ไม่ติดgate: buyA→transferB→readB ใช้Course IDเดิม/expiryเดิม; owner HN/nameและfull usage/transferhistoryถูก; totalสิทธิ์ไม่เปลี่ยนจากdisplay
- [ ] RED extensionเมื่อQ7ตอบ: authorizedเปลี่ยนแล้วreadback, unauthorized403, malformeddate400, expired/notexpiredขอบวัน, timezoneBangkok, auditค่าเก่าใหม่, สมาชิกBเห็นexpiryตามsharedCoursepolicyที่อนุมัติ
- [ ] ใช้LocalDate/Clockสำหรับdaysremainingตามpolicy: ตัวอย่างถ้าinclusiveexpiryอนุมัติแล้ว expiry=todayแสดง0วันและยังใช้ได้; อย่าทำformulaก่อนตอบQ7
- [ ] แก้servicecatalogให้DayและpositivevalidityValueสำหรับnewcourse; คอร์สเดิมMonth/Yearไม่ถูกconvertย้อนหลังเงียบ ๆ; lookupอ่านvaliditydaysตรงความหมายจริง
- [ ] ปุ่มต่ออายุ/แก้expiryและค้นrecipientจากpatientApi; ไม่สร้างrecipienttherapistselector; printใช้ชื่อนักกายภาพที่persistแล้ว; ไม่เพิ่มtreatingStaffId/เปลี่ยนBonus/Pool/credit
- [ ] F3 เพิ่มภาพแนบตอนเพิ่มคอร์ส: หลัง Q13 ตอบ ให้แยก endpoint และ metadata ของ purchase attachment ออกจาก `course_usage_print.document_snapshot`; เสนอ `POST /api/course-purchases/{id}/attachments` แบบ multipart และ `GET /api/course-purchases/{id}/attachments/{attachmentId}` ที่เช็กสิทธิ์สาขา/ผู้ป่วย; ระบุ MIME allowlist, ขนาด/จำนวน, storage, access audit, deletion/retention ใน contract ก่อนทำ migration; tests upload→readback, unauthorized, wrong course, MIME/size invalid และลบ/เก็บตาม policy
- [ ] GREEN expiry/transfer/courses/print contract + browsercourseflow + build; commit `feat: manage course expiry without commission changes`

## P8: Reports และ backlog ที่ต้องมี specification เพิ่ม (R23/43, T21/23/25/26)

แบ่งเป็นreleaseย่อยอิสระ ไม่จำเป็นต้องทำพร้อมกันและไม่ใช่dependencyของP2–P7

**Dashboard files:** `backend/src/main/java/com/sabyeguyz/clinicv2/report/{ReportController,RevenueSplit}.java`, เพิ่ม `DashboardService.java` ในpackageเดียวกันหากcontroller logicขยาย; `frontend/src/features/dashboard/views/DashboardView.vue`, `backend/cypress/e2e-v2/reports.cy.js`

- [x] Q11ตอบแล้ว: fixturesข้ามวันขาย/รับเงิน, void, ลงทะเบียนใหม่แม้ยังไม่มา/ลูกค้าเก่ามา2ครั้งนับ1คน, สองสาขาและขอบวันBangkok; APIใหม่RED9→GREEN9 และfullAPI226/226, browser120/120; ดู `docs/testing/2026-10-15-P8-Dashboard-Q11.md`
- [x] คงsummaryเดิมและเพิ่มsalesTotal/cashReceivedตามช่วงที่เลือก, newCustomers/returningCustomers/courseRenewals/packageUpdates/dailySales; วันศูนย์ครบ; templateupdateเป็นeventcountและlabelทั้งคลินิก; auditไม่เดาประวัติก่อนเริ่มบันทึก; Q11ยืนยันเป็นmetricไม่ใช่ปุ่ม
- [ ] ทดสอบbranchfilterตามP1และfullreportsregression; ไม่เพิ่มTreatmentFee/Tier/commissionmetric

**Google Calendar gate Q12:** ใช้ `appointment/Appointment.java`, migrationใหม่สำหรับsyncstate/outboxเมื่อจำเป็น และpackageใหม่ `backend/src/main/java/com/sabyeguyz/clinicv2/calendar/`; testใหม่ `backend/cypress/e2e-v2/calendar-sync.cy.js`

- [ ] ได้account/calendar/consent/conflictpolicyก่อนออกOAuthcontract; reuseexternal_calendar_event_idที่schemaมี; credentialsไม่ส่งในAPI/commit
- [ ] แผนintegrationเมื่อspecพร้อม: enqueueappointmentcreate/update/cancel, workerretryแบบidempotent, inboundeventreconcile, mappingต่อbranch, origin markerกันecho-loop; testscreate/update/deleteทั้งสองทาง, duplicateevent, expiredtoken, retry, eventotherbranch, disconnect และredactedpatientdata
- [ ] ยังไม่เลือกSDK/GoogleAPIรายละเอียดในรอบนี้; ตอนทำใช้officialdocumentationปัจจุบันและtestproviderfixtureก่อนทดลองบัญชีที่อนุญาต

**Rooms gate Q12:** `resource/{Room,ResourceController}.java`, BackOfficeView; confirmroomstatusก่อนmigration; testsopen/busy/repair/backwardbooleanmapping; แยกจากนัดที่ไม่ใช้room

**Email/reset gate Q12:** `auth/{AppUser,AppUserService,AuthController}.java`, forwardmigrationและauthspec; ต้องมีemailunique/verification/deliverypolicy; testsบัญชีมีไม่มีตอบเหมือนกัน, singleuseexpiringtoken, hashedtoken, resetแล้วoldcredentialใช้ไม่ได้; ไม่ส่งอีเมลจริงในtestหรือในรอบวางแผน

แต่ละreleaseย่อยต้องมีspec/contractและtestsที่กล่าวไว้ก่อนเขียนimplementation; commitแยกDashboard/Calendar/Rooms/Auth

## P9: Data migration, CI diagnosis และ release verification (T28–31)

**Modify/read:** `backend/scripts/legacy-migration/migrate-legacy-to-v2.sql`, `backend/scripts/legacy-migration/ci/{legacy-fixture.sql,verify-migrated-app.sh}`, `.github/workflows/{backend-ci,v2-api-ci,schema-ci,legacy-migration-ci,frontend-e2e-v2}.yml`, `docs/api-v2-frontend-contract.md`; create `docs/testing/2026-10-14-Non-Commission-Acceptance.md` เมื่อมีผลจริง

- [ ] เมื่อP2/P6schemaตัดสินแล้ว dryrunกับสำเนาข้อมูลที่อนุญาต; comparepatient/receipt/coursecounts, uniqueIDs, FKorphans, sumsยอดสุทธิ/รับเงินจริง, expiry, fieldที่legacyมี; บันทึกทุกfieldที่dropหรือไม่มีที่เก็บ ไม่เงียบข้อมูลสูญหาย
- [ ] Restoreซ้ำจากsnapshotและrunmigrationให้ผลrepeatable; testข้อมูลHN/receiptเก่าและrecordยกเลิก; เตรียมbackup/rollbackprocedureก่อนใช้ข้อมูลจริง
- [ ] ตรวจCI logที่Mavenfailจริงก่อนแก้: repoavailability/network/rate limit/pluginversion/cache; ใช้pin/cache/retryเฉพาะdownloadfailureที่พิสูจน์ได้ ไม่retryassertionfailureให้ดูgreen
- [ ] Main runtime/FrontendE2ECIเป็นv2แล้ว; verifyexistingwiringและไม่ทำcutoverซ้ำ; legacydeletionเป็นงานแยกที่ต้องตรวจreference/deploy/rollbackและขอบเขตcommissionก่อน
- [ ] Fullbackendv2suiteรวมcommissionเดิมต้องผ่านเพื่อพิสูจน์regression; frontendunit/buildและbrowserNew→register→book→service→pay→print→history→expiry/transfer; receiptเดียวต้องพิสูจน์coursepurchaseและpaymentจริงถ้าอ้างว่าขายคอร์สรับเงินครบ
- [ ] RunRuntimeCheck/SchemaCheckตามcommandsในdocs/testing/2026-10-13-Phase-2-V2-Acceptance.mdบนDBทดสอบ; สรุปผลพร้อมhash/versions/ข้อจำกัด; `git diff --check` และ `git status --short`
- [ ] อัปเดตsource-to-taskmatrix: ไม่มีR/Tตกหล่น; gatedงานยังไม่ทำต้องระบุ; commitเอกสารผลและเสนอPR review; deploy/migrateข้อมูลจริงไม่ใช่ส่วนที่อนุญาตจากคำขอวางแผนนี้

## เกณฑ์ปิดงานแต่ละ release

พฤติกรรมที่รับเข้าreleaseมีAPI/DBreadbackและbrowserหลักฐานตามระดับที่แตะ; schema/contract/docsตรงกัน; ไม่มีcommissionruleเปลี่ยน; sourceที่ขัดกันยังไม่ถูกเดาว่าresolved; testเก่าไม่ถูกนำมาอ้างว่ารันใหม่

## แพ็กแรกที่แนะนำเริ่มหลังเลือก execution

P0 → P1ส่วนสิทธิ์นัดที่ชัด → P2 mappingชื่อกลาง/title/genderและlookup → P3search/note/reason/Newregistration จากนั้นP4/P7ตามคำตอบgate; P6กับCalendarไม่ควรทำก่อนมีคำตอบ

ตรวจแผนรอบนี้: R1–R43และT1–T31มีscopeหรือเหตุผลพักครบ; F1–F4 ตรวจ delta แล้ว โดย F3 เพิ่ม requirement รูปแนบ; pathที่เสนอCreateแยกจากไฟล์ที่มี; คำตอบQ1–Q13ยังไม่ถูกถือเป็นrequirementยืนยัน
