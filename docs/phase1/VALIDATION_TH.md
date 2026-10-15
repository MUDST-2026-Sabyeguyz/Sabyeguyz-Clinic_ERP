# หลักฐานชุดส่งตรวจ Phase 1 — 16 Oct 2026

ใช้ branch `backup/draft-phase1` อิง develop `a8f8f49d58892280e707caec349dec87f6abe227` ระหว่างงาน develop เพิ่ม Cypress alignment จึงรวม `19f5baac3ffe14abe35c081c9a1f2fad992346a8` เข้ามาด้วย และรวมเอกสารเฉพาะ main `2bb57058f1975a222092fad7b01145ab9278f5ce` เข้ามาใน draft แล้ว ไม่เปลี่ยน main/develop

## วิธีตรวจผลของ revision ที่จะส่ง

1. จด `git rev-parse HEAD` ของ source ที่ clone
2. เปิด [Phase 1 review runs ของ draft](https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP/actions/workflows/phase1-review.yml?query=branch%3Abackup%2Fdraft-phase1) หรือ checks ของ PR ไป main
3. ต้องผ่านทั้ง `code-tests-coverage` และ `compose-runtime` อ่าน checked-out SHA ใน Job Summary/summary.json ด้วย หากเป็น PR run ค่า checkout อาจเป็น merge SHA ให้ตรวจ head SHA ของ PR ประกอบ
4. ดาวน์โหลด artifacts เพื่อเปิด stage logs/exits, JUnit, JaCoCo HTML/XML และ frontend V8 HTML/LCOV มี component fingerprints และตรวจว่าระหว่าง run source ไม่เปลี่ยน

ไฟล์นี้สรุปที่มาของ candidate; **ไม่ใช้ local PASS, workflow file หรือผลของคนละ SHA แทน remote CI ของ revision ที่ตรวจ** ผลล่าสุดให้อ่าน run/checks ที่ตรง SHA ตามขั้นตอนข้างต้น

## สิ่งที่เตรียมใน draft

- README และ [run document](REVIEW_GUIDE_TH.md): clone, tools, Compose แบบมี synthetic seed/ฐานว่าง, env/password ที่สร้างเฉพาะเครื่อง, เปิด/หยุดโดยรักษา volume, tests/E2E/coverage และ troubleshooting
- Canonical `node scripts/verify-phase1.mjs`: isolated PostgreSQL 16, Maven/JDK 21 container, frontend unit/lint/build, backend JUnit, HTTP Cypress v2 และ browser Cypress v2 ไม่มี exclude failing spec หรือ retries
- JUnit เพิ่ม 13 cases ด้าน Bangkok month/year/leap-day boundaries, HN running/reset/overflow และส่วนลดบาทที่ยืนยันแล้ว
- JaCoCo เก็บทั้ง unit และ JVM ที่ให้บริการ HTTP/browser จริง และบังคับว่ามี PatientController runtime coverage; stop JVM ก่อนอ่าน report
- Vitest V8 รวม TS/Vue ที่ไม่ได้ import ใน unit suite และเก็บ report เมื่อ test fail ไม่มีการตั้ง threshold ที่ทีมยังไม่อนุมัติ
- `node scripts/verify-compose.mjs` build/start project ใหม่ ทดสอบ health/app shell/JS/401/normal Manager login/authenticated patient read และลบเฉพาะข้อมูลสังเคราะห์ของ run
- `.github/workflows/phase1-review.yml` เรียกสองคำสั่งข้างต้นและ upload หลักฐานแม้ fail รองรับ dedicated runner ที่ owner เลือกผ่าน repo variable
- UI คอร์สแสดง bonus ที่มีอยู่ใน DTO และกันกด use/share/history ระหว่าง initial load ที่ยังไม่เสร็จ เพื่อไม่ให้ initial load ทับหน้าประวัติที่ผู้ใช้เพิ่งเปิด
- E2E ตามหน้าจอปัจจุบัน: registration success modal/profile, ชื่อ label, appointment date field/primary save button/time drawer และ selected-patient summary; คง assertions ด้าน API persistence, conflict และ course ledger

## Local diagnostics ก่อนส่ง candidate ให้ CI

ผลต่อไปนี้เป็น **คนละ run/working-tree fingerprint** ใช้วินิจฉัย ไม่ใช่ final clean-SHA PASS:

| Run / layer | ผลที่ตรวจได้ |
|---|---|
| Initial full local backend JUnit | 13/13, 0 fail/error/skip |
| Initial full local frontend Vitest | 98/98 |
| Initial full local backend HTTP/PostgreSQL | 312/312, 0 fail/error/skip |
| Initial full local browser (19 specs) | 152 tests: 127 passed / 25 failed; เก็บ failures แล้วปรับ selectors/flow ตาม UI |
| Initial Compose demo startup | PostgreSQL/backend/frontend healthy; แยก project/volume/ports จากข้อมูลเดิม |
| Fresh disposable Compose helper | health/app/JS/401/normal login/authenticated patient read PASS; มี image IDs และ cleanup สำเร็จ เป็น source ก่อนแก้ UI คอร์ส |
| Focused browser debug (5 specs, ก่อนแก้รอบสุดท้าย) | 62 tests: 54 passed / 8 failed; นัดหมาย 13/13 และขาย 13/13 ผ่าน |
| Focused browser หลังแก้ผู้ป่วย/ลงทะเบียน/คอร์ส | 36/36 ผ่าน, 0 fail/error/skip, component fingerprints ก่อน/หลังตรงกัน; runtime PatientController มี 98 covered lines |
| Frontend หลังแก้ course initial-load race | 98/98 ผ่าน; type-check/build/ESLint/Oxlint exit 0 (ยังมี typed-mock warnings); V8 lines 1554/4226 = 36.77% |

พบและเก็บ run ที่ติด sandbox EPERM ก่อนเริ่ม unit assertions แยกจาก permitted execution หลังจากนั้น unit/build/lint ผ่าน การเริ่ม backend ที่เปิด agent ใช้เกิน 60 วินาทีในเครื่องนี้ จึงเพิ่ม startup wait ของ cypress-v2 เป็น 180 วินาที; ไม่มีการเพิ่ม test retries

Billing annotation ของ develop run [38815490199](https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP/actions/runs/38815490199) ระบุ recent payments/spending limit และ job ไม่มี steps เป็น environment blocker ของ run นั้น ไม่ใช่ 189 assertion failures Dedicated Windows runner ของ draft ต้องอ่านผล execution ใหม่แยก

## ขอบเขตที่ยังไม่ใช้ชุดตรวจนี้ปิด

- Coverage เป็นบางส่วนและไม่มี approved numeric threshold; frontend V8 เป็น unit coverage ไม่รวม browser execution การผ่าน suites ไม่ยืนยันครบทุก requirement
- Local execution ตรวจบนเครื่องที่เตรียมไว้ ไม่อ้างว่าได้พิสูจน์ fresh machine อื่นแล้ว ต้องทำตามคู่มือและตรวจ prerequisites
- Canonical entry point มุ่ง current clinicv2; legacy Cypress/data-migration gates มี workflow/คู่มือแยก ไม่ใช้ชื่อ `mvn verify` อ้างว่ารัน legacy/HTTP/E2E ครบเอง
- Current-source Kubernetes publish/deploy/Ingress และ authenticated financial acceptance ต้องผูก SHA/image digest/deployment จริงแยก ไม่ใช้ archived demo ปิดแทน
- กฎธุรกิจที่ยังไม่มีคำยืนยัน, semantic test coverage ทุก method และ operational backup policy ไม่ปิดด้วย CI/README รอบนี้
- **ยังไม่อนุมัติขึ้น main** ให้เพื่อนตรวจ [TEAM_REVIEW_TH.md](TEAM_REVIEW_TH.md) และยืนยัน revision ก่อน merge
