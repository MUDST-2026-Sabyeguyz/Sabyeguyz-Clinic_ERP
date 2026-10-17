# Phase 1: รวม develop กับ dev-backed

วันที่ตรวจ: 12 ตุลาคม 2026 (Asia/Bangkok)

## ผลลัพธ์

รวม `dev-backed` เข้า branch งานที่สร้างจาก `develop` เดิมชื่อ `codex/backend-v2-integration` และปัจจุบันชื่อ `feat/backend-v2-integration` แก้ frontend conflicts ทั้ง 8 ไฟล์ พร้อมปรับจุดเชื่อมที่ compile/type-check เปิดเผย ระบบหลักยังใช้ legacy entry point และ migration เดิมตามขอบเขตช่วง 1 ส่วน v2/schema/tests จาก dev-backed อยู่ใน branch รวมแล้ว

| Parent | Commit หลัง fetch |
|---|---|
| develop / origin/develop | c62d68b07aae805d9b79492bbd6be8c5eee1cdab |
| dev-backed / origin/dev-backed | 4b63f31e86c2d976b066a34ab6743284c3cce7e5 |

ดูผลก่อนรวมใน [Pre-merge baseline](2026-10-12-Pre-Merge-Baseline.md) รายงาน baseline จากช่วง 0 ถูกเก็บเข้าประวัติพร้อมงานนี้ ไม่แก้เนื้อหาเดิม

Merge commit คือ `62172abeaa42abea7e931d7337f820423b683767` มี parents ตามตารางข้างต้น เก็บ compatibility fixes, regression tests และรายงานที่เหลือใน commit ต่อท้าย โดยไม่แก้ประวัติ merge commit เดิม

## การตัดสินใจแก้ conflicts

| ไฟล์ | แนวทางที่ใช้ |
|---|---|
| api/client.ts | คง typed patient update, searchAll, auth branch fields และ commission closing จาก develop; ใช้ Branch/BranchRequest `{ branchCode, name, address, phone }` ตาม backend รวม และล้าง mock permissions เมื่อ logout |
| AppointmentView.vue | คง UI/filter/calendar/form ของ develop; เปลี่ยน branch selectors และ default branch เป็น branchCode |
| DashboardView.vue | คง layout/charts/filter dropdown ของ develop; เปลี่ยน contract branch lookup |
| PatientRegistrationView.vue | คง form และ validation; โหลดและส่ง branchCode จากรายการสาขาจริง |
| PatientSearchView.vue | คง UI/sort/pagination; branch filter ส่ง branchCode ที่ backend คืนมา |
| ReportsView.vue | คง UI/filters/export; ใช้ branchCode และแสดง lookup error แทน fallback สาขา/คอร์สจำลองที่ไม่มี contract ถูกต้อง |
| BackOfficeView.vue | คง table/drawer/phone validation ของ develop; นำ API load/save/update/toggle จาก dev-backed มาปรับเข้ากับ UI ใหม่ |
| TransactionView.vue | คง list/detail/history-detail/cancellation steps/focus/Escape ของ develop; นำ API list/detail/reversal จาก dev-backed มาแทน seed และ repository ในหน่วยความจำ |

จุดเชื่อมเพิ่มเติม:

- `AuthController` ใช้ `Branch.getBranchCode()` แทน getter เดิมที่ไม่เหลือใน model หลัง merge; คง response branchCode/branchName สำหรับ StaffHeader
- ใบเสร็จใน services/course/patient history ใช้ branchCode เพื่อค้นที่อยู่และโทรศัพท์สาขา; backend ไม่คืน branch email/license/taxId จึงคงตำแหน่งบนเอกสารเป็น `—`
- Back Office เพิ่ม fields ที่ API เดิมต้องใช้ เช่น address, username/password/employeeCode, service duration, course bonus/validity; branchCode สองหลักแก้ไม่ได้เมื่อ update; loading/save error ไม่แสดงความสำเร็จหรือเปลี่ยนแถวก่อน API สำเร็จ
- Transaction โหลด receipt ทุกหน้า, course usage และสาขาจริง; โหลด sale detail เมื่อเปิดดู และรอ reverse API ก่อนแสดง success เก็บสถานะเดิมเมื่อ API ปฏิเสธ ลบเลข/เบอร์/จำนวนคอร์สจำลองออก
- API จาก dev-backed ใช้ Cypress แทน Java unit tests เดิม จึงนำ test migration และ workflow ของ branch นั้นมาด้วย ไม่ตีความ Maven package ที่ไม่มี unit tests ว่าได้รัน 221 tests เดิม

## ผลการตรวจสดหลังรวม

Windows 11, Java Corretto 21.0.12, Maven 3.9.16, Node 24.18.0, Cypress 16.1.0 / Electron 146, PostgreSQL 16.15

| Check | ผล |
|---|---|
| Backend `mvn -o --batch-mode --no-transfer-progress clean package` | ผ่าน: compile main 333 files และ test 22 files, package สำเร็จ |
| Backend `npm run test:api` | **396 tests / 17 specs ผ่าน**, failures/pending/skipped = 0; baseline 395 + test Login สาขาของ staff 1 ข้อ |
| Frontend `npm run test:unit -- --run` | **28 tests / 5 files ผ่าน**; baseline 3 + regression 25 |
| Frontend `npm run build` | ผ่านทั้ง vue-tsc type-check และ Vite production build |
| PostgreSQL legacy startup | Flyway validated/applied 27 migrations ถึง v26; packaged ClinicErpApplication เริ่มสำเร็จ |
| Legacy API smoke บน PostgreSQL | Manager login คืน token และ branchCode `01`; GET 11 endpoints คืน 200 |
| Frontend `npm run test:e2e -- --browser electron` | **Login 2 tests ผ่าน** ผ่าน Vite proxy ไป legacy backend บน PostgreSQL จริง |
| PostgreSQL v2 SchemaCheck | **SCHEMA_CHECK_OK**: 481 columns, 0 problems, 45 entities / 20 controllers / 30 services wiring ผ่าน |
| Code review | ตรวจ changes เทียบทั้งสอง parent แบบ static; ไม่พบ actionable integration regressions ที่เหลือ |
| Conflict / whitespace check | ไม่มี conflict markers หรือ unmerged entries; `git diff --check` และ cached check ผ่านก่อน commit |

Legacy GET endpoints: branches, users, services, courses, payment-methods, resources, master-data, commission/schemes, payments list, course-usages และ patients ที่กรอง branchCode

Regression tests ใช้จริงทั้ง component และ API client โดยจำลองเฉพาะ fetch boundary: branch filter 1, report lookup failure 1, Back Office 16 และ Transaction 7 ข้อ สังเกต RED ก่อนแก้และ GREEN หลังแก้ ครอบคลุม branch create/update/immutable code, backend errors, role display, payment/user toggles, pagination, rejected/pending reversals และ keyboard focus

## ขอบเขตที่ยังไม่เปิด

- `ClinicErpApplication` ยัง scan `com.sabyeguyz.clinic`; Flyway ยังใช้ `db/migration` และ vendor; ไม่เปิด scan/entry point/migration v2 ในแอปหลัก
- Backend Cypress ใช้ H2 PostgreSQL mode; v2 scenario บางส่วนใช้ repository stubs; SchemaCheck ตรวจ schema/wiring ไม่ใช่ workflow v2 บน PostgreSQL
- Login E2E สองกรณีไม่ใช่ browser verification ทุกหน้า ส่วน Back Office/Transaction integration ใหม่มี component regression tests และ API smoke ตามที่ระบุ
- Legacy user API ยังรับ role ไม่รับสิทธิ์รายรายการ: checkbox แสดงสิทธิ์ตามบทบาทแบบ read-only ไม่รับ selection ที่จะสูญหายเมื่อ reload
- Commission settings ใน Back Office แสดง scheme/tiers แบบ read-only ตาม API ที่หน้านี้รองรับ; งานเพิ่มสิทธิ์/กฎใหม่อยู่ช่วงถัดไป
- Transaction internal note ปิดการกรอกเพราะ reverse API รับเพียง reason; ไม่มีเลขอ้างอิง void จาก API จึงแสดง `—`; usage reversal ไม่มีเวลาใน response จึงแสดงเวลา acknowledgement หลัง API สำเร็จตาม flow เดิมของ dev-backed
- ไม่แก้ requirements/cutover/ย้ายข้อมูลจริง และไม่ push หรือรวมกลับ develop ในช่วงนี้

## หลักฐานและ cleanup

Logs, exit codes และ smoke runner อยู่ใน [integration artifacts](C:/Users/napal/.codex/visualizations/2026/10/12/01a130ca-73c9-7843-a985-8fc559a74e3b/integration/backend-api.log): `backend-package.*`, `backend-api.*`, `frontend-unit.*`, `frontend-build.*`, `frontend-e2e.*`, `legacy-api-smoke.log`, `legacy-backend.log`, `schema-check.*`, RED/GREEN logs และ `run-legacy-smoke.ps1`

ทดสอบฐานข้อมูลแยก `integration_v2` / `integration_legacy` ใน container `clinic-integration-01a130ca` บน loopback 15432 พร้อม tmpfs ตรวจ label ก่อนหยุดและลบ container สำเร็จ ไม่เหลือ listeners ทดสอบ 15432/18080/18081/19001/5173 และหยุด server processes ที่สร้างเองแล้ว เก็บ Docker Desktop เปิดไว้

ช่วง 1 เสร็จแล้ว พร้อมใช้ branch นี้เป็นฐานสำหรับช่วง 2: เปิด entry point/profile v2 และทดสอบ workflow บนฐานข้อมูลแยก
