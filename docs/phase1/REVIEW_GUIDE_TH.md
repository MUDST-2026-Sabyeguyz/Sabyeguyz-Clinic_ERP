# คู่มือผู้ตรวจ Phase 1 — 16 Oct 2026

ใช้ตรวจ current `clinicv2` ตั้งแต่ clone/open app จน tests/E2E/coverage/pipeline ไม่ผสม schema database-v2 หรือ images ของ archived demo

## เครื่องมือ

| งาน | ติดตั้ง |
|---|---|
| เปิดแอปด้วย Compose | Git, Docker Desktop Linux containers, Compose v2 |
| ตรวจ tests ทั้งหมด | เพิ่ม Node 24.12+ และ npm; ใช้ Maven/JDK 21 ใน Docker |
| พัฒนา backend บน host | เพิ่ม JDK 21 และ Maven 3.9 |
| GitHub Actions | Repo read access; GitHub CLI เป็นทางเลือก |

Windows: เตรียม WSL 2/virtualization และ [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/), [Git](https://git-scm.com/install/windows), [Node 24](https://nodejs.org/en/download) แล้วเปิด terminal ใหม่ ตรวจ:

```powershell
git --version
docker version
docker compose version
docker info --format '{{.OSType}}'
node --version
npm --version
```

Docker ต้องมี server และ OSType linux; Node 20 ใช้ frontend รุ่นนี้ไม่ได้ Login ด้วย browser/Git Credential Manager และบัญชีที่ได้รับสิทธิ์ private repo ไม่ใส่ token ใน URL

## Clone และ source identity

```bash
git clone --branch backup/draft-phase1 https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP.git
cd Sabyeguyz-Clinic_ERP
git branch --show-current
git rev-parse HEAD
git status --short --branch
```

main ยังเป็นอีก revision ระหว่างรอเพื่อน approve ให้ตรวจ draft branch นี้ก่อน บันทึก SHA/diff ทุก run README อธิบายวิธีทำ ไม่แทนผล execution ดู VALIDATION กับ artifact ที่ตรง SHA

<a id="run-compose"></a>
## เปิดระบบ

### Windows PowerShell — จาก repo root

```powershell
.\scripts\start-local.ps1 -DemoSeed
```

รอ healthy แล้วเข้า http://localhost:18082/ ใช้ synthetic `manager/manager123` มี branch/staff/services ตัวอย่าง แยก volume `clinic-phase1-review-demo_postgres-v2-data` สคริปต์สร้าง env เฉพาะเมื่อยังไม่มีและปฏิเสธ volume เดิมที่ขาด env; ไม่ overwrite รหัส/data เดิม

เก็บ `.env.phase1-demo` ไว้เปิดข้อมูลเดิม หาก PowerShell บล็อก script ให้ตรวจ policy กับผู้ดูแลหรือใช้ manual Compose ไม่ปิด policy ทั้งเครื่อง

### Linux/macOS หรือ manual Compose

จาก repo root ตรวจว่าพอร์ต 15432/18081/18082 ว่าง และไม่มี project/volume clinic-phase1-review-demo เดิม (`docker compose ls -a`, `docker volume ls`) หากมี ให้ใช้ env เดิมหรือเลือก project/ports ใหม่ **อย่าสร้างรหัสใหม่ใส่ฐานเดิม**

สร้าง env ครั้งแรกบน project ใหม่เท่านั้น:

```bash
test ! -e .env.phase1-demo || { echo 'Environment exists; inspect instead of overwriting'; exit 1; }
umask 077
cat > .env.phase1-demo <<EOF
POSTGRES_DB=clinic_erp_v2
POSTGRES_USER=clinic
POSTGRES_PASSWORD=$(openssl rand -hex 32)
JWT_SECRET=$(openssl rand -hex 32)
POSTGRES_PORT=127.0.0.1:15432
BACKEND_PORT=127.0.0.1:18081
FRONTEND_PORT=127.0.0.1:18082
EOF
docker compose -p clinic-phase1-review-demo --env-file .env.phase1-demo -f compose.yaml -f compose.phase1-demo.yaml up --build -d --wait --wait-timeout 300
docker compose -p clinic-phase1-review-demo --env-file .env.phase1-demo -f compose.yaml -f compose.phase1-demo.yaml ps
```

### ตรวจ runtime และบัญชีแรก

PowerShell:

```powershell
Invoke-RestMethod http://localhost:18081/actuator/health
(Invoke-WebRequest http://localhost:18082/healthz -UseBasicParsing).StatusCode
```

Linux/macOS ใช้ curl --fail กับ URL เดียวกัน ต้องได้ backend UP/frontend 200 แล้วทดลอง normal login และอ่านข้อมูล ตัว health 200 ไม่ยืนยัน business acceptance

ฐานว่าง: Windows `start-local.ps1` โดยไม่มี DemoSeed สร้าง `.env.phase1`, project clinic-phase1-review และบัญชี phase1.manager กับรหัสสุ่มใน env Bootstrap ทำเฉพาะครั้งแรกที่ไม่มี app user อื่นนอกจาก system ไม่ reset user เดิม หลังเก็บรหัส/login แล้วให้เว้นค่า CLINIC_BOOTSTRAP_MANAGER_* ใน env และ recreate backend ฐานว่างต้องตั้ง master data เพิ่ม ไม่ถือว่ารายการว่างเป็น build failure

### หยุด/เปิดต่อโดยรักษาข้อมูล

```bash
docker compose -p clinic-phase1-review-demo --env-file .env.phase1-demo -f compose.yaml -f compose.phase1-demo.yaml stop
docker compose -p clinic-phase1-review-demo --env-file .env.phase1-demo -f compose.yaml -f compose.phase1-demo.yaml start
```

ไม่ลบ volume และไม่เปลี่ยน POSTGRES_PASSWORD แล้วคาดว่ารหัส PostgreSQL เดิมจะเปลี่ยนตาม Persistent volume ไม่ใช่ off-machine backup

## Tests, E2E และ Coverage

จาก repo root, Node ถูกเวอร์ชัน, Docker เปิด และพอร์ต 18080/5173 ว่าง:

```bash
node scripts/verify-phase1.mjs
```

ไม่ต้อง start app/Vite ก่อน สคริปต์สร้าง database/network/containers แยกเฉพาะ run ใช้ v2 test classpath/profile และ fixture resets เฉพาะฐานนี้ ไม่ต่อ demo/production ไม่ไป kill server ที่พบใช้พอร์ตอยู่

ลำดับ:

1. Frontend npm ci, ESLint/Oxlint แบบไม่ autofix
2. Vitest/V8 coverage และ type-check/build
3. Backend clean verify: JUnit regressions + compile/package/JaCoCo
4. Backend HTTP Cypress v2 บน PostgreSQL จริง
5. Frontend browser Cypress v2 → Vite → backend/PostgreSQL เดียวกัน Specs ทำงานตามลำดับและ reset fixtures
6. Stop owned backend เพื่อ flush agent แล้วสร้าง backend coverage รวม unit/API/browser execution
7. เก็บ source/fingerprints/exits และ cleanup เฉพาะ IDs/labels ที่สร้างเอง รักษา Maven cache `sabyeguyz-m2`

Failing stage ต้องคง exit 1 ไม่มีการข้าม tests เพื่อทำให้ผ่าน ปริมาณ tests แยก layer ไม่บวกเป็นเลขรวมเดียว

```text
artifacts/phase1/<run>/summary.json
artifacts/phase1/<run>/*.log
artifacts/phase1/<run>/backend-api-*.xml
artifacts/phase1/<run>/frontend-browser-*.xml
backend/target/surefire-reports/
backend/target/site/jacoco/index.html
backend/target/site/jacoco/jacoco.xml
frontend/coverage/index.html
frontend/coverage/lcov.info
frontend/coverage/coverage-summary.json
frontend/test-results/unit.xml
```

summary.json เก็บ SHA, backend/frontend fingerprints, totals/failures/errors/skips และ exits แยกชุด ถ้า source ยังมี working diff ต้องอ่าน fingerprint ประกอบ ไม่อ้างผลของ HEAD อย่างเดียว

### รันเฉพาะ Unit

Frontend folder:

```bash
npm ci
npm run test:coverage
npm run build
```

Backend folder (ต้องมี host JDK 21/Maven 3.9; ถ้าไม่มีใช้ entry point เต็มด้านบน):

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

JUnit ชุดนี้ไม่เชื่อม DB หรือเริ่ม Spring app ทั้งตัว Maven verify อย่างเดียวไม่ใช่ HTTP/browser suite ครบ

### ขอบเขต Coverage

JaCoCo denominator คือ production `com.sabyeguyz.clinicv2` ทุก class รวมที่ยังไม่ cover; ไม่รวม legacy หรือ Java test fixtures ใน production denominator V8 รวม source TS/Vue ที่ยังไม่ถูก import ด้วย ยกเว้น declarations/unit test files เป็น **frontend unit coverage ไม่ใช่ browser coverage**

ไม่มี threshold ตัวเลขที่อนุมัติแล้ว ต้องรายงานเปอร์เซ็นต์ต่ำ/ช่องว่างตามจริง ไม่ลด denominator เพื่อให้ดูสูง และไม่ถือว่า reports พิสูจน์ requirements/การเงินครบ

อ้างวิธี agent จาก [JaCoCo prepare-agent](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html) และ [Vitest coverage](https://vitest.dev/guide/coverage.html)

## Pipeline และ Billing

`.github/workflows/phase1-review.yml` เรียก entry point เดียวกัน บน push main/develop/backup/draft-phase1 และ PR เข้า main/develop Upload reports ใช้ always() รวมกรณี fail

ค่าเริ่มต้น ubuntu-latest หาก owner เลือก dedicated existing runner ให้ตั้ง repo variable PHASE1_REVIEW_RUNNER เป็น JSON array ของ labels เช่น `["self-hosted","Windows","X64","clinic-phase1","powershell7"]` Runner ต้อง online และมี Docker Linux engine/Git; งาน PR จาก fork ไม่รันบน dedicated runner นี้; setup-node เตรียม Node ตาม .nvmrc

Billing/spending-limit ที่ทำให้ job ไม่มี steps คือ ENVIRONMENT BLOCKED ไม่ใช่ assertions fail Local PASS ไม่ใช่ remote CI PASS และ Jenkins ไม่แทน GitHub/K8s gate

Compose gate ใหม่ใน Phase 1 review เรียก `node scripts/verify-compose.mjs` สร้าง project/ports/volume ใหม่และ cleanup เฉพาะข้อมูลสังเคราะห์ของ run; workflow เดิมอยู่ `.github/workflows/compose-verify.yml`; Jenkins backup คู่มืออยู่ `jenkins-backup/README_TH.md` Current-source K8s/business acceptance เป็น gates แยก ไม่ใช้ archived deployment revision มาปิดแทน

<a id="troubleshooting"></a>
## Troubleshooting

| อาการ | ตรวจและแก้ |
|---|---|
| Clone 404 | Repo invitation/SSO/บัญชีที่มีสิทธิ์ |
| Docker เข้าไม่ได้ | Docker Desktop, Linux containers, WSL/virtualization, user access |
| Port 18080/5173 ถูกใช้ | หยุดเฉพาะ server ของตนก่อนทดสอบ สคริปต์ไม่หยุดให้ |
| Node 20/engine mismatch | ติดตั้ง Node 24.12+ แล้วเปิด terminal ใหม่ |
| npm/Maven ดาวน์โหลดไม่ได้ | Network/proxy/cache; ระบุว่าติด install/compile ไม่อ้างว่าทดสอบ assertion แล้ว |
| Flyway/schema ต่าง | จับ exception แรก+SHA/profile/ฐาน ห้าม repair/drop schema เพื่อให้ผ่าน |
| Login ไม่ได้ | Demo seed/project/volume ถูกหรือไม่; blank DB ใช้ bootstrap อีกชุด |
| Reports ไม่มีหรือเก่า | ตรวจ run SHA/exits ก่อนเปิด coverage/XML |
| Cypress binary missing | npm exec -- cypress install แล้ว verify ใน package ที่ใช้งาน |
| Linux ขาด libraries | ใช้ [Cypress Linux prerequisites](https://docs.cypress.io/app/get-started/install-cypress#Linux-Prerequisites) หรือ runner image ที่เตรียมไว้ |

ห้าม commit .env, credentials/tokens, SQL dumps, patient data หรือ generated artifacts รายงานใน repo เก็บเฉพาะสรุปที่ตรวจจริง
