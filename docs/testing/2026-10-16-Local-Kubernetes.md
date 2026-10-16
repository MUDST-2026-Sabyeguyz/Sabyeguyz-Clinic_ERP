# หลักฐานส่งงาน Local Kubernetes — 16 ตุลาคม 2026

## เกณฑ์และขอบเขต

เกณฑ์ตรวจล่วงหน้าที่ผู้ใช้แจ้งวันที่ 16 ต.ค. 2026: code มีมาตรฐานและอ่านง่าย, E2E, Pipeline as Code, tests/coverage บางส่วน, ระบบรันได้ และ README/run document ที่ทำตามได้ใน repo รายการ Kubernetes ใช้ [context §12](../context.md): Namespace, ConfigMap, Secret, PostgreSQL/Backend/Frontend Deployments/Services, Ingress, PVC และ health probes

เอกสารนี้เป็นหลักฐาน DevOps และ tests ตามคำสั่งที่รันจริง ไม่ได้ใช้แทนการยืนยันทุก business requirement ของคลินิก

## Source และ environment

- Branch ส่งงาน: `codex/local-kubernetes` (ต่อจาก Jenkins PR #52)
- Application build revision: `c73c0b091d76ab390e6ec7df751ee142aa39ef60`; application source ไม่มีการแก้ที่ยังไม่ commit ขณะ build
- Kubernetes manifests/สคริปต์แก้ compatibility และ startup เพิ่มใน `d7600ce`; source แอปใน images ใช้ revision ด้านบน
- Windows 11, Node.js `24.13.0`, JDK `17.0.17` สำหรับ tests/host compilation, Java 21 ใน runtime image, Maven `3.9.16`
- Docker Desktop Linux engine: `aarch64`; Kubernetes `1.36.1`, kubectl `1.36.3`, Kustomize `5.8.1`
- Context `docker-desktop`, namespace `clinic-local`, default StorageClass `standard`, PVC ขนาด `2Gi`
- แยกฐาน/Secret/PVC จาก Compose เดิม และใช้ข้อมูลสังเคราะห์ใน acceptance

## ผลตรวจ

| รายการ | ผลที่ยืนยัน |
|---|---|
| Frontend ESLint | PASS: `npm run lint:check` ไม่ใช้ `--fix` |
| Frontend TypeScript/production build | PASS: `npm run build` |
| Frontend unit tests | PASS: 16 files, 98 tests |
| Frontend V8 line coverage | 36.75% — 1,551 / 4,220 lines |
| Frontend statements / branches / functions | 35.57% / 31.83% / 28.08% |
| Backend Cypress regression | PASS: 17 specs, 432 tests พร้อม JaCoCo agent ใน JVM |
| V2 HTTP integration บน PostgreSQL 16 | PASS: 32 specs, 316 tests |
| Backend JaCoCo final line coverage | 88.27% — 5,389 / 6,105 lines |
| Frontend v2 browser E2E | ENVIRONMENT BLOCKED — รอ Docker/PostgreSQL เปิดกลับ |
| Kubernetes server validation / rollout | PASS: ทุก workload Ready, PVC Bound |
| Kubernetes authenticated/persistence acceptance | PASS: 13 checks |
| Kubernetes production browser E2E | ENVIRONMENT BLOCKED — รอบก่อน 3/3 timeout ขณะ Kubernetes API ไม่ตอบสนอง; ต้องรันใหม่ |
| Jenkins adapter regression | PASS: 3 tests รวม manual gate และ unsupported action failure |
| GitHub-hosted Actions execution | NOT RUN: เตรียม manual workflow และตรวจ YAML แล้ว |
| Minikube runtime | NOT RUN: path เตรียมไว้; runtime evidence รอบนี้ใช้ Docker Desktop Kubernetes |

Coverage เป็นการวัดจริง ไม่มีการตั้ง threshold แทนอาจารย์ Frontend รวม production `.ts/.vue` ที่ยังไม่มี tests ด้วย Backend report ครอบคลุม `com.sabyeguyz.clinicv2` เฉพาะ production classes และวัดจาก HTTP regression/integration ไม่รวม browser E2E ในยอด JaCoCo นี้

## Kubernetes runtime

เปิดแอปที่ `http://localhost:18088/` ผ่าน port-forward ของ **Ingress controller Service** ไม่ใช่ backend/frontend Service โดยตรง จึงตรวจ routing จริงทั้ง Vue และ `/api` backend

`Test-Local.ps1 -TestPersistence` ผ่าน:

1. Ingress → backend/PostgreSQL health `UP`
2. หน้า Vue production build
3. Vue deep-link refresh fallback
4. Anonymous patient API ถูกปฏิเสธด้วย 401
5. Login manager จริง
6. สร้างและอ่านกลับ patient สังเคราะห์ผ่าน API
7. อ่าน patient/HN เดิมหลัง PostgreSQL pod replacement
8. อ่าน patient/HN เดิมและใช้ JWT เดิมหลัง backend rollout
9. PVC Bound
10–13. Backend, frontend, PostgreSQL, Traefik pods Ready

Flyway production migrations สำเร็จ 10 รายการ PostgreSQL ใช้ `Recreate` และ PVC เดิม Backend มี init container รอฐานพร้อม, startup/readiness checks HTTP และ liveness TCP เพื่อลด restart loops เมื่อฐานหยุดชั่วคราว

## Pipeline as Code และเอกสารรัน

- [Kubernetes runbook](../../deploy/k8s/README_TH.md): prerequisites, fresh clone, build/deploy, login, tests/coverage, logs, rollback และหยุดโดยเก็บข้อมูล
- [Code standards](../../CONTRIBUTING.md): frontend checks, service/controller separation, test levels, migrations และ review evidence
- [GitHub workflow](../../.github/workflows/kubernetes-ci.yml): quality → Docker image builds → kind deployment → persistence/authentication → production browser E2E → artifacts
- GitHub workflow นี้สั่ง manual เพื่อคงการตรวจปกติไว้บน Jenkins ในเครื่อง ไม่เปิด GitHub-hosted runs ใหม่ทุก push
- Jenkins push replay รายงาน manual jobs เป็น `skipped` พร้อมเหตุผล ต้องใช้ผล local acceptance หรือ dedicated workflow แยก ไม่ถือว่าการ skip คือ Kubernetes ผ่าน

## ทำซ้ำบนเครื่องนี้

```powershell
pwsh -File scripts/Test-Submission.ps1
pwsh -File deploy/k8s/Start-Local.ps1 -HostBuild
pwsh -File deploy/k8s/Open-Local.ps1
# เปิด terminal เพิ่ม:
pwsh -File deploy/k8s/Test-Local.ps1 -TestPersistence
cd frontend
$env:CYPRESS_BASE_URL = 'http://127.0.0.1:18088'
$env:CLINIC_K8S_CREDENTIALS = (Resolve-Path ../deploy/k8s/runtime/docker-desktop-credentials.json).Path
npm run test:e2e:k8s
```

รหัส manager สุ่มอยู่ใน runtime ที่ Git ignore อ่านเฉพาะในเครื่องตาม runbook ไม่อยู่ในเอกสาร/หลักฐานสาธารณะ

## ข้อจำกัดจากการรันจริง

- WSL เดิมจำกัด RAM 4 GB และมี Jenkins/Compose เปิดพร้อมกัน Docker/Kubernetes API ไม่ตอบสนองซ้ำขณะทดสอบ จึงสำรอง `.wslconfig` ใน ignored runtime และเพิ่มเพดานเป็น 6 GB ตรวจ `/proc/meminfo` เห็นประมาณ 6 GB แล้ว
- Docker Desktop รอบเปิดกลับติด stale `sailor-ingest.sock`; ผู้ใช้อนุญาตให้ลบเฉพาะ socket แล้ว แต่ระบบตรวจอนุมัติยังบล็อกคำสั่งด้วยเหตุผล `blocked by policy` จึงต้องให้ผู้ใช้ดำเนินการผ่านเครื่องโดยตรงก่อนทดสอบ browser ต่อ ไม่ reset Docker หรือ volumes หลักฐาน 13 checks เป็นรอบก่อนเกิด outage ไม่ใช่การรับรองว่า engine กำลังพร้อมในขณะนี้
- Docker BuildKit resolve Maven Central ไม่สำเร็จใน environment นี้ ใช้ `-HostBuild` compile source ใหม่บน host แล้วสร้าง runtime images และโหลดเข้า node; ไม่ใช้ images ของ source เก่ามาทดแทน
- Node `24.13.0` รัน tests/build ผ่าน แต่ dependencies บางตัวแจ้ง engine warning ว่าต้องการ `24.15+`; runbook แนะนำ version ใหม่และ hosted workflow ใช้ Node 24 ล่าสุด
- เปลี่ยน Cypress configs เป็น CommonJS `.cjs` เพราะ TypeScript config loader ใน Cypress บน Windows ARM พบ esbuild binary คนละ architecture; Node syntax/ESLint/TypeScript checks ของการแก้ผ่านแล้ว
- GitHub Actions ยังไม่ได้ยืนยัน runtime บน hosted runner และไม่ได้เปลี่ยนการตั้งค่า billing/workflows เดิม
- ผลผ่านนี้เป็น tests ที่มีอยู่และ deployment acceptance เท่านั้น ยังต้อง review business acceptance และ coverage gaps ตามเกณฑ์อาจารย์

## หลักฐานที่ตรวจได้ใน Git

- [Release / image digests](evidence/2026-10-16-local-kubernetes/release.json)
- [Kubernetes acceptance และ runtime image IDs](evidence/2026-10-16-local-kubernetes/kubernetes-acceptance.json)
- [Pods / Services / Ingress / PVC](evidence/2026-10-16-local-kubernetes/kubernetes-resources.txt)
- [Cluster/client versions](evidence/2026-10-16-local-kubernetes/kubernetes-version.json)
- [Frontend coverage totals](evidence/2026-10-16-local-kubernetes/frontend-coverage.json)
- [Backend coverage totals](evidence/2026-10-16-local-kubernetes/backend-coverage.json)
- [API JUnit totals](evidence/2026-10-16-local-kubernetes/api-test-summary.json)

Raw HTML coverage, JUnit และ console logs อยู่ใน ignored runtime/build output ตาม runbook หรือ Actions artifacts เมื่อสั่งรัน workflow ไม่อัปโหลด credentials, kubeconfig หรือฐานข้อมูล
