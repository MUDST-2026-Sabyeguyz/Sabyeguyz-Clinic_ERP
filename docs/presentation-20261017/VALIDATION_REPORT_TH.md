# ผลตรวจสำหรับพรีเซนต์ 17 ตุลาคม

ตรวจวันที่ 13 ตุลาคม 2026 ใช้ฐาน `develop` SHA `c62d68b07aae805d9b79492bbd6be8c5eee1cdab` และ working tree ของ `codex/presentation-devops-20261017` ที่เพิ่ม infra/tests/docs รอบนี้

## ผลในเครื่อง

| สิ่งที่ตรวจ | ผล | หลักฐาน/ขอบเขต |
|---|---|---|
| Backend `mvn --batch-mode --no-transfer-progress verify` | PASS | 221 tests, failures/errors/skips 0; Java 21.0.12.1; 24 classes |
| Unit classification | 29 tests / 4 classes | จำแนก source ของ test; ไม่ใช้ชื่อลงท้าย ServiceTest แทนประเภท |
| Spring integration/component classification | 192 tests / 20 classes | Spring/H2/test profile; ไม่ใช่ PostgreSQL ทั้งหมด |
| JaCoCo line | 70.85% | 1,208 covered / 1,705 lines |
| JaCoCo branch | 32.37% | 201 covered / 621 branches |
| JaCoCo instruction | 53.04% | 6,979 / 13,158 instructions |
| JaCoCo method | 54.49% | 479 / 879 methods |
| Frontend unit | PASS 3/3 | 1 Vitest file; ไม่ใช่ทุก module |
| Frontend production build | PASS | Type check + Vite build |
| Local browser E2E | PASS 3/3 | 2 login scenarios + authenticated patient readback/reload; Electron |
| PostgreSQL integration | PASS | Real auth/401, create/read/search + direct DB count = 1 |
| Compose restart persistence | PASS | restart PostgreSQL/backend แล้วอ่าน patient/HN เดิมได้ |
| Actionlint 1.7.12 | PASS | ทั้ง 3 workflows; ไม่ได้เปิด shellcheck/pyflakes ที่ไม่มีในเครื่อง |
| PowerShell script parse | PASS | deploy script syntax |
| Kubernetes server dry-run | PASS | 10 resources, Traefik CRD, isolated namespace; ยังไม่ใช่ rollout PASS |

รายละเอียดราย class อยู่ใน `BACKEND_TEST_INVENTORY.csv` และรายงาน JaCoCo ที่ `backend/target/site/jacoco/index.html` หลัง verify; folder target เป็น generated evidence ไม่ได้ commit

Logs ในเครื่องอยู่ที่ `../tmp/presentation-20261017-sources/` จาก checkout นี้ ได้แก่ `backend-verify.log`, `frontend-unit.log`, `frontend-build.log`, `compose-build.log`, `cypress-local.log` ส่วน JSON API evidence อยู่ที่ `evidence/` ซึ่งถูก gitignore

การรันครั้งแรกใน sandbox ค้างที่ Mockito และ Vitest พบ EPERM กับ temp file จาก environment จึงหยุดงานที่ค้างและรันใหม่ผ่าน approved host execution ได้ผลตามตาราง ไม่ได้แก้ tests เพื่อให้ผ่านข้อจำกัดนี้

## หลักฐาน GitHub ก่อนเปลี่ยน pipeline

ตรวจจาก GitHub API ใหม่วันที่ 13 ต.ค. SHA develop `c62d68b`:

- [Backend CI 38019730878](https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP/actions/runs/38019730878): success
- [Frontend CI 38019730897](https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP/actions/runs/38019730897): success; Vitest 3 และ Cypress login 2
- [Compose build 38019730844](https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP/actions/runs/38019730844): success; build เดิมยังไม่ deploy k8s

## Pipeline และ deployment รอบใหม่

สถานะส่วนนี้ต้องอัปเดตจาก run ของ branch DevOps ที่ commit แล้ว: CI/CD, GHCR digests, Kubernetes rollout, authenticated Ingress และ PVC/restore proof ยังไม่ใช้ผล local ข้างบนแทน

หลักฐานปลายทางจะอยู่ใน artifact `backend-tests-and-jacoco-<SHA>`, `frontend-browser-tests-<SHA>`, `compose-evidence-<SHA>` และ `kubernetes-demo-<SHA>` โดย `deployment-checkpoint.json` ต้องมี source SHA, image refs, namespace/context และ PVC UID

## DB ล่าสุดของทีม

`feat/database` SHA `ab4e0d29158560b10857e0a8be5c4bde43c7fcad`, PR [#23](https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP/pull/23) ยังเปิดอยู่ ณ เวลาตรวจ:

- [Backend CI 38340731291](https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP/actions/runs/38340731291): success
- [Frontend CI 38340731236](https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP/actions/runs/38340731236): success
- [Compose build 38340731240](https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP/actions/runs/38340731240): success

ผลของ branch DB ไม่ใช่ผลของ develop หลังรวม ต้อง rebase/resolve workflow และทดสอบใหม่ก่อนใช้ schema นี้ในเดโมที่จะส่งจริง

## สิ่งที่ยังไม่ยืนยันว่าเสร็จ

- การรวม PR DevOps และ PR DB เข้า develop และ CI บน SHA หลังรวม
- Financial acceptance และสูตร commission จาก LA_BALANCE ทั้งชุด
- Coverage ครบทุก frontend module/function และ E2E ทุก business flow
- Lab Week 9 ส่วน project ของเพื่อนต่าง project ใน namespace อีกชุด
- Google Calendar OAuth/two-way sync จริง
- Production/off-cluster scheduled backup, retention และ recovery policy

Checkpoint นี้แยก infrastructure proof ออกจาก business acceptance และไม่ประกาศว่า Phase 1 COMPLETE
