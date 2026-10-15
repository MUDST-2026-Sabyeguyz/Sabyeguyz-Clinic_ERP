# Pre-merge baseline: develop / dev-backed

วันที่ตรวจ: 12 ตุลาคม 2026 (Asia/Bangkok)

## ขอบเขต

ดำเนินการช่วง 0 ของแผนรวม backend v2: fetch remote, เก็บ commit อ้างอิง และรันทดสอบแต่ละ branch แยกกัน เพื่อใช้เทียบผลหลัง merge ในช่วง 1 ไม่มีการแก้ application source, สลับ branch, merge, commit หรือ push ในรอบนี้

## Git baseline

`git fetch origin --prune` สำเร็จ และ working tree สะอาดก่อนเริ่ม

| Ref | Commit หลัง fetch | เทียบ remote |
|---|---|---|
| develop | c62d68b07aae805d9b79492bbd6be8c5eee1cdab | ตรงกับ origin/develop (0 ahead / 0 behind) |
| dev-backed | 4b63f31e86c2d976b066a34ab6743284c3cce7e5 | ตรงกับ origin/dev-backed (0 ahead / 0 behind) |
| merge base | bf2df340e45eb1fa59ecd88f626ecf1ed4f59d55 | develop มี 43 commits เฉพาะฝั่ง และ dev-backed มี 33 |

ทดสอบจากสำเนา `git archive` ของ commit ข้างต้นในคนละ directory ไม่ใช้ไฟล์ uncommitted หรือ build output ของ checkout หลัก ใช้ `npm ci` ตาม lockfile ของแต่ละ branch สำเนา archive, SHA-256, manifest, logs, exit codes และ runner อยู่ใน [baseline artifacts](C:/Users/napal/.codex/visualizations/2026/10/12/01a130ca-73c9-7843-a985-8fc559a74e3b/baseline/manifest.json)

สภาพแวดล้อม: Windows 11, Corretto Java 21.0.12, Maven 3.9.16, Node 24.18.0, npm 11.16.0, Cypress 16.1.0 / Electron 146, PostgreSQL 16.15 (`postgres:16-alpine`)

## ผลการตรวจสด

| Check | develop | dev-backed |
|---|---|---|
| Backend build | `mvn -o --batch-mode --no-transfer-progress verify` ผ่าน | `mvn -o --batch-mode --no-transfer-progress clean package` ผ่าน |
| Backend automated tests | 221 Java tests; failures/errors/skipped = 0 | Cypress API 395 tests / 17 specs; failures/pending/skipped = 0 |
| Frontend unit | 3 tests / 1 file ผ่าน | 3 tests / 1 file ผ่าน |
| Frontend production build + type-check | `npm run build` ผ่าน | `npm run build` ผ่าน |
| Frontend Login E2E + legacy PostgreSQL backend | 2 tests / 1 spec ผ่าน | 2 tests / 1 spec ผ่าน |
| v2 SQL + Hibernate validate + bean wiring บน PostgreSQL | ไม่มี SchemaCheck นี้ใน branch | ผ่าน: 481 mapped columns, 0 problems; 45 entities, 20 controllers, 30 services |

คำสั่ง frontend unit: `npm run test:unit -- --run` ส่วน backend API ใช้ `npm run test:api` หลัง package โดย runner เปิด/หยุดเซิร์ฟเวอร์ทดสอบเองบน 127.0.0.1:18080 / JMX 19001

Schema baseline ทำตาม workflow `schema-ci.yml`: โหลด `db/migration_new/V1__initial_schema.sql` ด้วย `psql -v ON_ERROR_STOP=1`, สร้าง test classpath ด้วย `mvn -o --batch-mode --no-transfer-progress -q dependency:build-classpath "-Dmdep.outputFile=target/test-classpath.txt" "-Dmdep.includeScope=test"`, แล้วรัน `com.sabyeguyz.schemacheck.SchemaCheck` โดยกำหนด DB_URL/DB_USER/DB_PASSWORD สำหรับฐานข้อมูลทดสอบ

## ปัญหาสภาพแวดล้อมและสิ่งที่พบก่อน merge

- `git fetch` รอบ sandbox เขียน `.git/FETCH_HEAD` ไม่ได้; รันนอก sandbox แล้วสำเร็จ
- `develop` backend รอบ sandbox ค้างที่ Mockito self-attach เช่นการตรวจครั้งก่อน; หยุดรอบนั้นและรันคำสั่งเดิมนอก sandbox ได้ 221 tests ผ่าน จึงไม่นับรอบที่ค้างเป็น application test failure
- Frontend unit รอบ sandbox ถูก `EPERM` ตอน rename ไฟล์ชั่วคราวก่อนเริ่ม tests; รันนอก sandbox ผ่านทั้งสอง branch
- PostgreSQL SchemaCheck รอบ sandbox ถูก `Permission denied: getsockopt`; รันนอก sandbox ผ่าน
- การติดตั้ง frontend แบบ offline ใน cache ใหม่ขาด package; ใช้ `npm ci` online สำเร็จ ไม่แก้ lockfile
- Login E2E รอบแรกใช้ origin `127.0.0.1:15173` / `127.0.0.1:15174`: ทั้งสอง branch ผ่าน 1 และ fail 1 เพราะได้ 403 แทน 400 ขณะที่ `WebConfig.java:11` อนุญาตเฉพาะ `http://localhost:5173` จึงเก็บ logs รอบแรกแยกเป็น `*-frontend-e2e-custom-origin.*` และแก้เฉพาะ baseline runner ให้ทดสอบ sequential บน origin ตาม config ไม่แก้ application source

## ขอบเขตของหลักฐาน

- Java unit/controller tests 221 กับ Cypress API 395 เป็นคนละชุดและคนละ coverage ไม่ใช้จำนวน tests ตัดสินว่า branch ใดถูกต้องกว่า
- Backend Cypress ใช้ H2 PostgreSQL mode และ migration legacy; v2 scenarios บางส่วนใช้ `V2RepositoryStub` จึงไม่ได้พิสูจน์ workflow v2 ผ่าน PostgreSQL จริง
- SchemaCheck พิสูจน์ SQL/schema mapping และ bean wiring ของ v2 บน PostgreSQL แต่ไม่พิสูจน์กฎธุรกิจทั้งหมดหรือ frontend integration กับ v2
- Frontend E2E ที่มีอยู่ครอบคลุมหน้า Login สองกรณีเท่านั้น ใช้ packaged legacy backend ของแต่ละ branch บน PostgreSQL แยก ไม่ใช่การทดสอบ cutover v2
- ผลนี้เป็น local baseline ไม่ใช่ผล GitHub Actions ล่าสุด; ไม่ได้รัน lint ที่มี `--fix` เพื่อรักษา source snapshots

## การแยกข้อมูลและ cleanup

PostgreSQL baseline ใช้ container เฉพาะ `clinic-baseline-01a130ca`, loopback port 15432, tmpfs และฐานข้อมูล `baseline_v2`, `baseline_develop`, `baseline_devbacked` Backend Login E2E ใช้ 18081/18082 และ frontend ใช้ localhost:5173 ทีละ branch; runner หยุด process ที่สร้างเองหลังรัน

ตรวจ label ก่อน cleanup แล้วหยุด container สำเร็จ (`--rm` ลบ container และ tmpfs) ไม่เหลือ container หรือ listener บน ports ทดสอบ 15432/18080/18081/18082/19001/5173 Docker Desktop ยังเปิดอยู่ เก็บ artifacts/logs ไว้สำหรับเปรียบเทียบหลัง merge

**สถานะช่วง 0: เสร็จแล้ว** ชุดตรวจ baseline หลักผ่านบนทั้งสอง branch ภายใต้สภาพแวดล้อมที่ระบุ ข้อผิดพลาด sandbox และ origin รอบแรกเก็บแยกไว้และมีผล rerun สำเร็จ ไม่พบ test failure ที่ค้างอยู่ในชุดตรวจนี้ หลังจบ checkout หลักยังอยู่บน develop และมีเพียงรายงานนี้เป็นไฟล์ใหม่ ยังไม่ commit

## หลักฐาน

ทุกไฟล์ด้านล่างอยู่ใต้ `C:/Users/napal/.codex/visualizations/2026/10/12/01a130ca-73c9-7843-a985-8fc559a74e3b/baseline/`

- `manifest.json`, `refs.txt`, `initial-status.txt`, `develop-ref.txt`, `dev-backed-ref.txt`, `develop.zip`, `dev-backed.zip`
- `develop-backend-unrestricted.log` / `.exit` และ `develop/backend/target/surefire-reports/`
- `dev-backed-package.log` / `.exit`, `dev-backed-api.log` / `.exit` และ `dev-backed/backend/cypress/results/`
- `develop-frontend-unit-unrestricted.log`, `dev-backed-frontend-unit-unrestricted.log` และ `.exit`
- `develop-frontend-build.log`, `dev-backed-frontend-build.log` และ `.exit`
- `schema-sql.log`, `schema-check-unrestricted.log` และ `.exit`
- `run-frontend-e2e.ps1`, `develop-frontend-e2e.log`, `dev-backed-frontend-e2e.log` และ `.exit`
- `cleanup.log` / `.exit`

ช่วงถัดไปตามแผนคือสร้าง integration branch จาก develop แล้วรวม dev-backed โดยใช้ผล baseline นี้เป็นจุดเปรียบเทียบ ไม่ได้ดำเนินการช่วง 1 ในคำขอนี้
