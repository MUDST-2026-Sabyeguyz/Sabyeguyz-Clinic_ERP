# P6–P9 Parallel Implementation and Team Handoff Plan

> **For agentic workers:** ใช้ superpowers:executing-plans ทำตาม checklist ทีละ deliverable; ใช้ subagents เฉพาะเมื่อผู้ใช้อนุญาต

**Goal:** ทำ P6/P7 และ P8/P9 พร้อมกันบนสอง branch โดยมีเจ้าของไฟล์ชัดเจน และตรวจ migration/release หลังรวม contract ที่อนุมัติแล้ว

**Architecture:** ฝั่ง A ดูแล identifiers/course lifecycle และ forward schema; ฝั่ง B ดูแล reports/integration ที่ยังขาด รวมถึง importer/CI/acceptance ฝั่ง B เริ่มเครื่องมือและ baseline ได้ทันที แต่ final P9 ต้องรับ P6/P7 ก่อน ไม่เปลี่ยน commission rules

**Tech Stack:** Java 17/Spring Boot/JPA/PostgreSQL/Flyway, Vue 3/TypeScript, Cypress API/browser, Vitest, GitHub Actions

**Spec:** `docs/superpowers/specs/2026-10-14-non-commission-scope.md`; รายละเอียดเดิม `docs/superpowers/plans/2026-10-14-non-commission-implementation.md` P6–P9 และ Q3/Q4/Q7/Q10/Q11/Q12/Q13

## 1. ฐานปัจจุบันและสถานะที่ตรวจจริง

- ตรวจวันที่ 14 ตุลาคม 2026: branch `feat/backend-v2-integration`, HEAD `b6473b807c344891e1cc188473194845e164a1a2`; working tree สะอาดก่อนเพิ่มเอกสารนี้
- สร้าง local branches สองชื่อด้านล่างจาก HEAD เดียวกันแล้ว; ไม่ switch checkout, ไม่ push และไม่สร้าง PR ในรอบวางแผนนี้
- HEAD มี merge งาน non-commission และ Google Calendar แล้ว จึงห้ามใช้ฐาน `feat/carlendar`/`7462949` ในแผนเก่าเป็นฐาน execution รอบนี้
- มี code Calendar ใน `clinicv2/calendar` และ test `backend/cypress/e2e-v2/calendar.cy.js` แล้ว; P8 ต้อง audit/reuse ก่อนเพิ่ม ไม่เริ่ม Calendar ใหม่จากศูนย์
- HN ปัจจุบัน `HnGeneratorService` ใช้ YYBBMMNNNN และ read-then-write counter; receipt ปัจจุบัน `ReceiptService` ใช้ REYYMMNNNN พร้อม allocation ต่อ branch/month ไม่ใช่ RE-7 digits ตามเอกสารบางตอน
- `docs/legacy-data-migration.md` มีข้อความขัดกันเรื่อง receipt format และการเก็บข้อมูลครบ: บาง field อยู่เฉพาะ legacy และ transfer บางชนิด map เป็น Adjustment ให้ B ตรวจเทียบ SQL จริงและแก้เอกสาร ไม่ถือว่าข้อมูลใช้งานผ่าน v2 ครบเพียงเพราะ legacy ยังอยู่
- migration ที่เห็นใน active folder คือ V1/V2/V3/V1001; ห้ามแก้ applied migrations และต้องอ่าน extra Flyway locations/runtime configuration ก่อนเลือกเลขใหม่
- รอบนี้ตรวจ source/Git/เอกสารเท่านั้น ยังไม่ได้รัน application tests ใหม่; จำนวน tests ในรายงานเดิมเป็นหลักฐานย้อนหลัง

## 2. แบ่งความรับผิดชอบ

| ฝั่ง | Branch | เจ้าของ | Deliverables |
|---|---|---|---|
| A | `codex/p6-p7-identifiers-courses` | เรา | P6 HN/receipt/sequence compatibility; P7 expiry/history/transfer และ attachment เมื่อผ่าน gate |
| B | `codex/p8-p9-reports-release` | เพื่อน | P8 dashboard และ audit Calendar/backlog; P9 importer, CI, migration dry run, acceptance/release evidence |

สองฝั่งทำได้พร้อมกัน แต่ dependency เป็นดังนี้:

```text
base b6473b8 ── A: P7 regression → approved P6/P7 → schema/contract freeze ─┐
             └ B: baseline → P8 audit/dashboard → P9 harness ───────────┤
                                                                        ↓
                         B merges A → importer alignment → final P9
                                                                        ↓
                         reviewed B → feat/backend-v2-integration
```

P9 harness/CI diagnosis เริ่มทันทีได้; P9 final dry run และ release sign-off รอ A และ P8 ที่รับเข้า release พร้อม ห้ามปิด P6–P9 ทั้งหมดถ้ายังมี gated scope

## 3. File ownership ลด conflict

| ไฟล์/พื้นที่ | เจ้าของ | กติกา |
|---|---|---|
| `backend/src/main/java/com/sabyeguyz/clinicv2/patient/Hn*`, `sale/ReceiptService.java`, `sale/ReceiptRepository.java` | A | B ใช้ผ่าน contract ไม่แก้ allocator เอง |
| `clinicv2/catalog/Service*.java`, `Course*.java`, `clinicv2/course/*` | A | expiry/transfer/history เท่านั้น; ไม่เปลี่ยน commission calculation/credit |
| `frontend/src/features/courses/*`, course UI ใน patient profile และ settings | A | B แจ้งหาก dashboard action ต้องเรียก API ใหม่ |
| `clinicv2/report/*`, dashboard/reports frontend, `reports.cy.js` | B | ไม่แก้ course lifecycle จาก report controller |
| `clinicv2/calendar/*`, `calendar.cy.js` | B | audit implementation ที่ merge แล้ว; appointment integration ที่ต้องแก้ให้แจ้ง A |
| `backend/scripts/legacy-migration/*`, `.github/workflows/*`, `docs/legacy-data-migration.md` | B | ย้าย importer ที่แผน P6 เดิมระบุให้ A มาอยู่ B; A ส่งกติกา mapping/counter ให้ B |
| `backend/src/main/resources/db/migration/*` และ extra migration folders ที่ runtime โหลด | A เป็นผู้ประสานเลข | B ขอ migration ผ่าน A; ห้ามสองฝั่งออก version ซ้ำหรือแก้ migration ที่ applied แล้ว |
| `frontend/src/api/client.ts`, `BackOfficeView.vue`, `docs/api-v2-frontend-contract.md` | shared | แจ้ง section/method ก่อนแก้; A แก้ course/identifier, B แก้ report/calendar; review diff ร่วมก่อน merge |
| `docs/testing/*` | แยกตามงาน | A ใช้รายงาน P6/P7, B ใช้รายงาน P8/P9; ไม่ overwrite รายงานอีกฝั่ง |

สำหรับ Rooms/Auth ที่ผ่าน Q12 แล้ว B เป็นเจ้าของ feature แต่ schema ยังคงประสาน A. งาน extra เหล่านี้ต้องแยก commit และ acceptance ไม่ขยาย scope เงียบ ๆ

## 4. Gates และข้อกำหนดร่วม

**Confirmed Requirement:** HN 9 หลักตาม R14 แต่สูตรยังไม่ชัด; receipt YYMM+run4 และไม่ reuse เลข void; expiry/extension/history; dashboard แยกยอดขายจากเงินรับ; commission/Tier/Pool/ค่ามืออยู่นอก scope

**Open Questions ที่ต้องตอบก่อนเปลี่ยน behavior:**

| Gate | ต้องยืนยัน | ผู้รวบรวม | งานที่รอ |
|---|---|---|---|
| Q3 | แบ่ง HN 9 หลักอย่างไร, ปี CE/BE, สาขา/เดือน/run, reset, capacity, legacy policy | A | new HN formula/schema |
| Q4 | receipt run รวม/แยกสาขา, RE เป็น display/persisted ID, uniqueness และ overflow | A | receipt numbering/import mapping |
| Q7 | expiry รวมวันสุดท้ายไหม, ใครแก้ได้, extension จากวันนี้/วันเดิม, ผลต่อสมาชิกที่รับโอน | A | expiry mutation/daysRemaining |
| Q13 | รูปชนิดใด ผูกกับ purchase/profile, MIME/size/count/storage/access/retention | A | attachments |
| Q11 | เก่า/ใหม่ตาม registration/first visit, sale/cash date, renewal/package เป็น metric หรือ action | B | dashboard semantics |
| Q12 | Calendar account/per-branch/conflict/delete/privacy; room state; reset delivery | B | integration behavior ที่ยังไม่ได้อนุมัติ |
| Q10 | allergy/consent/emergency mapping และข้อมูลเดิมที่ต้องใช้งานต่อ | A+B | active-schema completeness ใน P9 |

**Assumption สำหรับการจัดงาน:** B เป็นผู้ประกอบ integration และ final acceptance; ใช้ LocalDate/Clock กับ Asia/Bangkok ใน logic วันที่; fields/API เพิ่มแบบ backward compatible เท่าที่กติกาอนุมัติยอมให้ทำได้ ข้อเสนอนี้ไม่ใช่คำตอบแทน Q ข้างบน

- เก็บคำตอบและ literal examples ใน `docs/coordination/p6-p7-decisions.md` (A) และ `docs/coordination/p8-p9-decisions.md` (B) ก่อนลง implementation ที่พึ่งคำตอบ
- ไม่เปลี่ยน IDs เดิม/rename receipt ย้อนหลังอัตโนมัติ; ถ้าต้องแปลงจริงให้ทำ mapping และแผน rollback ที่ review แล้ว
- test DB แยกจากข้อมูลจริงและแยกสองคน; ห้าม fixture reset บนฐาน shared/production
- preserve regression payment/receipt/print/commission เดิม; UI/API compile สำเร็จไม่เท่ากับ persistence หรือ runtime acceptance

## 5. เตรียม branch และพื้นที่ทำงาน

Local branches สร้างแล้วจาก hash ที่ระบุ เอกสารนี้ยังเป็น untracked file บน checkout ปัจจุบัน ให้ส่งไฟล์ให้เพื่อนโดยตรงก่อน หรือ commit/push เอกสารเมื่อพร้อมแชร์; Git branch ยังไม่ได้มีไฟล์นี้ใน tree จนกว่าจะ commit

ขั้นตอนเรา:

```powershell
git status --short
git switch codex/p6-p7-identifiers-courses
git rev-parse HEAD
```

ถ้ามีงานค้างภายหลัง ให้ commit เฉพาะงานที่พร้อมหรือเก็บอย่างชัดเจนก่อน switch; ห้าม reset/clean เพื่อให้ checkout ผ่าน

ขั้นตอนเผยแพร่ branch หลังพร้อมแชร์ (ยังไม่ได้รัน):

```powershell
git push -u origin codex/p6-p7-identifiers-courses
git push -u origin codex/p8-p9-reports-release
```

เพื่อนใช้ clone ของตัวเอง แล้ว:

```powershell
git fetch origin
git switch --track origin/codex/p8-p9-reports-release
git merge-base --is-ancestor b6473b807c344891e1cc188473194845e164a1a2 HEAD
git rev-parse HEAD
git status --short
```

คำสั่ง switch --track ใช้ครั้งแรกที่ยังไม่มี local branch เท่านั้น ถ้ามีแล้วใช้ `git switch codex/p8-p9-reports-release` และตรวจ divergence ก่อน pull. หาก branch ยังไม่ถูก push ให้รอ remote branch หรือสร้างจาก exact base ที่ fetch มาได้ด้วย `git switch -c codex/p8-p9-reports-release b6473b807c344891e1cc188473194845e164a1a2`

ใช้เครื่อง/clone แยกกันจะไม่มี working files ปะปน ถ้าทำบนเครื่องเดียวให้ใช้ worktree แยกและ process/DB แยก; runner `backend/scripts/run-v2-tests.cjs` ใช้ ports 18080/19001 คงที่ จึงห้ามรันสอง suite พร้อมกันบน host เดียวจนกว่าจะจัด ports ใหม่ครบ config

## 6. Baseline ทั้งสองฝั่งก่อน implementation

- [ ] อ่าน spec, P6–P9 เดิม, API contract, รายงาน P1–P5 และ Calendar source/test ที่ HEAD ของตน
- [ ] บันทึก hash, status, Java/Node/PostgreSQL versions และ DB test identifier ในรายงานของฝั่งตน ห้ามเก็บ passwords/tokens
- [ ] ตั้ง `DATABASE_URL` แบบ JDBC, `DATABASE_USERNAME`, `DATABASE_PASSWORD` ผ่าน local environment ไป disposable PostgreSQL ของตน ตรวจชื่อ host/database ก่อน test
- [ ] ติดตั้ง dependencies ตาม lockfile ด้วย `npm ci` ใน backend/frontend ถ้ายังไม่มี และรัน baseline:

```powershell
# cwd: backend
npm run test:v2
mvn --batch-mode --no-transfer-progress clean package
# cwd: frontend
npm run test:unit -- --run
npm run build
```

- [ ] browser baseline: backend v2 test profile ทำงานบน test DB, frontend ที่ localhost:5173 ตาม CORS; ใช้ setup ตาม `.github/workflows/frontend-e2e-v2.yml` / `docs/frontend-e2e-tests.md` แล้วรันใน frontend:

```powershell
npx cypress run --config-file cypress.v2.config.ts
```

- [ ] แยก baseline failures ออกจาก regression ใหม่; Maven download/sandbox/port failures ต้องวินิจฉัยจาก log ห้ามเปลี่ยน assertion หรือ retry ให้ดูผ่าน

## 7. ฝั่ง A: P6/P7 ขั้นตอนปฏิบัติ

### A1 — P7 regression ที่ไม่ติด gate

**Files:** `clinicv2/course/CoursePurchaseController.java`, `CourseTransferService.java`, `CourseUsagePrintService.java`; `backend/cypress/e2e-v2/course-transfers.cy.js`, `courses.cy.js`, `receipt-history.cy.js`

- [ ] สร้าง fixture owner A ซื้อคอร์ส → โอนสิทธิ์ให้ patient B → GET ของทั้งสอง; expected Course ID และ expiry เดียวกัน, ยอด entitlement รวมคงเดิม, history แสดง transfer จริง
- [ ] ตรวจใบตัดคอร์สมี HN/owner/ชื่อนักกายภาพจากข้อมูลที่ persist และ usage/transfer history ครบ; อย่าเปลี่ยนผู้รักษาหรือเครดิต
- [ ] ถ้าพบ gap ให้เก็บ RED ที่พฤติกรรมจริง แล้วแก้และอ่านกลับจาก API/DB; รัน scoped test และ commit deliverable แยก

### A2 — P6 numbering หลัง Q3/Q4

**Modify:** `clinicv2/patient/HnGeneratorService.java`, `HnSequence.java`, `HnSequenceId.java`, `HnSequenceRepository.java`, `clinicv2/sale/ReceiptService.java`, `ReceiptRepository.java`; forward migration ตามเลขที่ตรวจว่าง
**Create test:** `backend/cypress/e2e-v2/identity-sequences.cy.js`

- [ ] ใส่ตัวอย่างเลขที่อนุมัติแบบ literal ใน tests ก่อน: year/month/branch01/02, month rollover, limit/overflow, existing IDs, void→next number ไม่ reuse
- [ ] ใช้ pattern `backend/cypress/tasks/parallel-requests.cjs` ทดสอบ first counter creation และ existing counter พร้อมกัน; expected unique IDs, success count เท่ากับ persisted rows, ไม่เกิด duplicate/500 จาก race
- [ ] รัน RED ใน backend: `npm run test:v2 -- --spec cypress/e2e-v2/identity-sequences.cy.js`
- [ ] ใช้ atomic allocation/upsert/locking ที่ทำงานจริงใน PostgreSQL; ตรวจ receipt counter มี atomic แล้วก่อนแก้ ไม่แทนด้วย max+1
- [ ] uniqueness ต้องสอดคล้อง Q4; ตรวจ key ของ DB/repository/API lookup และไม่ assume receiptNo เดี่ยว unique ข้ามสาขา
- [ ] เพิ่ม forward constraints รองรับ new/legacy ตามคำตอบ; ไม่แก้ V1–V3/V1001 ย้อนหลัง; API/print/validation/docs ใช้ contract เดียวกัน
- [ ] ทดสอบ empty DB และ existing-ID DB แล้วส่ง B: approved examples, sequence key/reset/overflow, legacy read policy, migration version และ affected columns
- [ ] GREEN scoped + patients/sales/receipt-history/frontend-contract; commit `feat: align identifiers with approved numbering rules`

### A3 — P7 lifecycle หลัง Q7 (attachment แยกหลัง Q13)

**Modify:** `clinicv2/catalog/Service.java`, `ServiceCatalogService.java`, `ServiceManagementController.java`, `CourseTemplateController.java`, `Course.java`; `clinicv2/course/CoursePurchaseController.java`, `CourseTransferController.java`; `frontend/src/features/courses/views/CourseActionView.vue`, settings/course API sections
**Create:** `clinicv2/course/CourseExpiryService.java`, `backend/cypress/e2e-v2/course-expiry.cy.js`

- [ ] อนุมัติ contract ก่อน implement: proposed `PATCH /api/course-purchases/{id}/expiry` body `{ "expiryDate": "YYYY-MM-DD" }`; response/audit/error/role ตาม Q7; `daysRemaining` ระบุสูตรและ boundary ชัดเจน
- [ ] RED matrix: authorized mutation/readback, unauthorized403, malformed400, missing404, yesterday/today/tomorrow ที่ Bangkok, old/new audit+actor, shared member expiry propagation ตามคำตอบ
- [ ] positive validity days สำหรับ template ใหม่; เดือน/ปีของคอร์สเก่าคงความหมายเดิม ไม่ convert เงียบ; expiry extension ไม่สร้าง sale/payment โดยอนุมาน
- [ ] ทำ UI ต่ออายุ/แก้วันหมดอายุ และค้น recipient จาก patient API; ไม่เพิ่ม therapist selector ใน transfer
- [ ] หลัง Q13 เท่านั้นทำ attachment เป็น deliverable แยก: proposed multipart POST/authorized GET ผูก purchase; MIME/size/count limits, wrong-course access, readback, retention/deletion tests; ไม่ฝังภาพใน print snapshot
- [ ] GREEN expiry/transfer/courses/history API + course browser + frontend build; ส่ง schema/API/date-policy/attachment metadata ให้ B; commit แต่ละ deliverable
- [ ] ออก report P6/P7 พร้อม test hash/results/gated remainder; freeze contract ที่จะใช้ P9

## 8. ฝั่ง B: Context และขั้นตอนสำหรับเพื่อน

คุณรับผิดชอบ `codex/p8-p9-reports-release` จาก base b6473b8. อีกฝั่งทำ P6/P7; คุณไม่ต้อง implement numbering/expiry ซ้ำ และไม่ต้องรอทั้งฝั่งก่อนเริ่ม baseline, P8 audit, migration harness และ CI diagnosis

### B1 — Audit P8 ปัจจุบันและคำตอบ Q11/Q12

**Read:** `clinicv2/report/ReportController.java`, `RevenueSplit.java`, `clinicv2/calendar/*`, `backend/cypress/e2e-v2/reports.cy.js`, `calendar.cy.js`, `frontend/src/features/dashboard/views/DashboardView.vue`, `docs/Reports-API-Handoff.md`

- [ ] บันทึก endpoint/response/branch enforcement ที่มีจริง พร้อมรายการต่างจาก R23/R43; Calendar code/test มีแล้วแต่ยังต้องแยก mock/provider fixture จากการเชื่อมบัญชี Google จริง
- [ ] ขอคำตอบ Q11 ก่อนนิยาม metrics; report branch filtering ต้องตรวจ actor จริง ไม่ถือว่า filter จาก client คือ authorization; report-wide permission matrix ส่วน Q9 ยังต้องยึดขอบเขตที่อนุมัติ
- [ ] ทำ checklist Q12 เทียบ Calendar implementation: per-branch mapping, credentials model ที่มี (service-account support), outbound/inbound, conflict/delete, retries, duplicate/echo-loop, privacy และ disconnect
- [ ] สิ่งที่ code มีแต่ไม่ผ่าน approved policy ให้ระบุ gap และทำ scoped fix; ไม่รื้อไป OAuth หรือสร้าง integration ใหม่เพียงเพราะแผนเก่าบอกว่ายังไม่มี package

### B2 — Dashboard deliverable หลัง Q11

**Modify:** `clinicv2/report/ReportController.java`, `RevenueSplit.java`, `frontend/src/features/dashboard/views/DashboardView.vue`, report sections ของ `frontend/src/api/client.ts`, `backend/cypress/e2e-v2/reports.cy.js`
**Create if needed:** `clinicv2/report/DashboardService.java`, dashboard browser spec ใน `frontend/cypress/e2e-v2/`

- [ ] Fixtures: sale วันนี้จ่ายพรุ่งนี้, sale เมื่อวานจ่ายวันนี้, void/refund, zero-day, branches01/02, registration ต่างจาก first visit; expected sales/cash/customer classification เป็น literal amounts/counts ตาม Q11
- [ ] proposed additive contract `salesToday`, `paymentsToday`, `newCustomers`, `returningCustomers`, `dailySales`; ตกลงกับ frontend ก่อน เปลี่ยนชื่อได้ตาม contract เดิมที่ตรวจจริง
- [ ] RED scoped: `npm run test:v2 -- --spec cypress/e2e-v2/reports.cy.js` ใน backend; เพิ่ม boundary ของช่วงวัน Bangkok และ permission cases ตาม approved scope
- [ ] Implement จาก persisted receipt/sale/payment; refund/void treatment ต้องกำหนดชัดตาม metric; ไม่แปลง payment revenue ให้เป็น sales ด้วย label อย่างเดียว
- [ ] renewal/package action เรียก API ของ A เมื่อพร้อม; ถ้าเป็น metric ต้องตกลง source/audit events กับ A; ไม่เขียน lifecycle rule ซ้ำใน report
- [ ] GREEN reports/full report regression + browser dashboard + frontend type-check/build; commit `feat: clarify dashboard sales and cash metrics`

### B3 — Calendar/backlog เป็น release ย่อย

- [ ] ต่อจาก audit B1: เขียน RED เฉพาะ approved gaps เช่น create/update/cancel ทั้งสองทาง, duplicate provider event, conflict, retry, other-branch event, credentials failure และ redacted patient data
- [ ] ใช้ provider fixture และทดสอบ permission ของ `/api/calendar/*`; แยกผล fixture test กับ live integration ที่มีบัญชีอนุญาตจริง
- [ ] Rooms/Auth reset ทำเฉพาะหลัง Q12 ระบุ delivery/room state แล้ว; แยก spec/test/commit ของแต่ละอย่าง ไม่เพิ่ม email field/schema โดยไม่มี mapping และ migration ที่ประสาน A
- [ ] หาก Q12 ยังไม่ตอบ ส่ง audit+regression ที่มีได้ และบันทึก gated tasks; ไม่เรียก P8 ทั้งหมด complete

### B4 — P9 harness/CI ที่เริ่มได้ทันที

**Own:** `backend/scripts/legacy-migration/migrate-legacy-to-v2.sql`, `ci/legacy-fixture.sql`, `ci/verify-migrated-app.sh`, `docs/legacy-data-migration.md`, `.github/workflows/backend-ci.yml`, `v2-api-ci.yml`, `schema-ci.yml`, `legacy-migration-ci.yml`, `frontend-e2e-v2.yml`

- [ ] ใช้ fixture copy ที่อนุญาตเท่านั้น; สร้าง before/after manifest ของ patient/receipt/course/payment, unique IDs ตาม scope, FK orphans, amounts, entitlement/ledger, expiry และ field-by-field mapping
- [ ] เพิ่ม fixture old HN/receipt/void/refund/transfer และ missing legacy fields; แยก preserved-in-legacy กับ accessible-in-v2 โดยชัดเจน
- [ ] ตรวจ SQL จริงก่อนเชื่อ docs: mapping receipt เดิม, HN fallback, treatment reversal/commission migration และ transfer Adjustment เป็นจุดต้องรายงาน ไม่แก้ commission rule ในรอบนี้
- [ ] ตรวจ workflow legacy migration ที่ enumerate V1/V2/V3/V1001 แบบ explicit: หลัง A เพิ่ม migration ต้องปรับให้สร้าง final schema ครบก่อน importer และ Flyway history/runtime สอดคล้องกัน
- [ ] ตรวจ Maven failures จาก log จริงถ้าเข้าถึงได้; ถ้าไม่มี log ห้ามเดาสาเหตุ; pin/cache/retry เฉพาะ download failure ที่พิสูจน์ได้
- [ ] รัน harness กับ base เป็น baseline; เก็บ failures จริง แล้ว commit fixture/check tooling โดยยังไม่อ้าง final P9 ผ่าน

### B5 — รับ A แล้วตรวจ final P9

- [ ] A ส่ง reviewed commit hash/contract freeze/migration list/test report และ push branch แล้ว B มี working tree สะอาดก่อน merge:

```powershell
git switch codex/p8-p9-reports-release
git fetch origin
git log --oneline HEAD..origin/codex/p6-p7-identifiers-courses
git merge --no-ff origin/codex/p6-p7-identifiers-courses
```

- [ ] Review shared files ด้วย ownership matrix; ห้าม resolve โดยเลือก ours/theirs ทั้งไฟล์; migrations ห้าม version ชนกัน และห้าม renumber file ที่ถูก apply ใน DB shared แล้ว
- [ ] ปรับ importer ให้ตรง approved numbering/expiry schema; ให้ A review mappings; ไม่ใช้สูตรจาก comment/document เก่าแทน approved contract
- [ ] Dry run บน empty target + permitted legacy snapshot; compare before/after counts, amounts, IDs, FK, history, expiry, active fields และ migration_issue; ทุก mismatch มีเหตุผลและ acceptance
- [ ] Repeat จาก snapshot เดิมลง target ใหม่อีกครั้งแล้วเทียบ manifests; importer ที่รับเฉพาะ empty target ไม่ต้องถูกบังคับให้ rerun บน populated target แต่ต้องปฏิเสธโดยไม่ทำข้อมูลเสีย
- [ ] Verify migrate→boot v2→read old records→register/sell/use/transfer/extend ใหม่; new sequences ไม่ชน old IDs; void ไม่ reuse; course purchase/payment อยู่ receipt เดียวจริงหาก claim flow นั้น
- [ ] รัน full v2 API รวม commission, frontend unit/build, browser end-to-end; รัน RuntimeCheck/SchemaCheck ตามขั้นตอนใน `docs/testing/2026-10-13-Phase-2-V2-Acceptance.md` กับ final test DB
- [ ] บันทึก `docs/testing/2026-10-14-P8-P9-Acceptance.md`: tested hash, environment, commands/exit/results, reconciliation tables, gated remainder, fixture/live distinction และ backup/rollback rehearsal
- [ ] `git diff --check`, `git status --short`; review focused files ก่อน commit/PR; ไม่ deploy หรือย้าย production DB จากคำขอวางแผนนี้

## 9. จังหวะ sync และสิ่งที่ต้องส่งต่อ

1. ก่อนเริ่ม: ส่ง branch/base hash, baseline failures, gate answers และ section ของ shared files ที่จะเปลี่ยน
2. หลัง approved contract: A ส่ง literal identifier examples/uniqueness/reset, expiry boundary/role/audit, migration versions; B ส่ง metric definitions/date filters และ importer mismatch list
3. ระหว่างงาน: sync เมื่อ schema/API เปลี่ยนทันที พร้อม reviewed commit hash; commit แยก P6/P7/P8/P9 เพื่อ review/rollback ได้
4. ก่อน final P9: freeze schema/API ทั้งสองฝั่ง, B merge A แล้วรัน acceptance ใหม่; หาก A เปลี่ยนหลัง freeze ให้ merge และทวน checks ที่ได้รับผลกระทบพร้อม full final suite ตาม release gate
5. ก่อนรวม parent: B ส่ง reviewed combined hash และ evidence ให้ A review; parent merge หลังผ่าน acceptance; ถ้า parent มี commit ใหม่ ให้รับเข้า B และตรวจผลกระทบก่อน merge

ไม่ cherry-pick A แล้ว merge A ซ้ำโดยไม่มีเหตุผล เพราะจะทำประวัติ review ซับซ้อน; แผนนี้ใช้ merge A เข้า B ครั้งเป็น checkpoint และ merge B ที่มี A แล้วกลับ parent ไม่จำเป็นต้อง merge A เข้ parent แยกอีกครั้ง

## 10. ข้อความพร้อมส่งให้เพื่อน

> รับผิดชอบ P8/P9 บน `codex/p8-p9-reports-release` จาก base `b6473b807c344891e1cc188473194845e164a1a2` ของ `feat/backend-v2-integration` ครับ/ค่ะ เราทำ P6/P7 บน `codex/p6-p7-identifiers-courses` อ่านเอกสารนี้กับ non-commission scope/plan และ API contract ก่อน เริ่ม baseline → audit reports/Calendar → dashboard หลัง Q11 → P9 fixture/reconciliation/CI ได้ทันที Calendar มี code ที่ merge แล้วจึงตรวจและต่อยอด ห้ามสร้างซ้ำ คุณเป็นเจ้าของ importer/CI เราประสาน schema migrations และ numbering/expiry contract; shared client.ts/BackOffice/API docs ให้แจ้ง section ก่อนแก้ Q3/Q4/Q7/Q13 ของเรากับ Q11/Q12 ของคุณยังต้องยืนยันก่อน behavior ที่เกี่ยวข้อง เมื่อเราส่ง reviewed hash/contract freeze ให้ merge branch เราเข้า branch คุณแล้วทำ final migration dry run/full regression รวม commission และ browser; ส่งผลพร้อม tested hash/ข้อจำกัดก่อนเสนอ merge กลับ parent ไม่ deploy หรือ migrate ข้อมูลจริง และไม่อ้าง P6–P9 ครบถ้ายังมี gated requirements

## 11. Definition of done

- [ ] P6/P7 และ P8 ที่รับเข้า release มี approved rules, scoped RED→GREEN, persisted readback, UI evidence เมื่อแตะ frontend และ regression เดิมผ่าน
- [ ] final B HEAD มี reviewed A commits และ final schema; importer/CI ใช้ schema เดียวกัน
- [ ] P9 reconciliation/repeatability/runtime/schema/browser และ commission regression มีผลจาก hash ปัจจุบันจริง
- [ ] API/docs/source-to-task mapping ตรงกัน; unresolved Q/gated features และข้อมูล legacy ที่ v2 ยังใช้ไม่ได้ถูกระบุครบ
- [ ] มี concrete backup/rollback rehearsal และ review ก่อน release; deployment/production migration ต้องเป็นขั้นตอนที่ได้รับอนุญาตต่างหาก
