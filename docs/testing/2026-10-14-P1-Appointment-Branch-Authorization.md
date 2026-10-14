# P1: Appointment branch authorization

วันที่: 14 ตุลาคม 2026 (Asia/Bangkok)

ฐานงาน: `codex/non-commission-backend`, parent `b4fa9ff` (P0)

สถานะ: ผ่าน acceptance ของ P1 appointment subset ตามที่อนุมัติ พร้อม commit; ส่วน Q9 และสิทธิ์ module อื่นยังเปิดอยู่

## ขอบเขตที่อนุมัติ

Confirmed Requirement: นายท่านให้ทำ P1 ต่อ และเลือก “ทำสิทธิ์นัดหมายก่อน พักส่วน Q9 ตามแผน” จึงเป็น release เฉพาะ appointment authorization ไม่ใช่การปิด P1 ทั้งหมด

อ้างอิง R1–R3 / หน้า 1 ใน source mapping `docs/superpowers/specs/2026-10-14-non-commission-scope.md` และ P1 ใน implementation plan: ข้อมูลนัดแยกตามสาขา, Manager นัดข้ามสาขาได้, ผู้ป่วยค้นรวมได้

Assumption/session contract ตาม Q9 ยังพัก: ไม่เพิ่ม login selector, ไม่สร้าง STAFF/ADMIN roles, ไม่เปลี่ยน JWT, granular permission, report/sale/course/catalog หรือ commission algorithms ระบบจริงมี Manager/Therapist จึงใช้ Therapist เป็น non-Manager test actor ทั้งสองสาขา

## สิ่งที่เปลี่ยนและเหตุผล

- เพิ่ม `auth/BranchAccessPolicy.java` อ่าน active AppUser, current DB roles และ staff home branch; ไม่ใช้ role claims เก่าหรือ legacy multi-branch grants เพื่อตัดสินสิทธิ์ข้ามสาขาของนัด
- `AppointmentController` ใช้ policy ก่อน query เพื่อให้ items และ pagination counts จำกัดสาขาเหมือนกัน; ก่อนอ่านหรือเปลี่ยนนัดตรวจสาขาของ resource ที่บันทึกไว้ก่อนประมวลผล action/payload
- create ทั้ง CURRENT/NEW ต้องเลือก therapist ในสาขาเดียวกับนัด; ผู้ใช้ส่ง branchCode ต่างสาขาประจำไม่ได้ แม้เปลี่ยน patientId/therapistId
- คง global patient directory; ผู้ป่วยที่ลงทะเบียนสาขา 02 ยังจองในสาขา 01 โดยพนักงานสาขา 01 ได้
- ไม่เปลี่ยน response shape, appointment status transitions, reschedule therapist behavior/Q8, business services, commission code หรือ migration
- tests เดิมใน `appointments.cy.js` สองจุดเคยจับ therapist2 (home 02) คู่กับ branch 01 จึงเปลี่ยน branch เป็น 02; assertions เรื่องคนละ therapist จองเวลาเดียวกันและ Manager list/filter ยังคงเดิม ไม่ลดจำนวนหรือข้าม tests

## TDD และผลรันจริง

รันบน PostgreSQL 16.15 ใน container `alice-noncommission-p1-20261014`, owner label `codex.task=noncommission-p1-20261014`, bind `127.0.0.1:55434`, database `noncommission_p1` สร้างใหม่เฉพาะงาน ไม่ reset ฐานจริง; password สุ่มอยู่ใน environment ของ process ไม่ลง Git

ใช้ Node 24.18.0, Maven 3.9.16, Java 21.0.12 (compile release 17), Cypress 16.1.0 / headless Electron 146 ตามเครื่องมือปัจจุบัน

| Check | ผลจริง |
|---|---|
| RED: `npm run test:v2 -- --spec cypress/e2e-v2/branch-access.cy.js` ก่อนแก้ production | 18 tests, ผ่าน 2 / ล้ม 16, exit 1; รายการ non-Manager ได้ totalItems 2 แทน 1, cross-branch read/mutations ได้ 200 และ create ได้ 201 แทน 403; validation บาง action ได้ 400 ก่อนเช็กสาขา |
| GREEN: scoped command เดิมหลังเพิ่ม policy | **18/18**, failed/pending/skipped 0, exit 0 |
| Full backend: `npm run test:v2` หลัง cleanup constructor | **161/161 จาก 19 specs**, failed/pending/skipped 0, exit 0; หยุด server สำเร็จ 14:31:22 +07:00; ตรวจจำนวนซ้ำจาก JUnit XML |
| Commission ใน full run เดียวกัน | **12/12 commission + 5/5 commission-override**; admin/catalog/refund/course/transfer/report/sale/frontend-contract specs ผ่านด้วย |
| `mvn --batch-mode --no-transfer-progress clean package` หลัง API runner หยุด | BUILD SUCCESS, exit 0, compile main 344 files/test 24 files, จบ 14:31:43 +07:00; ไม่อ้างจำนวน JUnit tests จาก package |
| Frontend `npm run test:unit -- --run` | **28/28 จาก 5 files**, exit 0 |
| Frontend `npm run build` | vue-tsc และ Vite production build ผ่าน, exit 0 |
| Browser: นัดหมายเดิม + branch isolation ใหม่ | **16/16**, exit 0: appointment-branch-access 3/3 (Therapist01, Therapist02, Manager) และ appointments เดิม 13/13; backend และ Vite ของงานนี้หยุดสำเร็จ 14:33:31 +07:00 |

tests ใหม่ครอบ non-Manager ทั้งสองสาขา, implicit/explicit filters, counts/paging, create CURRENT/NEW, read by ID, confirm/link-patient/check-in/start/complete/no-show/cancel/reschedule, snapshot อ่านกลับหลังปฏิเสธ, global patient search/profile, home-branch lifecycle, Manager cross-branch, therapist mismatch, unauthenticated/branchless, เปลี่ยน DB role/home branch แต่ใช้ token เดิม และ deactivate user

## ตรวจเทียบ requirements และ review

| Requirement | หลักฐาน |
|---|---|
| R1 ข้อมูลนัดแยกสาขา | สาขา 01/02 เห็นนัดของตนและ totalItems=1; foreign IDs/actions ได้ 403; explicit/query filters ไม่ bypass |
| R2 Manager เท่านั้นที่นัดข้ามสาขา | Manager สร้าง/อ่าน/filter/reschedule/cancel ได้ข้ามสาขา; non-Manager ได้ 403 และ resource snapshot ไม่เปลี่ยน |
| R3 directory ผู้ป่วยรวม | non-Manager อ่าน/search ผู้ป่วย02 และจองให้ใน home01 ได้; patientId ไม่ใช้เป็น branch authorization |
| ไม่กระทบ commission | production diff มีเฉพาะ appointment controller กับ policy ใหม่; full v2 รวม commission 17 tests ผ่าน; ไม่มี schema/business-service/commission formula diff |
| ทำทีละ P ตรวจแล้ว commit | เฉพาะ P1 appointment subset ตามคำตอบของนายท่าน; ยังไม่เริ่ม P2 และไม่ถือ Q9 resolved |

Code review แยกแบบ read-only ตรวจ policy, ทุก appointment route, current DB user/role/branch, tests และจุดเรียก services แล้ว ไม่พบ actionable defect ในขอบเขตที่อนุมัติ

ข้อจำกัดที่ review พบและยังพัก: management endpoints เดิม เช่น `/api/users` ยังใช้ JWT role claims ดังนั้น Manager ที่ถูกลดสิทธิ์แต่ token ยังไม่หมดอายุอาจใช้ endpoint นี้คืน DB privileges ให้ตนเองได้ การเช็ก DB role ของ appointments ไม่ใช่การเพิกถอน token/สิทธิ์ทั้งระบบ ต้องแก้ใน global authorization/session scope แยกโดยตรวจผลต่อ commission permissions ด้วย

## ส่วนที่ยังไม่ปิดและ environment notes

- Q9/Figma permission matrix, STAFF/ADMIN, login/session branch selection, browser branch selector และ report/sale/course/catalog enforcement ยังพักตามคำตอบนายท่าน
- งานนี้ป้องกัน `/api/appointments` เท่านั้น; ยังไม่กล่าวอ้าง branch isolation ของทั้งระบบหรือ reports/patient activity history
- ไม่รัน migration dry-run, standalone RuntimeCheck/SchemaCheck หรือ GitHub Actions ใหม่; DB/HTTP integration ถูกตรวจด้วย Cypress ในรอบนี้
- มี warning เดิมเรื่อง Electron deprecated, Node DEP0190, Vite native config import และ Cypress trash ที่ลบ screenshots ของ RED ไม่สำเร็จ; ผลผ่านอ้างจาก runner/JUnit ใหม่ ไม่ใช้ screenshots เก่าเป็นสถานะล่าสุด
- SQLState 23505 ของ CreditCard/PromptPay ใน reference-data specs เป็น negative tests ที่ตรวจ HTTP 409 ตามเดิม ไม่ใช่ failed regression
- หลังตรวจครบ ตรวจ owner label แล้วลบเฉพาะ container/anonymous volume ของ P1 สำเร็จ; ตรวจ ports 18080/19001/5173 ไม่มี test listener ค้าง และไม่มี running containers เหลือจากงานนี้

## คำสั่งตรวจ browser ซ้ำ

ตั้ง DATABASE_URL/USERNAME/PASSWORD ให้ชี้เฉพาะฐานทิ้งได้ของงาน แล้วเริ่ม backend ด้วย `mvn --batch-mode --no-transfer-progress -Pcypress-v2 test-compile spring-boot:start` ใน backend; กำหนด `VITE_API_PROXY_TARGET` และ `CYPRESS_API_URL` เป็น `http://127.0.0.1:18080` สำหรับ frontend ก่อนเปิด Vite ที่ localhost:5173

```powershell
npx cypress run --config-file cypress.v2.config.ts --browser electron --spec cypress/e2e-v2/appointment-branch-access.cy.js,cypress/e2e-v2/appointments.cy.js
```

รอบนี้รัน Cypress binary ที่ติดตั้งอยู่ผ่าน `node_modules/.bin/cypress.cmd` โดยตรง ไม่ดาวน์โหลด dependency ใหม่; ทดสอบ API และ browser ตามลำดับเพื่อไม่ให้ fixture resets ชนกัน
