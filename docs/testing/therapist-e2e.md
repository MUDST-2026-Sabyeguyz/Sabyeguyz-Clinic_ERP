# Therapist E2E coverage

## Scope and test design

ชุดนี้ขยายจาก Patients BCC ไปทุกหมวดและ route ของ Therapist ที่มีอยู่ใน `frontend/src/router/index.ts`
ใช้ Chrome จริง → Vue/Vite localhost:5173 → backend v2 127.0.0.1:18080 → PostgreSQL16 disposable `patients_bcc` พอร์ต15434
ไม่เท่ากับ statement/branch coverage 100% หรือทดสอบทุก combination ทั้งระบบ

- BCC: Patients search/branch/sort/page ในชุดเดิม และช่องทางชำระเงินในชุดใหม่ (PromptPay base, เปลี่ยนเป็น BankTransfer/CreditCard/Cash ทีละตัว โดยคงผู้ป่วย/รายการ/จำนวนเดิม)
- Boundary/negative: ข้อมูลจำเป็น, เบอร์โทร9/10หลัก, identity12/13หลัก, emailผิดรูปแบบ, วันเกิดอนาคต, quantity0/เกินสิทธิ์/สิทธิ์สุดท้าย, expiryเมื่อวาน/วันนี้, เหตุผลยกเลิกสั้น/ครบ, ข้อมูลซ้ำและIDไม่พบ
- State transitions: นัดหมาย, ใช้สิทธิ์/ยกเลิกคอร์ส, ขาย/ยกเลิก/ประวัติ, ค่าตอบแทน pending→locked→closed→paid และ adjustmentงวดถัดไป
- Authorization: anonymous, Therapist, Therapist2, manager-only UI routes/API, ข้อมูลค่าตอบแทนของตนเอง
- Workflow: ทำผ่านหน้าเว็บแล้วอ่าน API ตรวจข้อมูลที่ persist; API-only cases ระบุ `API:` ในชื่อหรืออธิบายในตาราง

## Traceability by feature

| Feature / routes | Spec / cases | Verification and limits |
|---|---|---|
| `/`, `/staff/login`, staff menu, logout/reload | auth.cy.js AUTH-01–04,08 | ล็อกอินฟอร์มจริง, passwordผิด, session, logout, anonymous7routes |
| Manager-only routes | auth.cy.js AUTH-05 | ทดสอบ direct URL ทุก manager-only route ที่ประกาศ รวม adjustment subroutes; ต้องกลับ Patients |
| Backend permissions | auth.cy.js AUTH-06 | ปฏิเสธ branch/user creation403 และ unauthenticated patient401; payloadอื่นครบไม่ให้ validationบดบัง authorization |
| `/staff/commission/formula` | auth.cy.js AUTH-07 | สูตรที่แสดงต้องอ่านจาก APIได้และค่ามือ45ตาม seed |
| `/staff/patients` | ชุดเดิม patients-bcc.cy.js BCC-00–15, SUP-01–10 | ค้นหา/กรอง/เรียง/แบ่งหน้า/loading/error, foreign registration, token401; แยก configและผลรัน |
| `/staff/patients/new` | patients.cy.js PAT-01–04,10–11 | ลงทะเบียนไทย, required/format/boundary, duplicateไม่สร้างซ้ำ, วันเกิดอนาคต, ยกเลิกconfirmationไม่บันทึก |
| `/staff/patients/:id` | patients.cy.js PAT-05–06,09 | แก้ไขและreloadตรวจค่าจริงทั้ง referralว่าง/มีค่า, ยกเลิกedit, invalid/not-foundID |
| `/staff/patients/:id/history` | patients.cy.js PAT-07–08 | payment/course empty, receipt filtering/preview/print; stubเฉพาะwindow.printเพื่อไม่ส่งงานจริงไปเครื่องพิมพ์ |
| `/staff/appointments`, `/new`, `/:id/edit` | appointments.cy.js | สร้างCURRENT/NEW, ค้นหาHNในpatient picker, validation/contact, explicittherapist, แก้ไข/เลื่อน, ยกเลิกพร้อมเหตุผล, conflict, date range |
| `/staff/appointments/calendar` | appointments.cy.js | day/week navigation, occupiedslotedit, emptyslotcreate, therapistfilter |
| Appointment lifecycle | appointments.cy.js API-only cases | check-in/start/complete/no-show+terminal rejection ผ่านAPI; ตรวจcalendar renderingเฉพาะno-show ส่วนcheck-in/start/completeตรวจAPI persistence; UIไม่มีปุ่ม lifecycle จึงไม่อ้างเป็นbrowser mutation |
| `/staff/services`, `/staff/billing` | services.cy.js; auth AUTH-08 | paymentmethods, checkout/review/cancelconfirmation, ความสอดคล้องของline/subtotal/paid/payment, actualreceipt, note persistence, course checkout, mixedpaymentnegative |
| `/staff/courses` | courses.cy.js | UIuseconfirmation/backout, APIledgerและbalance, transferconservation+invalid/self/zero/excessive, sharedrecipient, inactivehistory, exhaustion+expiryboundaries |
| `/staff/patients/:patientId/courses/:courseId` | courses.cy.js CRS-ROUTE-01 + history | directrouteมีหลายผู้ป่วยต้องเลือกpatient/courseตามURL; ประวัติการใช้สิทธิ์ |
| `/staff/transactions` | transactions.cy.js | HN/receipt/no-match, detail/backout, reasonvalidation, confirmation, persistedVOIDED+audit/reload, usagetab, duplicatecancelAPI |
| `/staff/compensation` | compensation.cy.js | pendingunknown, locked totals `10000×5%÷13=38.46`, ค่ามือ45, net83.46, month/tabnavigation |
| `/staff/compensation/commission/:id?/source?` และ treatment equivalents | compensation.cy.js | nonemptydetail/source regressions; directmissingIDempty states แยกต่างหาก ไม่อ้างdetailสำเร็จหากprerequisiteไม่มีรายการ |
| `/staff/compensation/payments`, `/adjustment/:id?` | compensation.cy.js | paidreference/amount, approved+50 nextmonth, paidงวดเดิมไม่เปลี่ยน, adjustmentdetail, missingID |
| Therapist isolation | compensation.cy.js | Therapist2ไม่เห็นpayments/linesของTherapist1 และปฏิเสธmanager mutation; ไม่ได้สร้างadjustmentเพื่อทดสอบcross-user adjustment isolation |

## Fixtures and assumptions

ก่อนแต่ละ test: loginmanager → `/__cypress/reset` → loginTherapist/Therapist2 แล้วเตรียมข้อมูลที่จำเป็น
browser/APIภายใต้การทดสอบใช้บัญชีTherapist; managerใช้เฉพาะเตรียมfixtures/payroll/adjustment
sharedhelperอยู่ `frontend/cypress/support-therapist/`; session/fixturesแชร์ผ่านCypress runtime เพราะsupportและspecเป็นคนละbundle

`CypressCourseExpiryController` อยู่เฉพาะ `backend/src/test`, profile `cypress-v2`, manager-only
ใช้เปลี่ยน expiry_date ของcourseที่สร้างในfixtureเพื่อทดสอบเมื่อวาน/วันนี้ตามนาฬิกาbackendจริง
ไม่ใช้fakebrowserclockอ้างว่าbackendหมดอายุ และไม่เพิ่มendpointในproductionjar
fixture endpointไม่ได้ตรวจชื่อdatabaseเอง: ผู้รันต้องตรวจDATABASE_URLชี้disposableDBก่อนเปิดresetflag

Cash checkoutและcourse checkoutเป็น expectation ที่อิงAPIและflowที่ตกลงครอบคลุม แต่UIปัจจุบันไม่มีcontrolให้เลือก
รายงานเป็นช่องว่างUI/ความต้องการที่ควรยืนยัน ไม่ตีความว่าได้รับอนุญาตให้เพิ่มฟีเจอร์จริงในงานนี้
quantity/discountหน้าcheckoutเป็นค่าคงที่/readonly จึงตรวจ arithmeticของquantity1/discount0 ไม่มีการสร้างinputสมมติ
การพิมพ์ตรวจpreview/confirmationและการเรียกwindow.print ไม่ใช่หลักฐานว่าเครื่องพิมพ์จริงพิมพ์สำเร็จ
GoogleCalendarintegration, ทุกขนาดหน้าจอ, concurrency/load, externalpaymentgatewayและPDFvisualเทียบต้นฉบับอยู่นอกชุดนี้

## Run

ห้ามรันพร้อม Patients BCC/backend E2E อีกชุดที่ใช้ฐานข้อมูลเดียวกัน เพราะresetก่อนทุกcase
หยุด/ปิดการรันในCypressGUIก่อนรันcommandline

เปิดbackendจากโฟลเดอร์backend (container `clinic-patients-bcc-pg` ต้องทำงาน):

```powershell
$env:DATABASE_URL='jdbc:postgresql://127.0.0.1:15434/patients_bcc'
$env:DATABASE_USERNAME='clinic'
$env:DATABASE_PASSWORD='clinic'
mvn --batch-mode --no-transfer-progress -Pcypress-v2 test-compile spring-boot:start
```

เปิดViteจากfrontendอีกterminal:

```powershell
$env:VITE_API_PROXY_TARGET='http://127.0.0.1:18080'
npm run dev -- --host localhost --port 5173 --strictPort
```

รันจากfrontendอีกterminal:

```powershell
$env:THERAPIST_E2E_RESET='1'
npx cypress run --config-file cypress.therapist.config.ts --browser chrome
# GUI:
npx cypress open --config-file cypress.therapist.config.ts
```

Configแยกจากdefaultsuite ป้องกันresetฐานข้อมูลปกติโดยไม่ตั้งใจ
JUnitอยู่ `frontend/test-results/therapist-*.xml` และภาพfailureอยู่ `frontend/cypress/screenshots/`
ผลรันและcase-by-caseอยู่ [therapist-e2e-results.md](therapist-e2e-results.md)
