# Task 2: contract และสถานะตรวจรับ

ฐานอ้างอิง: `b73f8cf762657ae95093a7d4025ee6e7b8d77bc8` บวกแพตช์ `AuthController.AuthResponse.staffId` ที่ผู้ใช้อนุญาตวันที่ 31 ตุลาคม 2026 ไม่มี migration เพิ่ม

| งาน | หลักฐานจาก main | Frontend ที่ปรับ | ผลตรวจ |
| --- | --- | --- | --- |
| ขายพร้อมรับชำระ | SaleController.CreateRequest/create; ReceiptService.finalizeTotals; PaymentTransactionService.record | client.ts, careTransactions.ts, SaleCheckoutView.vue, servicesView.vue, ManagerServicesView.vue | ใช้ SERVICE/COURSE, ผู้ขาย/ผู้รักษาแยกกัน, หนึ่ง payment เท่ากับยอดสุทธิ; unit/component tests และ API runtime ผ่าน; checkout/receipt browser ทั้งสอง role ผ่าน |
| ซื้อคอร์ส | SaleController COURSE item; CoursePurchaseController.PurchaseRequest/purchase | ขายพร้อมชำระใช้ /api/payments; client ของ /api/course-purchases มี caseOwnerStaffId/label | course quantity ถูกขยายเป็นหนึ่ง COURSE item ต่อหนึ่งคอร์ส เพราะ main ไม่ใช้ quantity ในเส้นทางนี้; ไม่สร้าง Visit; tests ผ่าน |
| จำนวนครั้ง | CatalogController.courses; CoursePurchaseController.toResponse | CourseActionView ใช้ purchased, BackOfficeView ใช้ sessions โดยไม่บวก bonus | sessions/purchased รวมโปรโมชันแล้ว; helper tests/ตรวจ source ผ่าน |
| ใช้คอร์สเดี่ยว | CoursePurchaseController.UseRequest/useTogether | client CourseUseRequest, CourseActionView, careTransactions.ts | ส่ง patientId ผู้รับบริการจริง/appointmentId/treatingStaffId/coursePurchaseId; tests ผู้รับโอนผ่าน |
| ใช้หลายสิทธิ์ | POST /api/course-usages/batch รับ {usages}; response lockedCourses.values เป็นแถวคอร์ส distinct | CourseUsagePicker, CourseActionView, courseApi.useBatch | ส่ง POST เดียว, อ่านผลตาม outer coursePurchaseId, ไม่สมมติ response หนึ่งแถวต่อ usage; tests ผ่าน |
| บริการผสม | SaleController.courseUsages + CourseUsageBatchService.recordForVisit | SaleCheckoutView ส่ง items/courseUsages พร้อมกันผ่าน builder | ต้องมี SERVICE ในคำขอเดียว ไม่ขายใบว่างหรือแยก usage POST; tests ผ่าน |
| ป้าย/วิธีชำระ | PaymentStatusOptionController, PaymentMethod.canonicalName | ชนิด FULL/PAID/INSTALLMENT/DEPOSIT, เลือกจาก enabled, ช่องทาง active เฉพาะ CreditCard/PromptPay/BankTransfer | ไม่เลือก FULL เอง; Cash ไม่อยู่ในรายการใหม่; tests validation ผ่าน |
| รายละเอียดใบเสร็จ | GET /api/receipts/{saleId}; SaleController.Detail/ItemResponse/PaymentResponse | ReceiptView.vue, client SaleDetail | main ไม่ส่ง branch snapshot/unitPrice/lineDiscount/paidAt: อ่านสาขาจาก branchCode, ค่าไม่ทราบแสดง — ไม่เดา; component test ผ่าน |
| ย้อน usage | GET course-usages: id คือ Treatment movement ตาม contract; POST course-movements/{movementId}/reverse | TransactionView.vue | ใช้ row.id เป็น movement reference ไม่สมมติว่ามี usageId; ตรวจผล reverse จากรายการล่าสุด |
| Progress/recovery | AppointmentProgressController + AppointmentVisitResolver | ส่วน progress ที่ทำรอบก่อนและ pending sale guards | ไม่มี requestKey ใน main; ไม่อ้าง idempotency ที่ backend ไม่รองรับ; timeout/409 อ่านผลและคง pending ถ้ายังยืนยันไม่ได้ |
| Staff identity | main StaffController ไม่มี userId/role; AuthResponse เดิมไม่มี staffId | AuthController เพิ่ม staffId; frontend AuthResponse, own appointment guards, staff filters ใช้ position | ผู้ใช้อนุญาต backend แล้ว; AuthStaffIdentityCheck ผ่าน (userId ต่าง staffId / staff null); session เดิมต้องล็อกอินใหม่ |

## สิ่งที่ตรวจจริงกับระบบแยก

- สร้าง project `clinic-task2-test` แยก PostgreSQL volume/network/localhost ports ใช้ source จาก git archive baseline ไม่ใช้ backend งานค้าง
- ปิด Google Calendar sync และใช้เฉพาะ demo seed ของ baseline
- backend baseline เริ่มทำงานและ Flyway 26 migrations ผ่านถึง V1020 อ่าน catalog/staff/branches/patients/payment options จาก API จริงแล้ว
- ระหว่าง rebuild สำหรับ auth patch Docker/WSL หลุดและ C เหลือ 0 ไบต์ ผู้ใช้อนุญาต restart; Docker ยังเริ่มไม่สำเร็จ และ log มี socket `dockerInference` error ไม่มีการ reset factory หรือลบ volume
- เก็บ crash dumps ของเหตุการณ์นี้และ backup Maven exec plugin ที่เพิ่งดาวน์โหลดไว้ใน `state/task2-runtime` บน D ไม่มีการลบข้อมูลเดิม
- compile/test-check/package ของ baseline + auth patch บน D ผ่าน แต่ยังไม่รันธุรกรรมขาย/ใช้คอร์ส/rollback กับ PostgreSQL หลัง patch

## ทำต่อเมื่อ Docker กลับมา

1. ตรวจระบบเดิมที่เคยรัน `sabyeguyz-postgres-1`, `sabyeguyz-backend-1`, `sabyeguyz-frontend-1` และคืนสถานะทำงานหลัง restart ที่ได้รับอนุญาต ห้ามเปลี่ยนฐาน/volume เดิม
2. เปิดเฉพาะระบบทดสอบแยกจาก `state/task2-runtime/compose.yaml` ตรวจ port ที่ Docker จัดให้ใหม่ ไม่สมมติว่าเหมือนรอบก่อน
3. ใช้ backend baseline + auth patch source เดียวกับที่ compile check ผ่าน; แพตช์เพิ่มเฉพาะ staffId ไม่แก้ migration
4. ตั้ง `TASK2_TEST_API` เป็น localhost port ของระบบทดสอบ, `TASK2_TEST_CONFIRM=isolated`, `TASK2_TEST_USERNAME`, `TASK2_TEST_PASSWORD` เป็นบัญชี seed ทดสอบ (ห้ามใช้บัญชีจริง)
5. รัน `node scripts/test-task2-contract.mjs`; ตั้ง `TASK2_TEST_REPORT` เป็น path บน D หากต้องการรายงาน JSON
6. runner ใช้ payload builder เดียวกับ frontend ตรวจ single payment/underpayment/duplicate409/NEW/ซื้อคอร์ส/ผู้รับโอน/batch/mixed/rollback/reverse เมื่อผ่านจึงบันทึกผล runtime ไม่ถือว่าการมี runner คือผ่านแล้ว
7. ทดสอบ browser บน backend นี้ด้วย ทั้ง Manager และ Therapist ที่ล็อกอินใหม่ (ต้องได้ staffId)

สถานะก่อนเปลี่ยนไปใช้ PostgreSQL portable บน D: runtime ยังไม่ครบ ณ เวลาบันทึกส่วนนี้; ให้ใช้สถานะล่าสุดท้ายไฟล์เป็นหลัก

### ใช้พื้นที่ D แทน Docker สำหรับ runtime acceptance — 2026-10-31

- ผู้ใช้ขอใช้ D แทน C จึงแยก PostgreSQL portable 16.15 (binaries ทางการ EDB) และ Java backend ไปอยู่ใน `state/task2-runtime` บน D; ไม่มี Windows service และไม่ได้ย้าย Docker disk/volume เดิม
- PostgreSQL data: `state/task2-runtime/postgres-portable-data`; เปิดเฉพาะ 127.0.0.1:55432; backend jar จาก clean archive b73f8cf + AuthController staffId patch เปิดเฉพาะ 127.0.0.1:20014; TEMP/TMP/log อยู่ D
- Flyway ผ่าน 26 migrations ถึง V1020 บนฐานใหม่ `clinic_task2_test` ไม่ใช้ฐานระบบเดิม
- รัน `node scripts/test-task2-contract.mjs` ผ่าน exit 0 ครบ 11 กลุ่ม: login staffId/null Staff, full payment/underpay/duplicate409/receipt saleId, NEW servedPatientId โดยไม่ link patient, ซื้อสองคอร์ส, dedicated purchase, ผู้รับโอนใช้สิทธิ์, batch 3 usages/2 rows, mixed, rollback, reverse movementId
- รายงานจริง: `state/task2-runtime/contract-report.json` (ignored); ไม่บันทึก token/password ในรายงาน
- แก้สมมติฐานใน runner: demo seed ของฐานใหม่อาจมี user.id เท่ากับ staff.id จึงตรวจ staffId มีอยู่จริงและสาขาตรง; กรณี ID ต่างกันตรวจแยกด้วย AuthStaffIdentityCheck ที่ผ่านแล้ว ไม่บังคับ seed ต้องต่างกัน
- ข้อความก่อนหน้านี้ว่า runtime transactions ถูก Docker บล็อก ถูกแทนที่ด้วยผล PostgreSQL portable นี้แล้ว; browser acceptance กำลังตรวจด้วย Cypress กับ frontend localhost:55174 (proxy ไป backend ทดสอบ)
- Docker เดิมยังมีปัญหา startup/socket และพื้นที่ C ต่ำ ยังไม่ยืนยันคืนสถานะ containers เดิม; ไม่มี factory reset หรือลบข้อมูล/volume เดิม

### ผลตรวจรับล่าสุดของ Task 2 — 2026-10-31 03:13 Asia/Bangkok

- Cypress `frontend/cypress/e2e/task2-real-backend.cy.js` ผ่าน 2 tests / exit 0: Manager และ Therapist ล็อกอินจริง ซื้อคอร์สโดยไม่มีนัด ขาย SERVICE จาก UNCONFIRMED ด้วย therapist ของนัด ยืนยันรับชำระ และเปิดใบเสร็จจริง ไม่มี API mocks
- พบ regression ระหว่าง browser test: หลังขาย SERVICE สำเร็จ progress FOUND ทำให้ panel บัง success UI; แก้ SaleCheckoutView template ให้ success ที่เพิ่งบันทึกแสดงได้ และ save() ปฏิเสธการส่งซ้ำเมื่อ stage=success; การเปิดนัดเดิมยังถูก progress guard ตามปกติ
- เพิ่ม regression ใน SaleCheckoutView.spec.ts ยืนยันก่อนแก้ fail และหลังแก้ pass; focused suite ล่าสุด 12 files / 87 tests ผ่าน; npm run build (รวม type-check) exit 0; git diff --check ผ่าน
- API runtime 11 กลุ่มผ่านก่อน browser; AuthStaffIdentityCheck ของ clean baseline + auth patch ผ่านก่อนหน้านี้; ไม่แก้ backend เพิ่มจาก staffId และไม่มี migration เพิ่ม
- browser test ต้อง opt-in `CYPRESS_TASK2_ISOLATED=yes` ใช้ base URL localhost:55173 หรือ 55174 และฐาน seeded disposable ใหม่ต่อรอบ (สร้างนัดไว้และไม่ลบ); ห้ามชี้ไปฐานใช้งานจริง
- proxy ชั่วคราว `state/task2-runtime/vite-test.config.ts` ส่ง Origin localhost:5173 ตาม baseline WebConfig เพื่อรองรับพอร์ตทดสอบ55174 โดยไม่เปลี่ยน backend; production Vite config เดิมไม่ได้แก้
- ข้อจำกัดหลักฐาน: browser รอบนี้ครอบคลุม checkout/receipt ของสอง role; transferred/batch/mixed/rollback/reverse ตรวจ API จริงและ component/helper tests ไม่ได้กดครบทุกหน้าจอผ่าน browser; งาน Task 1 ทั้งชุดและ Task 3/4 มี checklist ของตนเอง ไม่ถือว่าผ่านทั้งหมดจากผลนี้
- Docker เดิมยังเริ่มไม่ได้หลัง restart ที่ได้รับอนุญาต: ไม่ยืนยัน original containers กลับมาออนไลน์; ไม่ factory reset, ไม่ย้าย disk image, ไม่ลบ volume/data
- รักษา staged/unstaged งานผู้ใช้ ไม่มี commit/push/deploy

- หลังตรวจรับ ปิดเฉพาะ Vite/backend/PostgreSQL portable ของรอบนี้แล้ว เก็บฐานและรายงานบน D ไว้ ไม่เปลี่ยน startup/service ของเครื่อง


### งานต่อเนื่อง Task 3–5 — เริ่มตรวจ/ปรับ 2026-10-31

- ผู้ใช้อนุญาตให้ทำ Task 3–5 ทั้งหมด โดยยึด baseline `b73f8cf762657ae95093a7d4025ee6e7b8d77bc8`, รักษางานเดิม, แจ้งก่อนแก้ backend/database, ไม่ commit/push/deploy และหลัง Task 5 ต้องหยุดรออนุมัติ
- ตรวจ `git status --short` พบ staged/unstaged changes จำนวนมาก รวมการลบหน้า commission และ backend modifications/migrations ที่ไม่อยู่ใน main; ห้าม restore/reset/แก้ backend เหล่านั้นเหมารวม
- ตรวจ commission contract จาก `git show b73f8cf`: payroll payments/prepare/close รับ month+branchCode; close-month รับ path month `YYYY-MM` + branchCode และตอบ array; branch-closing เป็น endpoint แยก รวม reopen empty branch reason, pay request paidDate/reference; adjustment application body ต้องมี branchCode
- เริ่มแก้ `frontend/src/api/client.ts`: เพิ่ม branch-aware payments/closings/closeMonth, branchClosing/reopenEmptyBranch, prepare/close payments, reopen/pay, myPayments month และ branchCode ใน adjustment application
- ปรับ callers บางส่วน: CommissionOverviewView, CommissionPersonDetailView, CommissionAdjustmentScheduleView, CommissionClosingView เพื่อส่งเดือน/สาขาตาม main; งานยังไม่ครบและอาจมี type errors ที่ต้องตรวจ
- Task 3 ยังต้องแก้: remaining callers ของ changed signatures, direct user/selected-branch scoping, empty month branch closing UI, negative temporary closing IDs, OPEN/CLOSED/PAID constraints, null pendingSessions, pay forms/date/reference, reopen reason, preview race, treatment fee controls/override-rate/scheme version และ focused tests
- Task 4 ยังไม่ได้ implementation: account email constraints, permissions overrides/reset/current auth response, calendar role gating/read-only therapist status, fees/scheme rules, reports/dashboard semantics และ tests
- Task 5 ของรอบนี้ยังไม่ได้รัน build/tests/runtime; ต้องใช้ clean baseline runtime แยกบน D (portable PostgreSQL อยู่ที่ `state/task2-runtime`, สามารถ start ด้วย pg_ctl; backend baseline archive/jar ต้องตรวจว่ามีอยู่และ rebuild/start จาก clean pinned source + staffId patch ที่อนุญาตก่อน) ห้ามใช้ backend working tree ปนฐานทดสอบ
- backend/database ยังไม่ได้แก้เพิ่มจาก staffId ที่อนุมัติไว้ก่อนหน้านี้; หาก contract ข้อใดจำเป็นต้องแก้ backend ให้แจ้งก่อน
- สถานะ: IN PROGRESS, ห้ามถือ Task 3–5 complete และห้ามเริ่มงานถัดไปหลัง Task 5 โดยไม่มีอนุมัติผู้ใช้

### Checkpoint ก่อนอนุมัติ backend support — ประวัติ ณ 2026-10-31

ตารางนี้บันทึกสถานะก่อน backend support patch; รายการที่ระบุ BLOCKED ถูกปิดในหัวข้อ “สถานะปิดรายการค้างทั้งหมด” ท้ายไฟล์

ใช้ baseline `b73f8cf762657ae95093a7d4025ee6e7b8d77bc8`; รายการ IN PROGRESS ข้างต้นเป็นประวัติ ไม่ใช่สถานะปัจจุบัน

| ชุด | ผล | Frontend / หลักฐาน |
| --- | --- | --- |
| นัดหมาย / NEW | Frontend flow และ runtime ผ่าน | appointment create/reschedule ส่ง endAt; NEW/UNCONFIRMED ลงทะเบียนแล้วเข้าบริการด้วย patientId+appointmentId โดยไม่ linkPatient; `state/task2-runtime/task1-contract-report.json` |
| ขาย / ใช้คอร์ส / progress | ผ่านตามหลักฐานก่อนหน้า | runtime API 11 กลุ่ม + Cypress Manager/Therapist 2 tests; ดูส่วน Task 2 ในแผน |
| Commission / payroll | ผ่านฝั่ง frontend และ API runtime | branch/month contract, payment array, PAID guard, null handling, paidDate/reference/reopen reason; `state/task2-runtime/task3-contract-report.json` ผ่าน 5 checks |
| Account / permission | email validation และ override ที่มีผ่าน; เพิ่ม override ใหม่ BLOCKED | `BackOfficeView.vue` เรียก GET/PUT/DELETE จริง; main ไม่มี permission catalog endpoint จึงไม่มี permissionId สำหรับสร้าง override ใหม่โดยไม่ hardcode |
| Calendar / reports / dashboard | ผ่าน focused tests/build | Manager edit+sync, Therapist read-only own-branch status; report labels แยกวันที่ตามชนิดรายงาน; dashboard อ่าน metrics ตาม main response |
| Final frontend verification | ผ่าน | 15 Vitest files / 102 tests; `npm run build` (vue-tsc+Vite) exit 0; `git diff --check` ผ่าน |

#### Contract blockers ที่ต้องให้ backend owner แก้ก่อนรับรองครบ

- PatientController ใน main ไม่รับหรือ persist `drugAllergy`, `foodAllergy`, `chronicDisease`, emergency contact และ `privacyConsent`; Task1 runtime ส่งค่าแล้วตรวจ profile พบว่าไม่ได้บันทึก
- ไม่มี permission-catalog API ใน main: endpoint user permissions อ่าน/เปลี่ยน/ลบเฉพาะ override ที่มีได้ แต่ frontend สร้างรายการใหม่ไม่ได้โดยไม่เดา ID
- JWT ใน main ยังไม่มี immediate revocation; หลังเปลี่ยน role/deactivate บัญชี token เก่าอาจใช้ต่อจนหมดอายุ 1 ชั่วโมง
- AppointmentStatus Java enum มี legacy members แต่ migration V1020 บังคับฐานข้อมูลเหลือ `Unconfirmed`, `Confirmed`, `Cancelled`; UI/API types จึงยึด 3 ค่าที่ persist ได้

ข้อความนี้เป็นข้อสรุปของ checkpoint ก่อนอนุมัติ backend support เท่านั้น; สถานะหลังทำ support patch แสดงในตารางล่าสุดด้านล่าง

### สถานะปิดรายการค้างทั้งหมด — 2026-10-31

หัวข้อนี้ใหม่กว่าตารางและ blocker ด้านบน และแทนที่สถานะเก่าที่บันทึกไว้: ใช้ `b73f8cf762657ae95093a7d4025ee6e7b8d77bc8` เป็น baseline; backend support ที่จำเป็นทำเพิ่มใน working tree ตามการอนุญาตของผู้ใช้

| ชุด | ผลล่าสุด | หลักฐาน |
| --- | --- | --- |
| Patient registration persistence | ผ่าน | migration V1021 + entity/DTO/service/controller; clean-main runtime ตรวจ allergy, chronic disease, emergency contact และ privacy consent create/update/clear |
| Permission catalog/overrides | ผ่าน | Manager-only catalog ใช้ IDs จริง; override add/read/update/reset ผ่าน direct runtime API; frontend `BackOfficeView.vue` เลือก permission ID และ allow/deny |
| Immediate token revocation | ผ่าน | auth_version ใน JWT/database; direct runtime ปฏิเสธ token เก่าทันทีหลัง role change/deactivation และยังปฏิเสธหลัง reactivation |
| Frontend tests/build | ผ่าน | Vitest 35 files / 190 tests; `npm run build` ผ่าน type-check และ Vite |
| Backend compile/package | ผ่าน | `mvn clean package -DskipTests`; clean-main package + runtime ตรวจจริง 7 checks บน `clinic_b73_task5_20261031` |
| Cypress management rerun | ไม่นับเป็น pass | ฐานที่ใช้ซ้ำมี override residue จาก attempt ก่อน ทำให้ fixture reset ได้ 409; direct runtime API checks บน DB แยกใหม่ผ่าน |

หลักฐาน runtime: `state/task5-final-runtime-20261031/task5-clean-main-contract-report.json`. Clean source สร้างจาก `git archive b73f8cf762657ae95093a7d4025ee6e7b8d77bc8`; main มี V1010–V1020 จึงวาง support migrations เป็น V1021/V1022 เพื่อไม่ชน migration ใน main หรือแตะ V1011/V1012 ของผู้ใช้; clean package archive มี V1021/V1022 และไม่มี stale V1013/V1014; Flyway ตรวจ/ใช้ 28 migrations ถึง V1022 บนฐานสังเคราะห์; ไม่ใช้ข้อมูลผู้ป่วยจริง ไม่มี commit/push/deploy และหยุดหลัง Task 5 เพื่อรอผู้ใช้อนุมัติงานนอกแผน

## Final completion audit — 2026-10-31

ใช้ผลตรวจล่าสุดแทน blocker table เก่าใน checkpoint ก่อน backend support. Handoff acceptance **22/22** ข้อครบ; frontend ต่ออายุคอร์สใช้ main `/api/course-extensions` พร้อม Manager approval queue; API runtime course extension ผ่าน 3 checks และ Calendar side-effects ผ่าน 1 scenario บน PostgreSQL synthetic แยก.

Final support runtime report `state/task5-final-runtime-20261031/task5-clean-main-contract-report.json`: 9 checks ผ่าน รวม permission override, token revoke หลัง branch/role/deactivation changes และ patient extra-field persistence. Frontend full suite ผ่าน **37 files / 194 tests**; `npm run build` ผ่าน type-check/Vite. รายละเอียดและ limitations อยู่ใน plan `docs/superpowers/plans/2026-10-30-frontend-main-backend-alignment.md` และ source README handoff.

ผู้ใช้ staged backend migrations/files ที่ไม่อยู่ใน main ถูกเก็บไว้และไม่ overlay ใน clean-main runtime; integration ของ staged migrations ไม่อยู่ในผลตรวจนี้. ไม่มี commit/push/deploy.
