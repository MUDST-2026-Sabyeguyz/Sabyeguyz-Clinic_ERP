# GitHub Actions → Docker Desktop Kubernetes (manual only)

## ขอบเขตและสถานะ

Workflow `Manual Kubernetes deployment` ใช้ Windows self-hosted runner ที่มี label `clinic-k8s-deploy` รันเฉพาะ branch `feat/github-action-to-kurbernetes` และรับ source SHA เต็มที่เป็น ancestor ของ branch นี้เท่านั้น ไม่มี push/PR trigger และไม่รันโค้ด PR อัตโนมัติ

เป้าหมายตายตัวคือ namespace `clinic-actions-demo`, ฐาน `clinic_actions_demo`, PVC `postgres-data` ใน namespace นี้ และ image `clinic-actions-backend/frontend:<SHA12>-<run>-<attempt>` แยกจาก Jenkins/clinic-present ทั้งหมด ใช้บัญชี Manager bootstrap สุ่ม ไม่ใช้ seed หรือข้อมูลผู้ป่วยจริง

Actions ระดับ repository เปิดแล้วหลัง Bootstrap PR #112 เข้า main; workflows เก่าอื่นยังปิดอยู่ PR #111 ยังไม่ merge

## Pipeline

1. ตรวจ input SHA และ membership ของ trusted deployment branch จาก full history แล้ว checkout detached SHA นั้น
2. ตรวจ Node 24; frontend npm ci → Vitest → typecheck/build
3. Backend npm ci → Maven package → Cypress API จริง ต้องมีผล XML มากกว่า 0 tests และไม่มี failure ชุดนี้เป็น legacy API suite บน H2 ไม่ใช่หลักฐาน V2/483 AC
4. Build images linux/amd64 พร้อม source revision label
5. Trivy สแกน HIGH/CRITICAL โดย exit code 1 เมื่อพบ ไม่ใช้ ignore-unfixed หรือ continue-on-error
6. Import backend/frontend/Postgres เข้า containerd ของ Docker Desktop kind ทุก node ที่ admin บันทึกไว้ หาก node ไม่เป็น visible Docker container จะ fail ชัดเจน ไม่สมมติว่า build แล้ว Kubernetes เห็น image เสมอ
7. สร้าง ConfigMap/Deployment Postgres และรอ readiness; รัน Flyway เป็น Job เฉพาะ (ไม่มีเว็บ/seed/bootstrap ใน Job)
8. Migration สำเร็จจึง apply backend/frontend โดยแอปปิด automatic Flyway; รอ rollout
9. ตรวจ source annotation/image tag/resolved pod image ID และ frontend health, manager login, authenticated read, anonymous API 401
10. บันทึก release/evidence เฉพาะเมื่อผ่านครบ หาก scan/test/migration ล้มจะไม่รายงาน deployment สำเร็จ ไม่มี no-op ที่รายงาน Ready แทนการ deploy

## Dependency remediation และผลทดสอบ

Baseline run 43311257876 พบ 11 Critical และ 33 High ใน Java dependencies รายงานครบและ dependency path อยู่ใน [ตาราง remediation](../actions-dependency-remediation.md) อัปเกรด Spring Boot BOM เป็น 4.1.2 เพื่อรับ Spring Framework 7 fixes ที่มีให้แบบ OSS พร้อมเพิ่ม Springdoc 3.2.0, Flyway starter และ Jackson 2 compatibility module สำหรับ API code เดิม; request models ที่ใช้ JsonNode เปลี่ยนเป็น Jackson 3 โดยตรงตาม module ปัจจุบัน ส่วน frontend อัปเกรด libxslt ใน nginx image

ตรวจ dependency tree ที่ resolve แล้ว, frontend 153 tests/build/typecheck, legacy API Cypress 434/434 ผ่านบนพอร์ต 18083 และ Trivy local HIGH/CRITICAL ผ่านทั้งสอง images ใช้ฐาน vulnerability ปัจจุบันและ scanner digest ที่ pin ใน workflow รายงานก่อน/หลัง: [Trivy evidence](evidence/README.md). Maven พบ 0 JUnit tests จึงไม่นับ `BUILD SUCCESS` เป็น JUnit test pass

V2 Cypress ผ่าน 299/309 ในรอบแรกก่อน JSON compatibility fix; หลังแก้ request JsonNode, appointment/patient targeted specs ผ่านครบ (15 patient tests, 7 appointment workflow tests). สองกรณี commission/audit ที่ยังล้มเกิดซ้ำเหมือนกันบน baseline SHA 660836a0 ใน PostgreSQL database แยก จึงเป็น known pre-existing failures ไม่ใช่ผลยืนยันว่า V2 suite ทั้งหมดผ่าน

สถานะ migration/rollout/authenticated smoke จะอ้างเฉพาะ GitHub Actions run ที่กดจาก PR branch หลัง commit remediation นี้เท่านั้น

## เตรียมเครื่อง (ทำแยกจาก workflow)

ต้องมี Docker Desktop แบบ kind Kubernetes (visible node containers), kubectl, JDK 17+ / Maven, Node 24, Python, PowerShell 7 และ gh ที่ login มีสิทธิ์ลงทะเบียน runner แนะนำบัญชี Windows แยกสำหรับ runner; Docker access เป็นสิทธิ์สูงบน host แม้ Kubernetes RBAC จะจำกัด namespace แล้วก็ตาม

```powershell
# รันด้วย cluster administrator ที่มีอยู่: สร้างเฉพาะ namespace ใหม่, PVC, Secret, Role/SA/Binding
./scripts/actions/Provision-Environment.ps1

# ดาวน์โหลด runner จาก official API ตรวจ SHA256 แล้วลงทะเบียนผ่าน short-lived registration token
./scripts/actions/Install-Runner.ps1 -Node24Directory '<absolute-path-to-node24>'
./scripts/actions/Start-Runner.ps1
```

ข้อมูลลับอยู่ใน `%LOCALAPPDATA%/ClinicActionsDeploy` และ runner ใน `%LOCALAPPDATA%/ClinicActionsRunner` เท่านั้น ACL จำกัดผู้ใช้ Windows ปัจจุบัน ไม่มีการ commit kubeconfig/token/password และไม่แนบ runtime folder เป็น artifact

Provisioning สร้าง Kubernetes TokenRequest อายุ 4 ชั่วโมง (ดู expiration ที่ server คืนจริง) runner ไม่มี admin kubeconfig และไม่มีสิทธิ์อ่าน Secrets/Nodes หรือสร้าง RoleBindings ส่วน pipeline ต้องเหลืออายุ token อย่างน้อย 65 นาที ให้ admin เรียก provisioning ซ้ำเพื่อ renew ก่อน run หากหมดอายุ ห้ามเพิ่ม cluster-admin ให้ runner เพื่อหลบขั้นนี้

Role อนุญาตจัดการ deployments/configmaps/services/jobs และอ่าน pods/logs/events/port-forward เฉพาะ namespace ของงานนี้ PVC/Secrets/RBAC เป็นหน้าที่ provisioning ไม่ใช่ workflow ไม่มีการติดตั้ง service อัตโนมัติ; หลัง restart เครื่องให้ start runner ใหม่ด้วยสคริปต์ หยุด listener เมื่อไม่มี deploy ทำงาน:

```powershell
./scripts/actions/Stop-Runner.ps1
```

## Bootstrap และกดรันจริง (ยังต้องอนุมัติ)

1. ตรวจ main-bootstrap PR ที่มีเพียง workflow entrypoint + actionlint label config ไม่ย้ายแอปจาก develop เข้า main
2. เมื่อผู้ดูแลอนุมัติและ merge bootstrap แล้ว จึงเปิด repository Actions และเปิดเฉพาะ workflow ใหม่นี้ ตรวจรายการอื่นให้ยัง disabled_manually เช่นเดิม รวมทั้งประเมิน Dependabot Updates ซึ่งปิดแบบ workflow ปกติไม่ได้
3. เปิด GitHub Actions → Manual Kubernetes deployment → Run workflow → เลือก branch งาน (ไม่เลือก main) และกรอก SHA เต็มที่มี implementation นี้

```powershell
gh workflow run github-actions-kubernetes.yml --repo MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP --ref feat/github-action-to-kurbernetes -f source_sha=<40-character-SHA>
```

ไฟล์ workflow บน main มีหน้าที่ให้ GitHub รู้จัก manual entrypoint; เวลารันใช้ `--ref` branch งานและ checkout source SHA ที่อนุญาต สคริปต์ทุกตัวมาจาก SHA เดียวกัน ไม่อ้าง path ที่ไม่มีบน main/branch นั้น ไม่มีการเปิด namespace/ref อิสระให้ผู้ใช้ส่ง shell command

## เปิดแอปและ rollback

หลังมี deployment ที่ผ่านจริงแล้ว:

```powershell
$env:KUBECONFIG = "$env:LOCALAPPDATA/ClinicActionsDeploy/runner.kubeconfig"
kubectl -n clinic-actions-demo port-forward --address=127.0.0.1 service/frontend 18190:80
```

เปิด `http://127.0.0.1:18190` บัญชีอยู่ใน `credentials.json` ของ runtime ส่วนตัว อย่าเปิดเผยไฟล์นี้หรือใส่รหัสผ่านในสไลด์ นี่เป็น URL ในเครื่อง ไม่ใช่ public URL และยังใช้ไม่ได้ก่อน deploy สำเร็จ

ก่อน deploy เก็บ previous-release.json ของ images เดิมไว้ หากต้อง rollback ให้ตรวจ schema backward compatibility ก่อน:

```powershell
./scripts/actions/Rollback-Application.ps1 -PreviousReleaseFile '<downloaded-artifact>/previous-release.json' -WhatIf
# ตรวจแล้วจึงรันโดยไม่ใส่ -WhatIf
```

Rollback เปลี่ยนเฉพาะ image ของแอป ไม่ย้อน schema/data, ไม่ลบ PVC และไม่เปลี่ยน source annotation ให้เป็น release ที่ผ่าน verification ใหม่ ต้องตรวจ health/auth/data compatibility และบันทึกสถานะหลัง rollback แยกต่างหาก หากไม่มี release เดิมจะไม่มี image ให้ rollback

## นำเสนอ

แสดง source SHA ใน GitHub → manual run → runner name → tests/build/scan → migration/rollout → pods/imageID → login/read smoke และ artifacts ต้องใช้ run จริงเท่านั้น ไม่ใช้ local validation หรือ dry-run อ้างว่า GitHub deployment ผ่านแล้ว

## Deployment จริงหลังแก้ dependencies

GitHub Actions [run 43318023222](https://github.com/MUDST-2026-Sabyeguyz/Sabyeguyz-Clinic_ERP/actions/runs/43318023222) สำเร็จครบทุกขั้นจาก source SHA `ab5b9e349ac44bd0c5332929d1c8f00cf7ac70c4` ใน namespace `clinic-actions-demo`:

- Frontend 153 tests และ typecheck/build ผ่าน; backend API Cypress 17 XML / 434 executed, failures 0, errors 0, skips 0
- Trivy backend และ frontend: HIGH/CRITICAL 0 ทั้งคู่ (baseline ก่อนแก้: 33 HIGH + 11 CRITICAL ใน backend; frontend ยังไม่ได้สแกนใน run เดิม)
- Image tag: `ab5b9e349ac4-43318023222-1`; backend/frontend Deployment annotations ตรง full source SHA และ image IDs ถูกตรวจใน smoke
- Flyway Job `migrate-ab5b9e349ac4-43318023222-1` สำเร็จ 1/1, ใช้ migrations 12 รายการ ถึง v1008; backend/frontend/Postgres Ready 1/1
- Frontend health 200, Manager login และ protected read 200, anonymous protected API 401
- Artifact `clinic-actions-release-43318023222-1` มี reports/manifests/smoke และไม่มี kubeconfig หรือค่า credential ตรวจเทียบกับไฟล์ runtime แล้ว
- หลัง run ยืนยัน Jenkins container ยังทำงาน และ clinic-present backend 1/1, frontend 2/2

เปิดแอปจากเครื่องนี้ที่ **http://127.0.0.1:18190** (local port-forward; ไม่ใช่ public URL) บัญชีที่สุ่มให้การ deploy อยู่ใน `%LOCALAPPDATA%\ClinicActionsDeploy\credentials.json`; อ่านรหัสผ่านจากเครื่องที่ provision เท่านั้น อย่าแนบไฟล์หรือรหัสผ่านในเอกสาร/แชท

PR #111 ยังเปิดอยู่และยังไม่ merge
