# P0: Non-Commission Baseline

วันที่ตรวจ: 14 ตุลาคม 2026 (Asia/Bangkok)

สถานะ: **P0 ผ่าน acceptance gates ที่ระบุในแผน พร้อม commit เฉพาะเอกสาร**

## คำสั่งและฐานงาน

Confirmed Requirement จากคำขอ execution: แยก branch ใหม่ ทำทีละ P ตรวจเทียบ requirements และผลกระทบต่อ commission ก่อน commit

- แผน: `docs/superpowers/plans/2026-10-14-non-commission-implementation.md`
- ขอบเขตและ source mapping: `docs/superpowers/specs/2026-10-14-non-commission-scope.md`
- เริ่มตรวจบน `feat/backend-v2-integration`, HEAD `74629492972f4cfb04bac82cadd47b04668fdd2e`
- ตรวจ local `feat/carlendar` แล้วชี้ commit เดียวกัน จึงสร้าง `codex/non-commission-backend` จาก `feat/carlendar` โดยตรง
- ใช้ checkout เดิมบน branch ใหม่ ยังไม่ได้สร้าง linked worktree; branch แยกประวัติ Git แต่ไม่ใช่พื้นที่ไฟล์แยกตามข้อเสนอ worktree ใน P0
- ก่อนเริ่มมีเอกสาร plan/spec ข้างต้นเป็น untracked 2 ไฟล์; รักษาเนื้อหาเดิมไว้
- ไม่เปลี่ยน application code, schema, migration, test assertions หรือ commission rules

## Environment ที่ตรวจจริง

Windows 11 amd64; Node.js 24.18.0; npm 11.16.0; Maven 3.9.16; Amazon Corretto Java 21.0.12 (pom กำหนด compiler release 17)

มี `backend/node_modules` และ `frontend/node_modules` อยู่แล้ว ไม่ติดตั้ง dependencies ใหม่ในรอบนี้ จึงไม่ได้พิสูจน์ fresh install จาก lockfile

## ผลตรวจใหม่ในรอบนี้

| Gate / คำสั่ง | ผลจริง | ขอบเขตหลักฐาน |
|---|---|---|
| `backend`: `mvn --batch-mode --no-transfer-progress clean package` | ผ่านทั้งรอบแรกและรอบต่อ, exit 0, BUILD SUCCESS; รอบต่อจบ 14:09:55 +07:00 | compile main 343 files และ test 24 files, package Spring Boot jar; ไม่ใช่ผล HTTP/DB tests และไม่มีจำนวน JUnit tests ให้กล่าวอ้าง |
| `frontend`: `npm run test:unit -- --run` ใน sandbox | ล้มก่อนรัน tests: EPERM ขณะ rename temporary files, 5 suites | เป็นข้อจำกัด environment; ยังใช้ผลนี้ตัดสิน product ไม่ได้ |
| คำสั่ง unit tests เดิมนอก sandbox | ผ่าน, exit 0, 5 files / 28 tests | พิสูจน์ frontend unit tests ปัจจุบัน; มี warning เดิมเรื่อง extension ของ import `./vite.config` สำหรับ future native config loader |
| `frontend`: `npm run build` | ผ่าน, exit 0 | vue-tsc และ Vite 8.2.1 production build |
| `backend`: `npm run test:v2` | ผ่าน, exit 0, **143/143 จาก 18 specs**, failed/pending/skipped 0 | รันบน disposable PostgreSQL 16.15; runner หยุด server สำเร็จเวลา 14:09:32 +07:00; ตรวจ JUnit XML ซ้ำได้ 143 tests, 0 failures, 0 skipped |
| Commission regression ใน full v2 run เดียวกัน | `commission.cy.js` **12/12** และ `commission-override.cy.js` **5/5** | preview/tier/pool/closing/override/permissions/audit ตาม test เดิม; admin/catalog/refund/course/sale/report specs ผ่านในชุดเดียวกัน ไม่ใช่การรับรอง business rules ทุกกรณี |
| Browser E2E / RuntimeCheck / SchemaCheck | ยังไม่ได้รัน | ไม่อ้างผลวันที่ 13 ว่าเป็นผลใหม่ |

## Static checks เทียบแผนและข้อจำกัด commission

- `backend/pom.xml` ใช้ `com.sabyeguyz.clinicv2.ClinicV2Application`; entry point scan package v2 และ default profile v2
- `application.properties` และ `application-v2.properties` ใช้ Flyway `classpath:db/migration`; มี V1–V3 และ Hibernate `validate` ตามแผน ไม่แก้ migrations ที่ applied แล้ว
- อ่าน runner `backend/scripts/run-v2-tests.cjs`: ต้องกำหนด DATABASE_URL, ตรวจ ports 18080/19001 ว่างก่อนเริ่ม, ใช้ test-classpath profile `v2,cypress-v2`, หยุด test server ใน finally
- test profile เพิ่ม demo seeds; support ของ Cypress เรียก reset fixtures ก่อนแต่ละ test จึงต้องใช้ DB ทิ้งได้เท่านั้น
- ตรวจ workflow backend-ci, v2-api-ci, schema-ci, frontend-e2e-v2 และ legacy-migration-ci: มี PostgreSQL service และ v2 checks อยู่แล้ว; frontend E2E ใช้ v2 backend จึงไม่ทำ cutover ซ้ำ
- ไม่มี API/DTO contract เปลี่ยนใน P0 จึงยังไม่แก้ `docs/api-v2-frontend-contract.md`; จะอัปเดตเมื่อ P ถัดไปเปลี่ยน behavior จริง
- ตรวจตาม P0 เท่านั้น ไม่ได้ยืนยันว่า R/T ทุกข้อทำครบ; Open Questions Q1–Q13 ใน spec ยังเปิดอยู่
- ไม่มี production diff ในรอบนี้ จึงไม่มีการเปลี่ยน commission code; commission regression ที่มีอยู่ผ่าน HTTP tests ใหม่ตามตาราง ไม่ได้ขยายขอบเขตไปแก้ commission requirements

## ปัญหาสภาพแวดล้อมรอบแรกและผลรอบต่อ

Docker daemon ไม่เปิดตั้งแต่เริ่ม (`dockerDesktopLinuxEngine` pipe ไม่มี) และ `docker desktop start` หา registry installation key ไม่พบ จึงเปิด executable ที่ติดตั้งจริงแบบ hidden เพื่อวินิจฉัย

Log `C:/Users/napal/AppData/Local/Docker/log/host/com.docker.backend.exe.log` ระบุ:

```text
starting services: initializing Ingest server:
listening on unix://C:/Users/napal/AppData/Local/Docker/run/sailor-ingest.sock:
remove .../sailor-ingest.sock: The file cannot be accessed by the system.
```

ตรวจนอก sandbox แล้วยังไม่มี daemon; socket เป็น ReparsePoint และไม่สามารถย้ายเป็นไฟล์สำรองได้ แม้หยุด Docker แล้ว ไม่มีไฟล์ socket ถูกแก้สำเร็จ ไม่มีการ factory reset หรือลบ Docker data/volumes การตรวจ PostgreSQL ใน Windows/Ubuntu WSL ไม่พบ installation ที่พร้อมใช้

รอบแรกหยุด Docker ที่เปิดด้วย `docker desktop stop --force --timeout 15` สำเร็จ เพื่อไม่ทิ้ง failed startup ค้างไว้ ยังไม่สร้าง container หรือเชื่อมต่อฐานข้อมูลในรอบแรก

นายท่านเปิด Docker ให้ในรอบต่อ ตรวจนอก sandbox ได้ daemon 29.7.2 และไม่มี running containers ก่อนสร้างฐานทดสอบ; การเข้าถึง pipe ภายใน sandbox ถูกปฏิเสธ จึงรัน Docker/API tests นอก sandbox โดยไม่เปลี่ยน application code

- Container ของงานนี้: `alice-noncommission-p0-20261014`, owner label `codex.task=noncommission-p0-20261014`, image `postgres:16-alpine`
- PostgreSQL 16.15, bind เฉพาะ `127.0.0.1:55434`, database `noncommission_p0`, user `clinic_p0`; สร้าง password สุ่มและส่งผ่าน process environment ไม่ลงเอกสารหรือ Git
- readiness probe ครั้งแรกยังไม่พร้อม; ครั้งถัดไป accepting connections แล้วจึงเริ่ม tests
- ตรวจ `flyway_schema_history`: V1 initial schema, V2 integration runtime compatibility, V3 reference data และ V1000 demo data เป็น success ทั้งหมด; V1000 เป็น test seed ไม่ใช่ production migration ใหม่
- ตรวจฐานทดสอบมีสาขา `01` และ `02`; ไม่อ้างว่ามี STAFF/ADMIN permission matrix ครบเพราะเป็นงาน P1
- Frontend ตรวจซ้ำในรอบต่อ: unit 28/28 และ build exit 0; package backend รันใหม่หลัง API runner หยุดแล้วเพื่อไม่ชน target/classes
- ระหว่าง reference-data tests มี SQLState 23505 ของ CreditCard/PromptPay: ตรงกับ negative cases ที่ assert duplicate method ได้ HTTP 409 (`reference-data.cy.js:90,106`); ไม่ใช่ failed tests
- มี warning เดิมจาก Electron deprecated และ Node DEP0190 ของ test runner; ไม่มีการเปลี่ยน dependency/runner ใน P0
- หลัง tests ผ่าน ตรวจ owner label แล้วลบเฉพาะ container นี้พร้อม anonymous volume สำเร็จ; Docker Desktop ที่นายท่านเปิดไว้ยังทำงานอยู่

## ตรวจเทียบ requirements ก่อนปิด P0

| ข้อกำหนด P0 / คำขอ execution | หลักฐานและข้อจำกัด |
|---|---|
| แยก branch ตามแผน | `codex/non-commission-backend` จาก local feat/carlendar ที่ 7462949; เป็น branch ใน checkout เดิม ไม่ใช่ linked worktree |
| ไม่ทับงานค้าง | plan/spec เดิมถูกเก็บ; commit จะรวมเอกสารกำกับแผนและรายงาน P0 เท่านั้น |
| ไม่กระทบ commission | ไม่แก้ application/schema/tests และ full API baseline รวม commission 17 cases ผ่าน |
| ทดสอบบน DB แยก | container/database/port เฉพาะงาน; migration และ seed success; ไม่ reset ฐานจริง |
| ตรวจทั้ง backend/frontend | HTTP 143/143, frontend unit 28/28, frontend build ผ่าน; package backend บันทึกผลด้านบน |
| ทำทีละ P แล้วตรวจและ commit | รอบนี้เฉพาะ P0; ไม่มี P1 implementation หรือการ resolve Q1–Q13 โดยอนุมาน |

ข้อจำกัดคงเดิม: ยังไม่รัน browser E2E, RuntimeCheck/SchemaCheck หรือ GitHub Actions ใหม่ เพราะไม่ใช่ gate ที่ P0 ระบุให้รัน; ชุด HTTP tests ที่ผ่านยืนยันพฤติกรรมเดิมตาม assertions เท่านั้น โดยเฉพาะ phase2 workflow ยังแยกขายบริการรับเงินกับซื้อคอร์สเป็นคนละรายการ

## ประวัติการตรวจ 13:59 น. ก่อนรอบที่สำเร็จ

- ตรวจทั้งในและนอก sandbox: `docker info`, `docker ps` และ `docker --context default info` ยังเชื่อมต่อ daemon ไม่ได้; ทั้ง desktop-linux และ default ไม่มี engine pipe
- เปิด executable จริงและรอ startup แล้ว พบ processes ของ Docker Desktop แต่ log ใหม่เวลา 13:59:06–13:59:07 (Asia/Bangkok) ยืนยัน startup ล้มที่ `sailor-ingest.sock` ด้วย `The file cannot be accessed by the system.` เช่นเดิม
- ณ เวลา 13:59 น. Engine ยังไม่พร้อมและยังไม่ได้สร้างฐานทดสอบหรือรัน API/commission suite; สถานะนี้ถูกแก้ในรอบต่อเวลา 14:06–14:09 น. ตามผลผ่านในรายงานด้านบน
