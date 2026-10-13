# คู่มือ Present วันที่ 17 ตุลาคม 2026

## ลำดับการนำเสนอ

| ลำดับ | สิ่งที่พูดและเปิดให้ดู | ผู้รับผิดชอบ/หลักฐาน |
|---|---|---|
| 1 | Project Clinic ERP ทำอะไร: งานผู้ป่วย นัดหมาย การขาย/คอร์ส นักกายภาพ และรายงาน; เปิด flow ที่ทดสอบแล้ว | ทั้งทีม; เลือกเฉพาะ flow ที่พร้อม |
| 2 | เปิด GitHub repo, `develop`, latest commit และ PR; อธิบายแต่ละคนทำส่วนไหนจาก commit/diff จริง | ทั้งทีม; SHA ของ code ที่นำเสนอ |
| 3 | Unit tests, integration tests, E2E และ coverage | Backend owner + DevOps; Surefire, JaCoCo, PostgreSQL smoke, Cypress |
| 4 | Runner รับงานอะไร: Linux build/test/publish และ Windows เข้าถึง local Kubernetes | Natthapumin; Actions jobs และ runner online |
| 5 | เปิด deployment file, image digest, StatefulSet/PVC, Service, Ingress แล้วเปิดเว็บที่ deploy ผ่าน CI/CD | Natthapumin; `deployment-checkpoint.json` และเว็บเดโม |

เวลาที่เสนอ: 2 + 2 + 3 + 2 + 4 นาที หากเวลาน้อย ให้ใช้ run ที่ผ่านแล้วประกอบการอธิบาย อย่ารอ pipeline เต็มระหว่างพรีเซนต์

## เปิดเครื่องก่อนซ้อม/พรีเซนต์

เปิด Docker Desktop รอ engine พร้อม แล้วเปิด PowerShell จาก `C:\Users\Natth\Documents\3-1MU\devops`

```powershell
& .\tools\minikube\v1.39.0\minikube-windows-amd64.exe start -p clinic-phase1
kubectl --context clinic-phase1 -n clinic-erp-demo-20261017 get pods,deployments,statefulsets,svc,ingress,pvc
```

Pod ของ backend/frontend/PostgreSQL ต้อง Ready ก่อนเดโม ถ้ายังไม่มี namespace หรือ images ของ SHA ล่าสุด ต้องใช้ pipeline deploy ให้ผ่านก่อน

Self-hosted runner ที่ตั้งไว้ต้อง online หากยังไม่ทำงาน เปิดอีก PowerShell แล้วรัน:

```powershell
Set-Location C:\Users\Natth\Documents\3-1MU\devops\tools\actions-runner
.\run.cmd
```

ถ้า runner online อยู่แล้วไม่ต้องเปิดซ้ำ Jobs บน GitHub-hosted runner ทำงานได้แม้เครื่องนี้ปิด แต่ job deploy local cluster จะรอ runner

## เปิดเว็บเดโมผ่าน Ingress

เปิดอีก PowerShell จาก checkout ที่มี scripts:

```powershell
Set-Location C:\Users\Natth\Documents\3-1MU\devops\presentation-devops-20261017
.\scripts\deploy\open-demo.ps1
```

เปิด [http://localhost:16969/sabyeguyz/](http://localhost:16969/sabyeguyz/) คำสั่งนี้ forward ไป Service ของ Traefik จึงยังวิ่งผ่าน Ingress และ prefix middleware ไม่ได้ forward ตรงไป frontend

URL สำหรับเปิด profile หลัง login คือ `/sabyeguyz/staff/patients/<id>` ต้อง refresh แล้วได้หน้าเดิม assets ต้องโหลดจาก `/sabyeguyz/assets/` และ API ใช้ `/sabyeguyz/api/`

บัญชีเดโมเป็นบัญชีสังเคราะห์จาก migration ปัจจุบัน ดู dev accounts ใน `docs/Phase-Implementation-Report.md` ไม่ใช้บัญชีจริงของคลินิก

## คำสั่งสำหรับโชว์ Kubernetes

```powershell
kubectl --context clinic-phase1 -n clinic-erp-demo-20261017 get pods --show-labels
kubectl --context clinic-phase1 -n clinic-erp-demo-20261017 get deployments,replicasets
kubectl --context clinic-phase1 -n clinic-erp-demo-20261017 get statefulsets,pvc
kubectl --context clinic-phase1 -n clinic-erp-demo-20261017 get services,ingress
kubectl --context clinic-phase1 -n clinic-erp-demo-20261017 get deployment clinic-demo-backend -o 'jsonpath={.spec.template.spec.containers[0].image}'
kubectl --context clinic-phase1 -n clinic-erp-demo-20261017 get deployment clinic-demo-frontend -o 'jsonpath={.spec.template.spec.containers[0].image}'
```

อธิบายว่า Deployment ควบคุม ReplicaSet/Pods, Service ให้ address ที่คงที่, StatefulSet เก็บ identity ของ DB, PVC เก็บข้อมูล และ Ingress รับ HTTP path

Backend ใช้ `Recreate` เพื่อมี startup migration runner คนเดียว จึงมีช่วงหยุด API สั้น ๆ Frontend ใช้ RollingUpdate การ restart Pod และคงข้อมูลอยู่ใน PVC เป็นคนละหลักฐานกับ backup/restore

## เปิด commit และผลงานทีม

```powershell
git fetch origin
git log origin/develop --oneline -12
git shortlog -sn origin/develop
git log origin/develop --format='%h %ad %an %s' --date=short -- .github/workflows compose.yaml deploy scripts
```

เลือก commit ของแต่ละคนแล้วเปิด diff เพื่ออธิบายงาน จำนวน commit เป็นข้อมูลประกอบ ต้องดูเนื้อหาด้วย และ merge commit อาจรวมงานของคนอื่น

ใช้ PR และ commit ของงาน DevOps รอบนี้เป็นหลักฐานว่าเพิ่ม runner/deploy/docs ส่วน historical release บน `xan`/`backup/devops-phase-1` เมื่อ 8 ต.ค. เป็นอีกชุดหนึ่ง อย่านำมาอ้างว่าทดสอบ develop ล่าสุดแล้ว

## เชื่อมกับ Lab Week 8 และ Week 9

| เนื้อหาใน Lab | ส่วนที่โชว์ใน project | ขอบเขต |
|---|---|---|
| Week 8 Pods, labels/selectors, health checks, volumes, ClusterIP/NodePort | Pods/labels/probes, Services/PVC ใน manifest | นำ concept มาใช้กับ project ไม่จำเป็นต้องสร้าง nginx ตัวอย่างทุกตัวซ้ำใน app |
| Week 9 Deployment และ ReplicaSet | frontend/backend Deployment และ rollout | frontend RollingUpdate; backend Recreate พร้อมอธิบายเหตุผล |
| Week 9 StatefulSet และ persistent storage | PostgreSQL StatefulSet + headless Service + PVC | ตรวจ DB ด้วย API จริงและ persistence |
| Week 9 หน้า 29: service + DB, API ใช้ DB, localhost/serviceName ผ่าน Ingress | `/sabyeguyz/` และ `/sabyeguyz/api/` | หลักฐานจาก pipeline/deploy |
| Week 9 หน้า 29: project ของเพื่อนใน namespace ใหม่พร้อม DB/Ingress | ยังต้องใช้ source ของอีก project ที่ไม่ใช่ Clinic ERP | ยังไม่มี source ให้รอบนี้ จึงยังไม่ถือว่า Lab ครบทั้ง 4 ข้อ |

CI/CD และ backend coverage เป็นงานที่ผู้ใช้ขอเพิ่มสำหรับพรีเซนต์ ไม่ใช่ข้อกำหนด coverage ที่ระบุใน Lab PDF

## แผนก่อนวันที่ 17 ต.ค.

| วันที่ | เป้าหมาย |
|---|---|
| 13 ต.ค. | ทำ pipeline/manifest/docs และทดสอบบนฐาน develop ปัจจุบัน |
| 14 ต.ค. | ทีมตกลงการรวม DB PR #23; ทดสอบ API contract และปรับ fixtures ถ้ารวม |
| 15 ต.ค. | CI และ deploy จาก SHA หลังรวม; เก็บ coverage และ financial-flow evidence เพิ่ม |
| 16 ต.ค. | ซ้อมทั้งหมด เปิด runner/cluster/Ingress ให้พร้อม; เตรียม screenshot และ run links สำรอง |
| 17 ต.ค. | ตรวจ SHA/images/Ready และใช้ข้อมูลสังเคราะห์ในการพรีเซนต์ |

ก่อนพูดว่า “ส่ง develop พร้อมแล้ว” ต้องมี PR รวมจริงและ CI ผ่านบน SHA หลังรวม ก่อนพูดว่า “Lab Week 9 ครบ” ต้องมี project ของเพื่อนตามหน้า 29 ด้วย
