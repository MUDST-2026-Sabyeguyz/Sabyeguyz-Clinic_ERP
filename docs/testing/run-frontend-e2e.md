# รันเทสต์ Cypress E2E ของ Frontend ด้วยสคริปต์เดียว

สคริปต์ `scripts/run-frontend-e2e.ps1` เตรียมระบบทดสอบแยกต่างหากให้ครบ รันเทสต์ Cypress ทั้ง 3 ชุด (Patients, Manager,
Therapist) ทีละชุด สรุปผล แล้วปิดสิ่งที่ตัวเองเปิด

```
Chrome (Cypress) → Vite :5173 → backend เทสต์ :18080 → PostgreSQL 16 :15434 (ฐานข้อมูล patients_bcc)
```

> **ทำไมต้องแยกระบบ:** เทสต์เรียก `/__cypress/reset` ซึ่ง **ล้างข้อมูลทั้งฐานข้อมูลก่อนทุกเคส** สคริปต์จึงใช้เฉพาะ container
> ของตัวเอง (`clinic-patients-bcc-pg`, `clinic-e2e-backend`) และไม่แตะ postgres ที่ใช้งาน, Neon หรือ `docker compose` ปกติ
> อย่าตั้ง `VITE_API_PROXY_TARGET` ไปที่ backend จริงเด็ดขาด

## สิ่งที่ต้องมี

- Windows + PowerShell, Docker Desktop (รออยู่ที่ "Engine running"), Node.js, Google Chrome
- พอร์ต 15434, 18080 และ 5173 ว่าง (หรือเป็นของระบบทดสอบนี้เอง)
- ที่ว่างในดิสก์ ไม่ต่ำกว่า ~10 GB (Docker image และ Maven cache)

## คำสั่งที่ใช้

รันที่โฟลเดอร์ root ของโปรเจกต์ (`Sabyeguyz-Clinic_ERP`)

| ต้องการ | คำสั่ง |
|---|---|
| รันทั้ง 3 ชุด | `.\scripts\run-frontend-e2e.ps1` |
| รันชุดเดียว | `.\scripts\run-frontend-e2e.ps1 -Suite therapist` (`patients` \| `manager` \| `therapist`) |
| เปิดหน้าต่าง Chrome ให้ดูขั้นตอน | `.\scripts\run-frontend-e2e.ps1 -Suite manager -Headed` |
| รันเฉพาะสเปค | `.\scripts\run-frontend-e2e.ps1 -Suite therapist -Spec cypress/e2e-therapist/services.cy.js` |
| ไม่ปิดระบบหลังรัน (รันซ้ำเร็วขึ้น) | เพิ่ม `-KeepRunning` |
| ใช้ Maven/JDK ในเครื่องแทน Docker | เพิ่ม `-HostBackend` |

ถ้า PowerShell ไม่ยอมรันสคริปต์ ให้ใช้ `powershell -ExecutionPolicy Bypass -File .\scripts\run-frontend-e2e.ps1 ...`

## สคริปต์ทำอะไรตามลำดับ

1. ตรวจเครื่องมือ (docker, node, npx) และว่า Docker engine ทำงานอยู่ ติดตั้ง `npm ci` ถ้ายังไม่มี `node_modules`
2. สร้างเครือข่าย `clinic-e2e-net` และฐานข้อมูล PostgreSQL 16 ใช้แล้วทิ้ง (พอร์ต 15434, เปิดเฉพาะ localhost)
3. สตาร์ต backend เทสต์ (profile `cypress-v2`) ใน Docker โดยก๊อปโค้ดไปไว้ในโฟลเดอร์ชั่วคราว จึงไม่แตะ `backend/target`
   รอบแรกต้องดาวน์โหลด Maven dependency (หลายนาที) รอบต่อไปใช้ cache จาก volume `clinic-e2e-m2`
4. สตาร์ต Vite ที่ 5173 โดย proxy `/api` ไปที่ backend เทสต์ แล้วทดสอบว่าล็อกอินผ่าน Vite ถึง backend เทสต์จริง
5. รัน Cypress ทีละชุดด้วย Chrome (ตั้ง `PATIENTS_BCC_RESET`, `MANAGER_E2E_RESET`, `THERAPIST_E2E_RESET` ให้เอง)
6. แสดงตารางสรุป (จำนวนเทสต์ ผ่าน พัง) และ exit code เป็น 1 ถ้ามีชุดใดพัง
7. หยุด Vite และลบ container/เครือข่ายที่สคริปต์เปิดเอง (ยกเว้นใช้ `-KeepRunning`)

## ผลลัพธ์อยู่ที่ไหน

- สรุปในเทอร์มินัลตอนท้าย
- ไฟล์ JUnit: `frontend\test-results\<suite>\*.xml`
- ภาพหน้าจอตอนเทสต์พัง: `frontend\cypress\screenshots\` (เก็บเฉพาะชุดที่รันล่าสุด)
- โฟลเดอร์เหล่านี้ถูก git-ignore อยู่แล้ว

## ปิดระบบด้วยมือ (กรณีใช้ `-KeepRunning` หรือสคริปต์ถูกหยุดกลางคัน)

```powershell
docker rm -f clinic-e2e-backend clinic-patients-bcc-pg
docker network rm clinic-e2e-net
# ปิด Vite: หา PID ที่ฟังพอร์ต 5173 แล้ว taskkill
Get-NetTCPConnection -LocalPort 5173 -State Listen | ForEach-Object { taskkill /PID $_.OwningProcess /T /F }
# ลบ Maven cache ถ้าต้องการคืนที่ว่าง
docker volume rm clinic-e2e-m2
```

## ปัญหาที่พบบ่อย

| อาการ | สาเหตุ / วิธีแก้ |
|---|---|
| `Docker engine is not running` | เปิด Docker Desktop แล้วรอ "Engine running" |
| `Port ... is used by something else` | มีโปรแกรมอื่นใช้พอร์ต 15434/18080/5173 ปิดก่อนแล้วรันใหม่ |
| backend ไม่ขึ้นภายใน 15 นาที | ดู `docker logs clinic-e2e-backend` (ดิสก์เต็มหรือดาวน์โหลด Maven ไม่ได้ เช่น DNS ใน Docker) |
| `Unable to establish loopback connection` ตอนใช้ `-HostBackend` | JVM บนเครื่องนี้สตาร์ตไม่ได้ ใช้โหมด Docker (ค่าเริ่มต้น) |
| เทสต์ที่ค้นหา/กดปุ่มพังพร้อมกันหลายเคส | หน้าจอหรือกฎ backend ล่าสุดเปลี่ยนไป ดูภาพใน `cypress\screenshots` และข้อความ error ใน XML |

## สถานะเทสต์ล่าสุดบน `main` (14 พ.ย. 2026)

| ชุด | ผล |
|---|---|
| Patients | ผ่าน 26/26 |
| Manager | ผ่าน 26/26 (4 สเปค: checkout, navigation, patients, appointments) |
| Therapist | ผ่าน 91/91 (compensation ใช้ fixture `shift-activity` ย้ายข้อมูลไปเดือนก่อนหน้าเพื่อปิดรอบได้) |
