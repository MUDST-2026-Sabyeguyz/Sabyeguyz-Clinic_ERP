# คู่มือ Jenkins CI/CD สำรองในเครื่อง

ชุดนี้ใช้ `develop` เพื่อ build/test และ deploy แอปทดสอบในเครื่อง โดยไม่ใช้โควตา GitHub Actions ไม่ใช่ production deployment

## สิ่งที่ต้องเตรียม

- Docker Desktop เปิดอยู่และใช้ Linux containers
- Git และสิทธิ์อ่าน repository นี้
- Windows PowerShell; แนะนำ RAM อย่างน้อย 8 GB และพื้นที่ว่างสำหรับ images/dependencies
- พอร์ต 8085, 8090 และ 8091 ว่าง เครื่องต้องเปิดไว้ระหว่าง build และใช้งานแอป

Docker สำหรับงาน Jenkins เป็น daemon แยกใน privileged container ไม่ mount Docker socket ของเครื่อง และไม่เปิดพอร์ต API 2375 ออก host เก็บ Jenkins และฐานข้อมูลแอปไว้ใน volumes แยกจากระบบเดิม

## เริ่มใช้ครั้งแรก

เปิด PowerShell ใน root ของ repo ที่ clone แล้ว:

```powershell
cd .\jenkins-backup
.\Refresh-Source.ps1
docker compose up --build -d
docker compose exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

1. เปิด http://localhost:8085 ใส่รหัสปลดล็อกที่ได้ ห้ามส่งรหัสนี้ในแชตหรือ commit
2. เลือก Install suggested plugins แล้วสร้างบัญชี admin และตั้ง URL เป็น http://localhost:8085/
3. เข้า Clinic-backup ที่สร้างไว้ หากยังไม่เห็น ให้รัน `docker compose restart jenkins` หลัง setup เสร็จ
4. กด Build Now ครั้งแรก; ครั้งถัดไปใช้ Build with Parameters
5. ดูเลข build → Console Output, Test Result และ Artifacts เพื่ออ่านผล

Pipeline และ Git plugins ติดตั้งไว้ใน image; recommended plugins เพิ่มหน้า Test Result และส่วนเสริมทั่วไป

## Pipeline ทำอะไร

1. Checkout snapshot ของ `develop` หรือดึงจาก GitHub ตามค่าที่เลือก แล้วบันทึก SHA
2. Backend: `mvn verify` ตาม workflow ของ develop
3. Frontend: `npm ci`, unit tests และ production build
4. สร้าง backend/frontend images โดยตั้ง tag เป็น SHA
5. เปิด PostgreSQL/backend/frontend ชั่วคราวด้วย Compose และรอ healthy จากนั้นรัน Cypress ด้วย Chromium
6. ถ้าทุกขั้นผ่านและ DEPLOY=true จะ deploy image ตาม SHA เดียวกับที่ทดสอบไปยังแอปทดสอบในเครื่อง
7. ตรวจ health หลัง deploy เก็บ test reports, screenshots, SHA และ logs แล้วล้างเฉพาะฐาน acceptance ชั่วคราว

Backend tests ปัจจุบันของ develop ใช้ H2 ส่วน Compose/E2E ตรวจ runtime บน PostgreSQL จริง ชุด E2E ปัจจุบันตรวจหน้า login ไม่ได้ยืนยันทุก business flow ถ้า tests ล้มเหลวจะไม่ไปขั้น deploy; ถ้า deploy ล้มเหลวให้ตรวจ logs ไม่มีการ rollback migration อัตโนมัติ

## ทำงานรอบถัดไป

ในโฟลเดอร์ jenkins-backup รัน:

```powershell
.\Refresh-Source.ps1
```

จากนั้น Clinic-backup → Build with Parameters:

| ตัวเลือก | การใช้ |
|---|---|
| LOCAL_SNAPSHOT=true | ใช้ develop ที่ Refresh-Source.ps1 ดึงล่าสุดผ่านสิทธิ์ Git ในเครื่อง |
| LOCAL_SNAPSHOT=false | ดึง develop จาก GitHub โดยตรงด้วย credential ใน Jenkins |
| GIT_CREDENTIAL_ID | ID ของ credential สำหรับอ่าน private repo ค่าเริ่มต้น github-read |
| DEPLOY=true | deploy หลังตรวจผ่าน |
| DEPLOY=false | ตรวจอย่างเดียว |

อย่ารัน Refresh-Source.ps1 พร้อมกับขั้น checkout ของ build เดิม Snapshot ไม่รวมไฟล์แก้ที่ยังไม่ push และไม่เปลี่ยน branch ปัจจุบันของเครื่อง

กรณี PowerShell บล็อก script ตามนโยบายของเครื่อง ให้ตรวจนโยบายกับผู้ดูแล ไม่ต้องปิดนโยบายทั้งเครื่องเพื่อใช้ Jenkins

## เชื่อม GitHub private repo โดยตรง

Manage Jenkins → Credentials → System → Global credentials → Add Credentials:

- Kind: Username with password
- Username: GitHub username ของผู้มีสิทธิ์อ่าน repo
- Password: token ที่จำกัดสิทธิ์อ่าน Contents ของ repo นี้
- ID: github-read

จากนั้นเลือก LOCAL_SNAPSHOT=false ใน build ห้ามฝัง token ใน Jenkinsfile หรือ source code หากองค์กรใช้ SSO ต้องอนุญาต token ตามนโยบายองค์กร
ชุดนี้เป็น manual build ยังไม่มี webhook/polling; snapshot mode ต้อง refresh ก่อนทุกครั้งที่ต้องการ source ใหม่

## เข้าแอปที่ deploy

- แอป: http://localhost:8090
- Backend health: http://localhost:8091/actuator/health
- Jenkins: http://localhost:8085

ใช้บัญชีของแอปตามขั้นตอน provision ของ develop; บัญชี Jenkins เป็นคนละบัญชีกับแอป ชุดนี้ไม่ได้สร้างรหัสผ่านแอปหรือย้ายข้อมูลจากฐานเดิม

Secret ของฐาน deploy/JWT สุ่มครั้งแรกและเก็บใน `/var/jenkins_home/clinic-deploy/local.env` ภายใน Jenkins volume ไม่อยู่ใน Git/artifacts ห้ามเปลี่ยนรหัสฐานในไฟล์นี้โดยไม่เปลี่ยนรหัส PostgreSQL ที่ initialize แล้วด้วย

## หยุดและเปิดต่อ

```powershell
docker compose stop
docker compose start
```

ข้อมูล Jenkins อยู่ `clinic-jenkins-backup_jenkins-home`; images และข้อมูลฐาน deploy อยู่ใน `clinic-jenkins-backup_docker-data` โดยฐานแอปชื่อ `clinic-local_app-data` ภายใน daemon แยก
ห้ามใช้ `docker compose down -v` หรือ prune volumes หากต้องการเก็บข้อมูลและประวัติ รอบ deploy ปกติไม่ลบฐานแอป
การ restart Jenkins จะ sync pipeline Clinic-backup จาก Jenkinsfile นี้และไม่เริ่ม build อัตโนมัติ

## แก้ปัญหา

| อาการ | วิธีตรวจ |
|---|---|
| Checkout authentication failed | ตั้ง github-read credential หรือใช้ snapshot แล้ว refresh |
| develop.bundle not found | รัน Refresh-Source.ps1 ในโฟลเดอร์นี้ |
| Docker daemon not ready | ดู `docker compose logs --tail 100 docker` แล้วรอ daemon พร้อมก่อน build ใหม่ |
| พอร์ตชน | ปิดชุด Jenkins เดิมหรือบริการที่ใช้พอร์ตเดียวกันก่อน ห้ามเปิดสองชุดพร้อมกันด้วย project name เดียวกัน |
| Pipeline/test/deploy แดง | อ่าน stage ที่ล้มและ Console Output/Artifacts ไม่ข้าม tests เพื่อให้ deploy ผ่าน |
| แอปเข้าไม่ได้ | ดู health และใน Jenkins container ใช้ `docker ps` / `docker logs` ของ clinic-local |

## หลักฐานเดิมและข้อจำกัด

ชุด pipeline ก่อนนำเข้า repo รัน Jenkins build #3 ผ่านบน develop `c62d68b07aae805d9b79492bbd6be8c5eee1cdab`: Maven 221/221, frontend unit/build, E2E login 2/2 และสามบริการ deploy healthy หลักฐานนี้เป็น local run ของ SHA นั้น ไม่ใช่ GitHub Actions หรือการรับรอง source ใหม่หลัง merge

อ้างอิง: https://www.jenkins.io/doc/book/installing/docker/ และ https://www.jenkins.io/doc/book/pipeline/
