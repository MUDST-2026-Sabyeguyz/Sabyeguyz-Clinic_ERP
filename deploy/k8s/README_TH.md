# รัน Clinic ERP บน Kubernetes ในเครื่อง

เอกสารส่งงานสำหรับเกณฑ์ที่ผู้ใช้แจ้งวันที่ 16 ตุลาคม 2026: code อ่านง่าย มีมาตรฐาน, E2E, Pipeline as Code, tests/coverage และโค้ดที่รันได้พร้อม README ใน repo รายการ Kubernetes อ้างอิง [context §12](../../docs/context.md) ผลตรวจจริงและข้อจำกัดอยู่ใน [รายงานส่งงาน](../../docs/testing/2026-10-16-Local-Kubernetes.md)

## เตรียมเครื่อง

- Git, Docker Desktop แบบ Linux containers และ `kubectl`
- เปิด Kubernetes ใน Docker Desktop เลือก provisioner **kind** แล้วรอ node Ready
- PowerShell 7.4+ (`pwsh`) สำหรับสคริปต์ทั้ง Windows และ CI Linux (ใช้ native binary pipeline โหลด images)
- ถ้ารัน quality suite: Node.js 24.15+ และ JDK 17+; หากไม่มี Maven สคริปต์ทดสอบจะนำ Maven จาก build image มาใช้ในโฟลเดอร์ runtime
- ต้องมีอินเทอร์เน็ตสำหรับดาวน์โหลด dependencies/images ครั้งแรก แนะนำให้ Docker มี RAM 6–8 GB เมื่อเปิด Jenkins/Compose พร้อมกัน

```powershell
git clone https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP.git
cd Sabyeguyz-Clinic_ERP
kubectl config get-contexts
kubectl --context docker-desktop get nodes
```

สำหรับ branch ส่งงาน ให้ checkout branch/commit ที่ระบุในรายงานก่อนรัน คำสั่งทั้งหมดต่อไปนี้ใช้จาก root ของ repo และไม่ต้องคัดลอก `.env` ของ Compose

## Build และ deploy

```powershell
pwsh -File deploy/k8s/Start-Local.ps1
pwsh -File deploy/k8s/Open-Local.ps1
```

เปิด **http://localhost:18088/** ใน browser และเปิด terminal ที่ port-forward ค้างไว้ หน้าเว็บผ่าน Traefik → Kubernetes Ingress → Frontend/Backend Services → PostgreSQL ไม่ได้เข้าชุด Docker Compose

ชื่อผู้ใช้เริ่มต้น `clinic-admin`; อ่านรหัสเฉพาะในเครื่อง:

```powershell
(Get-Content deploy/k8s/runtime/docker-desktop-credentials.json -Raw | ConvertFrom-Json).MANAGER_PASSWORD
```

รหัสสุ่มถูกสร้างครั้งแรกใน Secret และไฟล์ runtime ที่ Git ignore; สคริปต์ไม่เปลี่ยนรหัสฐานเดิมเมื่อ deploy ซ้ำ และ bootstrap manager ไม่ทับบัญชีที่มีอยู่แล้ว อย่า commit runtime, kubeconfig หรือ credentials

สคริปต์ build images พร้อม source revision label, โหลด images เข้า containerd ของทุก node, สร้าง Secret, apply Kustomize และรอ rollout ทุก Deployment เก็บ tag/image ID ไว้ใน `runtime/release.json` มีการระบุว่ามี application source ที่ยังไม่ commit หรือไม่ด้วย การรันซ้ำสร้าง release tag ใหม่แต่ใช้ PVC เดิม

เครื่องที่ RAM จำกัดหรือ DNS ใน Docker BuildKit มีปัญหา ใช้ build จาก source บน host แล้วบรรจุเป็น runtime images ใหม่ โดยต้องมี Node.js 24.15+ และ JDK 17+ (Maven เตรียมอัตโนมัติ):

```powershell
pwsh -File deploy/k8s/Start-Local.ps1 -HostBuild
```

HostBuild คัดลอก source backend ไป workspace ใหม่ใน runtime แล้ว package ใหม่ และรัน `npm ci`/production build ของ frontend ก่อนสร้าง images จึงไม่ใช้ jar หรือ bundle เก่ามาแทน source ที่เลือก โหมดนี้ใช้ Kubernetes probes ตรวจ health และระบุ `hostBuild: true` ในหลักฐาน release ส่วน GitHub CI ใช้ multi-stage Dockerfiles ตามปกติ

ถ้าใช้ Minikube แทน Docker Desktop:

```powershell
minikube start --driver=docker --memory=6144
pwsh -File deploy/k8s/Start-Local.ps1 -Context minikube
pwsh -File deploy/k8s/Open-Local.ps1 -Context minikube
```

Minikube path เตรียมไว้แต่ต้องแยกผลทดสอบจาก Docker Desktop ที่ใช้ตรวจส่งงาน ไม่ต้องเปิด minikube เพิ่มถ้า Docker Desktop cluster พร้อมอยู่แล้ว สคริปต์ยอมรับเฉพาะ local contexts ที่ระบุ ไม่เปลี่ยน current context และไม่ deploy ไป cluster อื่น

## Tests และ coverage

เปิดอีก terminal รันทดสอบระบบที่ deploy จริง:

```powershell
pwsh -File deploy/k8s/Test-Local.ps1 -TestPersistence
```

ตรวจสุขภาพผ่าน Ingress, Vue fallback, anonymous API 401, login จริง, สร้างผู้ป่วยสังเคราะห์และอ่านกลับ จากนั้นแทนที่ PostgreSQL pod และ restart backend เพื่อยืนยันข้อมูลยังอยู่ใน PVC สร้างสาขาสังเคราะห์รหัส `99` และผู้ป่วยชื่อ `K8S... Synthetic` ใน namespace นี้เท่านั้น ไม่ใช้ข้อมูลผู้ป่วยจริง ผลที่ไม่มีรหัส/token อยู่ใน `runtime/acceptance.json`

เมื่อ `Open-Local.ps1` ยังรันอยู่ ให้ทดสอบ browser บน production images เดียวกัน:

```powershell
cd frontend
npm ci
$env:CYPRESS_BASE_URL = 'http://127.0.0.1:18088'
$env:CLINIC_K8S_CREDENTIALS = (Resolve-Path ../deploy/k8s/runtime/docker-desktop-credentials.json).Path
npm run test:e2e:k8s
cd ..
```

ชุดนี้ไม่ reset ฐานคลินิก ตรวจหน้า login, invalid login และ manager login → หน้า patient → refresh โดยใช้ API จริง ใช้ `Test-Local.ps1` สร้าง fixture สังเคราะห์ก่อน

รัน quality suite และ browser regression เดิมทั้งหมดบนฐานทดสอบแยก:

```powershell
pwsh -File scripts/Test-Submission.ps1
```

ชุดนี้ตรวจ ESLint, TypeScript/build, Vitest/V8 coverage, backend Cypress/H2 regression, v2 API/PostgreSQL พร้อม JaCoCo และ frontend v2 browser E2E เปิดฐานชั่วคราวที่ `127.0.0.1:15439`, test API `18080`, JMX `19001`, Vite `5173` จึงต้องให้พอร์ตเหล่านี้ว่างก่อน สคริปต์หยุดเฉพาะ process/container ที่ตนสร้างเมื่อจบ ไม่ใช้ฐาน Kubernetes หรือ Compose ในการ reset fixtures

อ่านรายงาน coverage:

| รายงาน | ตำแหน่ง |
|---|---|
| Frontend HTML | `frontend/coverage/index.html` |
| Frontend summary | `frontend/coverage/coverage-summary.json` |
| Backend HTML/XML | `backend/target/site/jacoco/index.html`, `jacoco.xml` |
| Backend Cypress JUnit | `backend/cypress/results/` |
| Kubernetes browser JUnit | `frontend/cypress/results/k8s-*.xml` |

Frontend coverage รวม `src/**/*.ts,vue` ยกเว้น tests, declarations และ entry point Backend JaCoCo วัดเฉพาะ production package `com.sabyeguyz.clinicv2` จาก HTTP tests ใน JVM จริง รายงานของ H2 และ v2 สามารถสะสมในรอบเดียวโดย `test:v2:coverage` ไม่ clean target หลังจบรอบแรก Coverage เป็นค่าที่วัดได้ ไม่ได้ตั้งเกณฑ์ผ่านแทนอาจารย์ และไม่แปลว่าทุก business flow ผ่านแล้ว

## Pipeline as Code บน GitHub

ไฟล์ [kubernetes-ci.yml](../../.github/workflows/kubernetes-ci.yml) ทำ quality/tests/coverage → build images → สร้าง kind cluster ชั่วคราว → deploy → health/authenticated/PVC tests → browser E2E → upload artifacts โดยไม่อัปโหลด credentials

รองรับ PR เข้า develop, push develop/codex branches และ manual `workflow_dispatch` ใช้ GitHub-hosted runner จึงไม่ต้องส่ง kubeconfig เครื่องส่วนตัวขึ้น GitHub หากบัญชีติด billing หรือ workflow ถูก disable ต้องแก้สถานะนั้นก่อนรันบน GitHub ผล local ไม่ใช่ผล GitHub Actions การใช้ Jenkins ย้อนหลังเดิมยังเป็นอีกชุดหนึ่ง

## ตรวจสถานะ / logs / rollback / หยุด

```powershell
kubectl --context docker-desktop -n clinic-local get pods,svc,ingress,pvc
kubectl --context docker-desktop -n clinic-local get events --sort-by=.lastTimestamp
kubectl --context docker-desktop -n clinic-local logs deployment/backend --tail=100
kubectl --context docker-desktop -n clinic-local logs deployment/clinic-traefik --tail=50
kubectl --context docker-desktop -n clinic-local rollout history deployment/backend
kubectl --context docker-desktop -n clinic-local rollout undo deployment/backend
kubectl --context docker-desktop -n clinic-local rollout status deployment/backend
```

Rollback image ไม่ rollback Flyway/schema ต้องประเมินความเข้ากันได้ของ migration ก่อน rollback แอปจริง

หยุด port-forward ด้วย Ctrl+C หยุด pods โดยเก็บ PVC/Secret:

```powershell
kubectl --context docker-desktop -n clinic-local scale deployment/backend deployment/frontend deployment/postgres deployment/clinic-traefik --replicas=0
```

เปิดต่อด้วย replicas=1 หรือรัน Start-Local ใหม่ ห้ามลบ namespace/PVC หรือ reset Docker Kubernetes ถ้าต้องการเก็บข้อมูล

## ปัญหาที่พบบ่อย

| อาการ | วิธีแก้ |
|---|---|
| `Temporary failure in name resolution` ระหว่าง Docker build | ตรวจ DNS/VPN; ใช้ `Start-Local.ps1 -HostBuild` เพื่อ build dependencies บน host หรือทดสอบ `-BuildNetwork host` ถ้าเครื่องรองรับ |
| `ImagePullBackOff` | เช็ก image tag และการ import; อย่าใช้ `latest` จาก daemon อื่นโดยคาดว่า Kubernetes เห็นเอง |
| PVC Pending | `kubectl get storageclass` ต้องมี default dynamic provisioner และตรวจ events |
| `address already in use` | เปลี่ยน `Open-Local.ps1 -Port 18090` แล้วตั้ง Cypress base URL ตามพอร์ตนั้น |
| Login ไม่ผ่านหลัง deploy ซ้ำ | ใช้บัญชีเดิมของฐานนี้ ไม่ใช้บัญชี Jenkins และไม่สุ่ม Secret ใหม่เพื่อแก้ |
| ไม่มีไฟล์ credentials แต่ Secret มีอยู่ | คืนไฟล์ runtime เดิมจากสำรองส่วนตัว ไม่ลบ Secret หรือ PVC |

อ้างอิง: [Kubernetes probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/), [Traefik Kubernetes Ingress](https://doc.traefik.io/traefik/reference/install-configuration/providers/kubernetes/kubernetes-ingress/), [Docker Desktop Kubernetes](https://docs.docker.com/desktop/use-desktop/kubernetes/)
