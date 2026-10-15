# Sabyeguyz Clinic ERP — Phase 1 review

Vue 3/TypeScript frontend, Spring Boot `clinicv2`, PostgreSQL 16/Flyway, Docker Compose, Vitest, JUnit, Cypress and GitHub Actions.

**ชุดเตรียมส่งตรวจ 16 Oct 2026:** branch `backup/draft-phase1` อิง develop ล่าสุด ยังไม่ merge เข้า main จนเพื่อนยืนยัน และไม่ใช่คำยืนยันว่า business requirements/Phase 1 ครบทั้งหมด

- **[คู่มือผู้ตรวจ: clone → run → tests/E2E → coverage → pipeline](docs/phase1/REVIEW_GUIDE_TH.md)**
- [ผลตรวจจริงและข้อจำกัด](docs/phase1/VALIDATION_TH.md)
- [รายการให้เพื่อน review ก่อนขึ้น main](docs/phase1/TEAM_REVIEW_TH.md)
- [User stories](docs/user-stories/README.md) · [Figma](docs/figma.md) · [QA strategy](docs/testing/README.md)

## 1. Clone revision ที่ต้องตรวจ

ต้องมี Git และสิทธิ์อ่าน repository:

```bash
git clone --branch backup/draft-phase1 https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP.git
cd Sabyeguyz-Clinic_ERP
git rev-parse HEAD
```

จด SHA ก่อนรัน ระหว่างรออนุมัติ main ให้ใช้ branch นี้ ไม่ใช้ผล CI/coverage ของ archived backup/present-environment แทน app revision นี้

## 2. เปิดแอปพร้อม synthetic demo data

ขั้นต่ำ: Docker Desktop ที่เปิด **Linux containers และ Compose v2** ไม่ต้องลง host Java/Node/Maven/PostgreSQL เพื่อเปิด Compose

Windows PowerShell จาก repo root:

```powershell
.\scripts\start-local.ps1 -DemoSeed
```

สคริปต์สุ่มรหัส DB/JWT เก็บ local ใน `.env.phase1-demo`, แยก project `clinic-phase1-review-demo` / volume `clinic-phase1-review-demo_postgres-v2-data`, build/start และรอ postgres/backend/frontend healthy

| สิ่งที่ตรวจ | URL / ค่า |
|---|---|
| Login | http://localhost:18082/ |
| บัญชีสังเคราะห์ | manager / manager123 |
| Backend health | http://localhost:18081/actuator/health |
| Swagger | http://localhost:18081/swagger-ui/index.html |
| PostgreSQL host port | 127.0.0.1:15432 |

พอร์ตต้องว่าง เก็บ env file ไว้เปิดข้อมูลเดิม ห้ามใช้ demo seed กับฐานจริง สำหรับ Linux/macOS หรือ manual Compose ดู [คำสั่งพร้อม env](docs/phase1/REVIEW_GUIDE_TH.md#run-compose)

ถ้าต้องการฐานว่างให้รัน `start-local.ps1` โดยไม่ใส่ DemoSeed บัญชีแรก phase1.manager ใช้รหัส bootstrap สุ่มใน `.env.phase1`; ฐานนี้ยังต้องตั้ง branch/staff/services ก่อนใช้ flow

หยุดโดยรักษาข้อมูล:

```powershell
docker compose -p clinic-phase1-review-demo --env-file .env.phase1-demo -f compose.yaml -f compose.phase1-demo.yaml stop
```

เปิดต่อด้วย start-local.ps1 -DemoSeed ไม่ลบ volume เพื่อแก้ password mismatch

## 3. Tests/E2E/coverage ด้วยคำสั่งเดียว

เพิ่ม **Node 24.12+ และ npm** ตาม .nvmrc; Docker เปิดอยู่ และพอร์ต **18080/5173** ต้องว่าง จาก repo root:

```bash
node scripts/verify-phase1.mjs
```

ติดตั้ง dependencies ตาม lockfile, ESLint/Oxlint แบบไม่ autofix, frontend unit/V8 coverage/type-check/build, backend JUnit/JaCoCo, backend HTTP Cypress และ frontend browser Cypress v2 กับ PostgreSQL ที่สร้างเฉพาะ run ใช้ Maven/JDK 21 ใน Docker จึงไม่ต้องลง Maven/JDK บน host สำหรับ entry point นี้

ใช้ Electron ที่มากับ Cypress ไม่ต้องลง Chrome เอง Fixtures reset เฉพาะฐาน test แยก ไม่ชี้ไป demo/production Cleanup ตรวจ IDs/labels ของ run และเก็บ dependency cache ใช้ซ้ำ

| ผลลัพธ์ | ตำแหน่ง |
|---|---|
| Source SHA, code fingerprints, stage exits/logs | artifacts/phase1/แต่ละ-run/summary.json และ logs |
| Backend unit JUnit | backend/target/surefire-reports/ |
| Backend JaCoCo HTML/XML/CSV | backend/target/site/jacoco/ |
| Frontend Vitest JUnit | frontend/test-results/unit.xml |
| Frontend coverage HTML/LCOV/summary | frontend/coverage/ |
| HTTP/browser Cypress JUnit | artifacts/phase1/แต่ละ-run/ |

Backend denominator รวม production clinicv2 ทุก class รวมที่ยังไม่ cover โดยไม่รวม legacy; frontend รวม TS/Vue source ที่ไม่ได้ import ใน unit suite ด้วย เป็น **frontend unit coverage ไม่ใช่ browser coverage** ไม่มี threshold ตัวเลขที่อนุมัติ และไม่ใช้เปอร์เซ็นต์แทน requirement completeness

## 4. Pipeline as code

[Phase 1 review workflow](.github/workflows/phase1-review.yml) เรียก entry point เดียวกับ local บน push main/develop/backup/draft-phase1 และ PR เข้า main/develop พร้อม upload reports แม้ fail

Workflow เดียวกันมี Compose build/runtime/normal-login gate แยกผ่าน `node scripts/verify-compose.mjs`; [Compose build เดิม](.github/workflows/compose-verify.yml) และ [workflows ทั้งหมด](.github/workflows) เป็น source configuration; CI PASS ต้องมี run ที่ตรง SHA จริง Default workflow ใหม่ใช้ ubuntu-latest กรณี Billing block ให้ owner แก้ Billing/Actions budget หรือเลือก dedicated self-hosted runner ตามคู่มือ Local PASS ไม่ใช่ remote CI PASS

Current-source Kubernetes deployment และ business acceptance ยังเป็น gates แยกจาก pre-review นี้ Historical archived deployment ไม่ยืนยัน clinicv2 revision นี้

## 5. หากรันไม่ได้

อ่าน [troubleshooting](docs/phase1/REVIEW_GUIDE_TH.md#troubleshooting) เริ่มจาก exception แรก ห้ามข้าม tests/ล้าง schema/แก้ applied migrations เพื่อทำให้เขียว ไม่ commit .env, credentials, dumps, generated artifacts หรือ patient data
